package nz.myinspection.app.platform

import android.content.Context
import android.os.UserManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.KeyStoreException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Android implementation of [SecretKeyPort] and [DeviceUnlockPort]. Keys live in AndroidKeyStore: AES-256, GCM without
 * padding, encrypt and decrypt only, randomized encryption, no user authentication and not unlocked-device-required,
 * so the finalize and weekly backups can open the envelope after first unlock (ADR-0006 §3). On seal a usable key is
 * kept; while the device is unlocked, an entry that cannot be loaded, is not a secret key or cannot encrypt is replaced
 * by a new key. Ordinary failures become one fixed message without a cause; an Error propagates. The debug
 * LocalSecretBoxProbeActivity checks the key and seal behavior on devices.
 */
class AndroidSecretKeys internal constructor(
    private val keyStore: () -> KeyStore,
    private val userUnlocked: () -> Boolean,
) : SecretKeyPort, DeviceUnlockPort {
    constructor(context: Context) : this(
        { KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) } },
        context.getSystemService(UserManager::class.java)::isUserUnlocked,
    )

    override fun existingKey(alias: String): SecretKey? = keystoreCall { loadKey(keyStore(), alias) }

    @Synchronized
    override fun keyForSeal(alias: String): SecretKey = keystoreCall {
        val store = keyStore()
        chooseSealKey(
            load = { loadKey(store, alias) },
            canEncrypt = ::canEncrypt,
            unlocked = isUnlocked(),
            create = { generate(alias) },
            replace = { generate(alias) },
        )
    }

    /** The app is not direct-boot aware, so no app process runs before first unlock and this reads true in practice. */
    override fun isUnlocked(): Boolean = keystoreCall { userUnlocked() }

    /** Generating under an existing alias replaces that entry. */
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

/** The entry under [alias], or null when there is none; an entry that is not a secret key throws, so it is unusable. */
private fun loadKey(store: KeyStore, alias: String): SecretKey? =
    store.getKey(alias, null)?.let { it as? SecretKey ?: throw KeyStoreException() }

/**
 * The seal key: create one when there is none, keep one that can encrypt, and replace one that cannot be loaded or
 * cannot encrypt only while the device is unlocked. A locked device keeps the key (or rethrows the load failure), so
 * the box's own lock check reports the retryable state.
 */
internal fun <K : Any> chooseSealKey(
    load: () -> K?,
    canEncrypt: (K) -> Boolean,
    unlocked: Boolean,
    create: () -> K,
    replace: () -> K,
): K {
    val existing = try {
        load()
    } catch (failure: Exception) {
        if (unlocked) return replace() else throw failure
    }
    return when {
        existing == null -> create()
        canEncrypt(existing) -> existing
        unlocked -> replace()
        else -> existing
    }
}

/** Keystore failures carry aliases and provider detail; callers get one fixed message and no cause. */
internal inline fun <T> keystoreCall(block: () -> T): T = try {
    block()
} catch (_: Exception) {
    throw IllegalStateException(KEYSTORE_UNAVAILABLE)
}

internal const val KEYSTORE_UNAVAILABLE = "android keystore unavailable"
private const val ANDROID_KEY_STORE = "AndroidKeyStore"
