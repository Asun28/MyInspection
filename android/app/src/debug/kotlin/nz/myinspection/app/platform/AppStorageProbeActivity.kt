package nz.myinspection.app.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager.NameNotFoundException
import android.os.Bundle
import android.os.Environment
import java.io.File
import java.security.MessageDigest

/**
 * Debug-only device probe for [AndroidAppStorageEnvironment]; recipe in docs/storage-android-probe.md. Expected values
 * are platform getters called by the probe itself, never the adapter's results. Checks named `synthetic` or
 * `controlled` run the real adapter over a wrapper or a File subclass and say in a comment what it substitutes. The
 * receipt holds the run id, the installed APK digest, raw volume-state constants and check names only.
 */
class AppStorageProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val runId = intent.getStringExtra("runId")?.takeIf { RUN_ID.matches(it) }
        if (runId != null) {
            val receipt = StringBuilder("run=$runId\napk=${sha256(File(applicationInfo.sourceDir))}\n")
            try {
                ProbeChecks(applicationContext, runId, receipt).run()
                receipt.append("DONE $runId\n")
            } catch (failure: AssertionError) {
                val name = failure.message?.takeIf { CHECK_NAME.matches(it) } ?: "unnamed"
                receipt.append("FAIL $name ${failure.javaClass.name}\n")
            } catch (failure: Throwable) {
                receipt.append("ERROR ${failure.javaClass.name}\n")
            }
            val directory = File(filesDir, "app-storage-probe").apply { mkdirs() }
            val partial = File(directory, "$runId.partial").apply { writeText(receipt.toString()) }
            check(partial.renameTo(File(directory, "$runId.txt")))
        }
        finish()
    }
}

private class ProbeChecks(private val ce: Context, runId: String, private val receipt: StringBuilder) {
    private val dp = ce.createDeviceProtectedStorageContext()
    private val external = checkNotNull(ce.getExternalFilesDir(null))
    private val fixture = File(external, "app-storage-probe-$runId")
    private val adapter = AndroidAppStorageEnvironment(ce)
    private val dpAdapter = AndroidAppStorageEnvironment(dp)

    @Suppress("DEPRECATION")
    private val sharedRoot = Environment.getExternalStorageDirectory()

    @Suppress("DEPRECATION")
    private val publicPictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)

    fun run() {
        try {
            roots()
            states()
            spaceAndConversionErrors()
        } finally {
            fixture.deleteRecursively()
        }
        expect("cleanup.fixtureRemoved", !fixture.exists())
    }

    private fun roots() {
        expect("A1.pre.contextMarkers", !ce.isDeviceProtectedStorage && dp.isDeviceProtectedStorage)
        expect("A1.pre.noBackupDistinctFromFiles", distinct(ce.noBackupFilesDir, ce.filesDir))
        expect("A1.pre.dpDistinctFromCe", distinct(dp.dataDir, ce.dataDir) && distinct(dp.noBackupFilesDir, ce.noBackupFilesDir))
        expect("A1.pre.externalDistinctFromPublic", distinct(external, sharedRoot) && distinct(external, publicPictures))
        expect("A1.marker.ce", !adapter.isDeviceProtectedStorage)
        expect("A1.marker.dp", dpAdapter.isDeviceProtectedStorage)
        expect("A1.root.noBackup", adapter.noBackupFilesDir == ce.noBackupFilesDir)
        expect("A1.root.appData", adapter.appDataDir == ce.dataDir)
        expect("A1.root.deviceProtectedData", adapter.deviceProtectedDataDir == dp.dataDir)
        expect("A1.root.external", adapter.appSpecificExternalMediaDir == external)
        val converted = dpAdapter.credentialEncryptedContext()
        expect("A1.convert.marker", !converted.isDeviceProtectedStorage)
        expect("A1.convert.roots", converted.noBackupFilesDir == ce.noBackupFilesDir && converted.appDataDir == ce.dataDir)
        expect("A1.policy.ceRoutes", routesUnderCeNoBackup(AppStoragePolicy(adapter)))
        expect("A1.policy.dpConvertedRoutes", routesUnderCeNoBackup(AppStoragePolicy(dpAdapter)))
        // Synthetic: the real DP context behind a wrapper whose only substitution is a false CE marker.
        val falseMarker = AndroidAppStorageEnvironment(object : ContextWrapper(dp) {
            override fun isDeviceProtectedStorage(): Boolean = false
        })
        expect(
            "A1.synthetic.falseMarkerKeepsDpRoot",
            !falseMarker.isDeviceProtectedStorage && falseMarker.noBackupFilesDir == dp.noBackupFilesDir,
        )
        expect("A1.synthetic.falseMarkerRejected", refusedWithoutCause(POLICY_REFUSAL) { AppStoragePolicy(falseMarker) })
    }

    private fun states() {
        val raw = linkedMapOf(
            "external" to Environment.getExternalStorageState(external),
            "internal" to Environment.getExternalStorageState(ce.filesDir),
            "global" to Environment.getExternalStorageState(),
            "root" to Environment.getExternalStorageState(sharedRoot),
            "rootParent" to Environment.getExternalStorageState(checkNotNull(sharedRoot.parentFile)),
        )
        receipt.append(raw.entries.joinToString(" ", "STATE ", "\n") { "${it.key}=${it.value}" })
        val mounted = listOf("external", "global", "root").all { raw[it] == Environment.MEDIA_MOUNTED }
        val notMounted = listOf("internal", "rootParent").none {
            raw[it] == Environment.MEDIA_MOUNTED || raw[it] == Environment.MEDIA_MOUNTED_READ_ONLY
        }
        expect("A2.pre.rawStates", mounted && notMounted)
        expect("A2.state.external", adapter.appSpecificExternalMediaState(external) == ExternalMediaVolumeState.MOUNTED)
        expect("A2.state.internal", adapter.appSpecificExternalMediaState(ce.filesDir) == ExternalMediaVolumeState.UNMOUNTED)
        expect("A2.state.volumeRoot", adapter.appSpecificExternalMediaState(sharedRoot) == ExternalMediaVolumeState.MOUNTED)
        expect("A2.writable.ownedDirectory", !fixture.exists() && fixture.mkdir() && adapter.isAppSpecificExternalMediaWritable(fixture))
        val plain = File(fixture, "plain").apply { writeText("probe") }
        expect("A2.writable.plainFile", plain.isFile && plain.canWrite() && !adapter.isAppSpecificExternalMediaWritable(plain))
        val absent = File(fixture, "absent")
        expect("A2.writable.absentPath", !absent.exists() && !adapter.isAppSpecificExternalMediaWritable(absent))
        val available = AppStoragePolicy(adapter).mediaLocation(1)
        expect("A2.policy.available", available is MediaStorageLocation.Available && available.root.directory == external)
        // Controlled: a wrapper whose only substitution is the absent path as the external directory. Each port value
        // is read first without error: the state is mounted, 0 bytes are requested, and only writability is false.
        val absentExternal = AndroidAppStorageEnvironment(object : ContextWrapper(ce) {
            override fun getExternalFilesDir(type: String?): File = absent
        })
        expect(
            "A2.controlled.absentExternalUnavailable",
            absentExternal.appSpecificExternalMediaDir == absent &&
                absentExternal.appSpecificExternalMediaState(absent) == ExternalMediaVolumeState.MOUNTED &&
                !absentExternal.isAppSpecificExternalMediaWritable(absent) && absentExternal.usableBytes(absent) >= 0 &&
                AppStoragePolicy(absentExternal).mediaLocation(0) == MediaStorageLocation.Unavailable && !absent.exists(),
        )
    }

    private fun spaceAndConversionErrors() {
        // Controlled: a File whose usable space is a sentinel; its free and total space stay real.
        val sentinel = object : File(fixture.path) {
            override fun getUsableSpace(): Long = SENTINEL_BYTES
        }
        expect(
            "A3.controlled.usableSpaceSentinel",
            fixture.freeSpace != SENTINEL_BYTES && fixture.totalSpace != SENTINEL_BYTES &&
                adapter.usableBytes(sentinel) == SENTINEL_BYTES,
        )
        // Platform: accept the first of five attempts in which the space did not move between the direct reads.
        val stableRead = (1..5).any {
            val before = external.usableSpace
            val actual = adapter.usableBytes(external)
            actual >= 0 && actual == before && actual == external.usableSpace
        }
        expect("A3.usableSpace", stableRead)
        // Controlled: package-context creation throws a chosen throwable carrying a sensitive marker.
        expect("A3.controlled.nameNotFoundRefused", conversionRefused(NameNotFoundException(SENSITIVE_MARKER)))
        expect("A3.controlled.securityRefused", conversionRefused(SecurityException(SENSITIVE_MARKER)))
        val fatal = object : Error(SENSITIVE_MARKER) {}
        expect(
            "A3.controlled.errorPropagates",
            runCatching { throwing(fatal).credentialEncryptedContext() }.exceptionOrNull() === fatal,
        )
    }

    private fun expect(name: String, condition: Boolean) {
        if (!condition) throw AssertionError(name)
        receipt.append("PASS $name\n")
    }

    private fun distinct(first: File, second: File): Boolean = first.canonicalPath != second.canonicalPath

    private fun routesUnderCeNoBackup(policy: AppStoragePolicy): Boolean = SecureStorageNamespace.entries.all {
        policy.location(it).directory.parentFile?.canonicalFile == ce.noBackupFilesDir.canonicalFile
    }

    private fun conversionRefused(failure: Throwable): Boolean =
        refusedWithoutCause(CONVERSION_REFUSAL) { throwing(failure).credentialEncryptedContext() }

    private fun throwing(failure: Throwable) = AndroidAppStorageEnvironment(object : ContextWrapper(ce) {
        override fun createPackageContext(packageName: String, flags: Int): Context = throw failure
    })

    private fun refusedWithoutCause(message: String, block: () -> Unit): Boolean {
        val thrown = runCatching(block).exceptionOrNull()
        return thrown is IllegalStateException && thrown.message == message && thrown.cause == null
    }
}

private fun sha256(file: File): String {
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

private val RUN_ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
private val CHECK_NAME = Regex("[A-Za-z0-9]+(\\.[A-Za-z0-9]+)+")
private const val POLICY_REFUSAL = "credential-encrypted storage unavailable"
private const val CONVERSION_REFUSAL = "credential-encrypted context unavailable"
private const val SENSITIVE_MARKER = "42 Example St/Jane Tenant"
private const val SENTINEL_BYTES = 7_340_033L
