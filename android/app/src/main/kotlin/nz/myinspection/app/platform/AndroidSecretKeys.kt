package nz.myinspection.app.platform

import android.content.Context
import android.os.UserManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Android implementation of [SecretKeyPort] and [DeviceUnlockPort]. Keys live in AndroidKeyStore: AES-256, GCM without
 * padding, encrypt and decrypt only, randomized encryption, no user authentication and not unlocked-device-required,
 * so the finalize and weekly backups can open the envelope after first unlock (ADR-0006 §3). An existing key is kept;
 * only a key that cannot initialise for encryption while the device is unlocked is replaced on seal. Ordinary failures
 * become one fixed message without a cause; an Error propagates. SecretBoxProbeActivity (debug) checks this on devices.
 */
class AndroidSecretKeys(context: Context) : SecretKeyPort, DeviceUnlockPort {
    private val userManager = context.getSystemService(UserManager::class.java)

    override fun existingKey(alias: String): SecretKey? = keystoreCall { keyStore().getKey(alias, null) as? SecretKey }

    override fun keyForSeal(alias: String): SecretKey = keystoreCall {
        val store = keyStore()
        chooseSealKey(
            existing = store.getKey(alias, null) as? SecretKey,
            canEncrypt = ::canEncrypt,
            unlocked = isUnlocked(),
            create = { generate(alias) },
            replace = {
                store.deleteEntry(alias)
                generate(alias)
            },
        )
    }

    override fun isUnlocked(): Boolean = userManager.isUserUnlocked

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private fun generate(alias: String): SecretKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
        init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        generateKey()
    }

    /** Runs a complete empty encryption, so the Keystore operation is finished rather than left open. */
    private fun canEncrypt(key: SecretKey): Boolean = try {
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, key)
            doFinal()
        }
        true
    } catch (_: Exception) {
        false
    }
}

/**
 * The seal key: create one when there is none, keep one that can encrypt, and replace one that cannot only while the
 * device is unlocked. A locked device keeps the key, so the box's own lock check reports the retryable state.
 */
internal fun <K : Any> chooseSealKey(
    existing: K?,
    canEncrypt: (K) -> Boolean,
    unlocked: Boolean,
    create: () -> K,
    replace: () -> K,
): K = when {
    existing == null -> create()
    canEncrypt(existing) -> existing
    unlocked -> replace()
    else -> existing
}

/** Keystore failures carry aliases and provider detail; callers get one fixed message and no cause. */
internal inline fun <T> keystoreCall(block: () -> T): T = try {
    block()
} catch (_: Exception) {
    throw IllegalStateException(KEYSTORE_UNAVAILABLE)
}

internal const val KEYSTORE_UNAVAILABLE = "android keystore unavailable"
private const val ANDROID_KEY_STORE = "AndroidKeyStore"
