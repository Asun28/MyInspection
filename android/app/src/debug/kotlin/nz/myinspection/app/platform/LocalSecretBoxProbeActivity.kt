package nz.myinspection.app.platform

import android.app.Activity
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.io.File
import java.security.InvalidAlgorithmParameterException
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/**
 * Debug-only device probe for [AndroidSecretKeys] driving the production [LocalSecretBox]; recipe in
 * docs/local-secret-box-probe.md. It uses its own key version, so its alias is never a production alias, and keeps
 * envelopes in memory, so no production envelope file is touched. Expected values come from AndroidKeyStore and
 * UserManager read by the probe itself. The receipt holds the run id, the installed APK digest, one KEYINFO line of
 * recorded (not asserted) hardware facts, check names and, on failure, an exception class name; never key material.
 */
class LocalSecretBoxProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val runId = intent.getStringExtra("runId")?.takeIf { PROBE_RUN_ID.matches(it) }
        if (runId != null) {
            val receipt = StringBuilder("run=$runId\napk=${probeSha256(File(applicationInfo.sourceDir))}\n")
            try {
                SecretBoxChecks(applicationContext, receipt).run()
                receipt.append("DONE $runId\n")
            } catch (failure: AssertionError) {
                val name = failure.message?.takeIf { PROBE_CHECK_NAME.matches(it) } ?: "unnamed"
                receipt.append("FAIL $name ${failure.javaClass.name}\n")
            } catch (failure: Throwable) {
                receipt.append("ERROR ${failure.javaClass.name}\n")
            }
            val directory = File(filesDir, "secret-box-probe").apply { mkdirs() }
            val partial = File(directory, "$runId.partial").apply { writeText(receipt.toString()) }
            check(partial.renameTo(File(directory, "$runId.txt")))
        }
        finish()
    }
}

private class SecretBoxChecks(context: Context, private val receipt: StringBuilder) {
    private val keys = AndroidSecretKeys(context)
    private val envelopes = MemoryEnvelopes()
    private val box = LocalSecretBox(keys, keys, envelopes, PROBE_VERSION, setOf(PROBE_VERSION))
    private val alias = secretKeyAlias(PURPOSE, PROBE_VERSION)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val userManager = context.getSystemService(UserManager::class.java)

    fun run() {
        try {
            keyStore.deleteEntry(alias)
            expect("pre.aliasAbsent", !keyStore.containsAlias(alias))
            expect("unlock.adapterMatchesUserManager", keys.isUnlocked() && userManager.isUserUnlocked)
            expect("A5.roundTrip", seal() && openedText() == SAMPLE)
            keyFacts()
            sealsAndTamper()
            unusableKeyRebuilt()
        } finally {
            keyStore.deleteEntry(alias)
        }
        expect("cleanup.aliasRemoved", !keyStore.containsAlias(alias))
    }

    private fun keyFacts() {
        val key = keys.existingKey(alias)
        expect("A4.key.inAndroidKeyStore", key != null && keyStore.containsAlias(alias))
        expect("A4.key.notExportable", key!!.encoded == null)
        val info = keyInfo(key)
        expect("A4.key.size256", info.keySize == 256)
        expect("A4.key.purposesEncryptDecrypt", info.purposes == KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        expect("A4.key.gcmOnly", info.blockModes.toList() == listOf(KeyProperties.BLOCK_MODE_GCM))
        expect("A4.key.noPadding", info.encryptionPaddings.toList() == listOf(KeyProperties.ENCRYPTION_PADDING_NONE))
        expect("A4.key.noUserAuthentication", !info.isUserAuthenticationRequired)
        @Suppress("DEPRECATION")
        val inside = info.isInsideSecureHardware
        val level = if (Build.VERSION.SDK_INT >= 31) info.securityLevel else -1
        receipt.append("KEYINFO securityLevel=$level insideSecureHardware=$inside\n")
        expect("A4.keystore.callerIvRefused", callerIvRefused(key))
    }

    private fun sealsAndTamper() {
        val first = envelopes.current()
        expect("A4.sameKeyAcrossSeals", seal() && run { envelopes.put(first); openedText() == SAMPLE })
        seal()
        val one = envelopes.current()
        seal()
        val two = envelopes.current()
        expect(
            "A5.freshNonceAndCiphertext",
            !one.copyOfRange(1, 13).contentEquals(two.copyOfRange(1, 13)) &&
                !one.copyOfRange(13, one.size).contentEquals(two.copyOfRange(13, two.size)),
        )
        mapOf("nonce" to 6, "ciphertext" to 13, "tag" to two.size - 1).forEach { (part, index) ->
            val tampered = two.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
            envelopes.put(tampered)
            expect("A5.tamper.$part", refused(SecretFailureReason.AUTHENTICATION_FAILED) && envelopes.current().contentEquals(tampered))
        }
        envelopes.put(ByteArray(5))
        expect("A5.corruptEnvelope", refused(SecretFailureReason.ENVELOPE_CORRUPT))
        envelopes.put(two)
        keyStore.deleteEntry(alias)
        expect("A5.keyMissing", refused(SecretFailureReason.KEY_MISSING) && envelopes.current().contentEquals(two))
    }

    private fun unusableKeyRebuilt() {
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
        val planted = keyStore.getKey(alias, null) as SecretKey
        expect("A7.plantedKeyCannotEncrypt", keyInfo(planted).purposes == KeyProperties.PURPOSE_DECRYPT)
        val rebuilt = seal() && openedText() == SAMPLE
        val current = keyStore.getKey(alias, null) as SecretKey
        expect(
            "A7.unusableKeyRebuiltOnSeal",
            rebuilt && keyInfo(current).purposes == KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
    }

    private fun seal(): Boolean = box.seal(PURPOSE, SAMPLE.toCharArray()) == SecretSealResult.STORED

    private fun openedText(): String? = (box.open(PURPOSE) as? SecretOpenResult.Opened)?.value?.use { String(it.chars) }

    private fun refused(reason: SecretFailureReason): Boolean = box.open(PURPOSE) == SecretOpenResult.NeedsPassphrase(reason)

    private fun keyInfo(key: SecretKey): KeyInfo =
        SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore").getKeySpec(key, KeyInfo::class.java) as KeyInfo

    private fun callerIvRefused(key: SecretKey): Boolean = try {
        Cipher.getInstance("AES/GCM/NoPadding").init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, ByteArray(12)))
        false
    } catch (_: InvalidAlgorithmParameterException) {
        true
    }

    private fun expect(name: String, condition: Boolean) {
        if (!condition) throw AssertionError(name)
        receipt.append("PASS $name\n")
    }
}

private class MemoryEnvelopes : SecretEnvelopeFiles {
    private var stored: ByteArray? = null

    fun current(): ByteArray = checkNotNull(stored).copyOf()

    fun put(envelope: ByteArray) {
        stored = envelope.copyOf()
    }

    override fun read(purpose: SecretPurpose): ByteArray? = stored?.copyOf()

    override fun replace(purpose: SecretPurpose, envelope: ByteArray) = put(envelope)
}

/**
 * Debug-only lock-screen check, run by the host script on the emulator only (recipe in docs/local-secret-box-probe.md).
 * Receivers still run behind the keyguard, so `open` can show that the envelope opens while the screen is locked: the
 * property the weekly background backup depends on. Phases: `prepare` seals under key version 202 into a probe-owned
 * file, `open` must run locked, `cleanup` deletes the alias and the file. Each phase writes its own receipt.
 */
class LocalSecretBoxLockedProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val runId = intent.getStringExtra("runId")?.takeIf { PROBE_RUN_ID.matches(it) } ?: return
        val phase = intent.getStringExtra("phase")?.takeIf { it in LOCKED_PHASES } ?: return
        val directory = File(context.filesDir, "secret-box-probe").apply { mkdirs() }
        val receipt = StringBuilder("run=$runId phase=$phase\n")
        try {
            LockedChecks(context, File(directory, "locked-$runId.envelope"), receipt).run(phase)
            receipt.append("DONE $runId\n")
        } catch (failure: AssertionError) {
            val name = failure.message?.takeIf { PROBE_CHECK_NAME.matches(it) } ?: "unnamed"
            receipt.append("FAIL $name ${failure.javaClass.name}\n")
        } catch (failure: Throwable) {
            receipt.append("ERROR ${failure.javaClass.name}\n")
        }
        val partial = File(directory, "$runId-$phase.partial").apply { writeText(receipt.toString()) }
        check(partial.renameTo(File(directory, "$runId-$phase.txt")))
    }
}

private class LockedChecks(context: Context, private val envelope: File, private val receipt: StringBuilder) {
    private val keys = AndroidSecretKeys(context)
    private val box = LocalSecretBox(keys, keys, FileEnvelopes(envelope), LOCKED_VERSION, setOf(LOCKED_VERSION))
    private val alias = secretKeyAlias(PURPOSE, LOCKED_VERSION)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val keyguard = context.getSystemService(KeyguardManager::class.java)

    fun run(phase: String) = when (phase) {
        "prepare" -> {
            expect("locked.pre.screenUnlocked", !keyguard.isDeviceLocked)
            keyStore.deleteEntry(alias)
            expect("locked.prepare.sealed", box.seal(PURPOSE, SAMPLE.toCharArray()) == SecretSealResult.STORED)
        }
        "open" -> {
            expect("locked.pre.screenLocked", keyguard.isDeviceLocked && keys.isUnlocked())
            val opened = (box.open(PURPOSE) as? SecretOpenResult.Opened)?.value?.use { String(it.chars) }
            expect("locked.open.whileScreenLocked", opened == SAMPLE)
        }
        else -> {
            keyStore.deleteEntry(alias)
            envelope.delete()
            expect("locked.cleanup.removed", !keyStore.containsAlias(alias) && !envelope.exists())
        }
    }

    private fun expect(name: String, condition: Boolean) {
        if (!condition) throw AssertionError(name)
        receipt.append("PASS $name\n")
    }
}

private class FileEnvelopes(private val file: File) : SecretEnvelopeFiles {
    override fun read(purpose: SecretPurpose): ByteArray? = file.takeIf { it.isFile }?.readBytes()

    override fun replace(purpose: SecretPurpose, envelope: ByteArray) = file.writeBytes(envelope)
}

private fun probeSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(1 shl 16)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private val PURPOSE = SecretPurpose.BACKUP_PASSPHRASE
private const val PROBE_VERSION = 201
private const val LOCKED_VERSION = 202
private val LOCKED_PHASES = setOf("prepare", "open", "cleanup")
private const val SAMPLE = "probe passphrase 42"
private val PROBE_RUN_ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
private val PROBE_CHECK_NAME = Regex("[A-Za-z0-9]+(\\.[A-Za-z0-9]+)+")
