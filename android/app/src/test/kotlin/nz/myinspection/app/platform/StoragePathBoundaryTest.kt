package nz.myinspection.app.platform

import java.io.File
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StoragePathBoundaryTest {
    @Test
    fun `roots obey normalized strict CE and DP boundaries`() = withStoragePaths { f ->
        val cases = listOf(
            Triple(f.ce.resolve("nested/../metadata"), f.ce, true),
            Triple(f.ce.resolve("./metadata"), f.ce, true),
            Triple(f.ce, f.ce, false),
            Triple(f.root.resolve("ce-shadow/metadata"), f.ce, false),
            Triple(f.external.resolve("metadata"), f.ce, false),
            Triple(f.dp.resolve("metadata"), f.ce, false),
            Triple(f.ce.resolve("../dp/metadata"), f.ce, false),
            Triple(f.ce.resolve("metadata"), f.ce.resolve("nested/.."), true),
        )
        cases.forEachIndexed { index, (candidate, app, accepted) ->
            val result = StoragePathBoundary.create(candidate.toFile(), app.toFile(), f.dp.toFile())
            assertEquals(accepted, result != null, "root vector $index")
            if (accepted) assertEquals(f.ce.resolve("metadata").toFile(), result!!.directory)
        }
        assertNull(StoragePathBoundary.create(
            f.dp.resolve("metadata").toFile(), f.dp.toFile(), f.dp.resolve("nested/..").toFile(),
        ), "normalized DP must independently exclude an otherwise contained candidate")
    }

    @Test
    fun `each blank root refuses before any root is parsed`() {
        val cwd = File("").toPath().toAbsolutePath()
        val sibling = cwd.resolveSibling(cwd.fileName.toString() + "-credential")
        val cases = listOf(
            listOf(File(""), cwd.parent.toFile(), cwd.resolve("dp").toFile()),
            listOf(cwd.resolve("metadata").toFile(), File(""), cwd.resolve("dp").toFile()),
            listOf(sibling.resolve("metadata").toFile(), sibling.toFile(), File("")),
        )
        cases.forEachIndexed { index, inputs ->
            val paths = inputs.map { it.toPath().toAbsolutePath().normalize() }
            assertTrue(paths[0] != paths[1] && paths[0].startsWith(paths[1]), "blank $index geometry")
            assertFalse(paths[0].startsWith(paths[2]), "blank $index DP geometry")
            listOf("", " ", "\t").forEach { blank ->
                var calls = 0
                val roots = inputs.map { original ->
                    object : File(if (original.path.isEmpty()) blank else original.path) {
                        override fun toPath(): Path {
                            calls++
                            throw AssertionError("parsed root before blank rejection")
                        }
                    }
                }
                val result = runCatching { StoragePathBoundary.create(roots[0], roots[1], roots[2]) }
                assertTrue(result.isSuccess, "blank root $index")
                assertNull(result.getOrNull(), "blank root $index")
                assertEquals(0, calls, "no root may be parsed")
            }
        }
    }

    @Test
    fun `actual DP root is refused even when it is also app root`() = withStoragePaths { f ->
        val sensitive = f.dp.resolve("42 Example St/Jane Tenant/secret")
        val result = runCatching { StoragePathBoundary.create(sensitive.toFile(), f.dp.toFile(), f.dp.toFile()) }
        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun `real aliases resolve candidate app and DP roots`() = withStoragePaths { f ->
        val target = f.dir("ce/no-backup")
        val candidateAlias = f.alias("candidate-alias", target)
        assertEquals(target.toFile(), f.boundary(candidateAlias).directory, "candidate real path")
        val appAlias = f.alias("app-alias", f.ce)
        assertNotNull(StoragePathBoundary.create(target.toFile(), appAlias.toFile(), f.dp.toFile()), "app real path")
        val forbidden = f.dir("ce/no-backup/forbidden")
        val dpAlias = f.alias("dp-alias", forbidden)
        assertNull(StoragePathBoundary.create(forbidden.resolve("child").toFile(), f.ce.toFile(), dpAlias.toFile()))
    }

    @Test
    fun `root toPath fatal errors retain identity`() = withStoragePaths { f ->
        val roots = listOf(f.ce.resolve("no-backup").toFile(), f.ce.toFile(), f.dp.toFile())
        listOf(OutOfMemoryError("candidate"), ThreadDeath(), OutOfMemoryError("DP")).forEachIndexed { i, fatal ->
            val inputs = roots.toMutableList().apply { this[i] = failingPath(this[i], fatal) }
            assertSame(fatal, runCatching { StoragePathBoundary.create(inputs[0], inputs[1], inputs[2]) }.exceptionOrNull())
        }
    }

    @Test
    fun `ordinary toPath failures return null`() = withStoragePaths { f ->
        val roots = listOf(f.ce.resolve("no-backup").toFile(), f.ce.toFile(), f.dp.toFile())
        roots.indices.forEach { i ->
            listOf(IOException("42 Example St Jane Tenant"), SecurityException("Authorization Bearer secret")).forEach { failure ->
                val inputs = roots.toMutableList().apply { this[i] = failingPath(this[i], failure) }
                val result = runCatching { StoragePathBoundary.create(inputs[0], inputs[1], inputs[2]) }
                assertTrue(result.isSuccess, "ordinary failure at root $i")
                assertNull(result.getOrNull())
            }
        }
    }

    @Test
    fun `saved candidate is the exact path checked once`() = withStoragePaths { f ->
        val target = f.dir("ce/no-backup")
        var calls = 0
        val changing = object : File(target.toString()) {
            override fun toPath(): Path = if (++calls == 1) target else f.external
        }
        val boundary = assertNotNull(StoragePathBoundary.create(changing, f.ce.toFile(), f.dp.toFile()))
        assertEquals(target.toFile(), boundary.directory)
        assertEquals(1, calls, "must save the path already compared")
    }

    @Test
    fun `source alias retarget does not move the saved root`() = withStoragePaths { f ->
        val target = f.dir("ce/no-backup")
        val alias = f.alias("source", target)
        val boundary = f.boundary(alias)
        f.retarget(alias, f.external)
        assertEquals(target.toFile(), boundary.directory)
        assertEquals(target.resolve("child").toFile(), boundary.resolveChild(File(boundary.directory, "child")))
    }

    @Test
    fun `replacement of saved directory refuses children`() = withStoragePaths { f ->
        val target = f.dir("ce/no-backup")
        val boundary = f.boundary(target)
        f.park(target, "parked")
        f.alias("ce/no-backup", f.external)
        assertNull(boundary.resolveChild(target.resolve("child").toFile()))
    }

    @Test
    fun `missing suffix replaced by alias refuses children`() = withStoragePaths { f ->
        val absent = f.ce.resolve("no-backup")
        val boundary = f.boundary(absent)
        assertFalse(Files.exists(absent, NOFOLLOW_LINKS))
        f.alias("ce/no-backup", f.external)
        assertNull(boundary.resolveChild(absent.resolve("child").toFile()))
    }

    @Test
    fun `child boundaries reject root siblings external and DP`() = withStoragePaths { f ->
        val root = f.dir("ce/no-backup")
        val boundary = f.boundary(root)
        listOf(root, f.ce.resolve("no-backup-shadow/child"), f.ce.resolve("sibling"), f.external, f.dp).forEach { path ->
            assertNull(boundary.resolveChild(path.toFile()), "child boundary $path")
        }
        listOf("ce-sibling" to f.ce, "external" to f.external, "dp" to f.dp).forEach { (name, target) ->
            assertNull(boundary.resolveChild(f.alias("ce/no-backup/$name", target).toFile()), "alias $name")
        }
        val nestedDP = f.dir("ce/no-backup/forbidden")
        val nestedBoundary = assertNotNull(StoragePathBoundary.create(root.toFile(), f.ce.toFile(), nestedDP.toFile()))
        listOf(nestedDP, nestedDP.resolve("child")).forEach { assertNull(nestedBoundary.resolveChild(it.toFile())) }
    }

    @Test
    fun `legal child alias returns exactly the checked target`() = withStoragePaths { f ->
        val root = f.dir("ce/no-backup")
        val target = f.dir("ce/no-backup/target")
        val alias = f.alias("ce/no-backup/alias", target)
        val returned = assertNotNull(f.boundary(root).resolveChild(alias.toFile()))
        assertEquals(target.toFile(), returned)
        assertFalse(returned == alias.toFile(), "return must not retain the unchecked raw alias")
    }

    @Test
    fun `absent roots and multilevel children stay uncreated`() = withStoragePaths { f ->
        val root = f.ce.resolve("missing/no-backup")
        val child = root.resolve("one/two/three")
        listOf(root, child).forEach { assertFalse(Files.exists(it, NOFOLLOW_LINKS)) }
        val boundary = f.boundary(root)
        assertEquals(root.toFile(), boundary.directory)
        assertEquals(child.toFile(), boundary.resolveChild(child.toFile()))
        listOf(root, child).forEach { assertFalse(Files.exists(it, NOFOLLOW_LINKS), "resolver must not create") }
        val absentApp = f.root.resolve("absent-app")
        val absentDP = f.root.resolve("absent-dp")
        val absentCandidate = absentApp.resolve("no-backup")
        val uncreated = StoragePathBoundary.create(absentCandidate.toFile(), absentApp.toFile(), absentDP.toFile())
        assertEquals(absentCandidate.toFile(), assertNotNull(uncreated).directory)
        listOf(absentApp, absentDP, absentCandidate).forEach { assertFalse(Files.exists(it, NOFOLLOW_LINKS)) }
    }

    @Test
    fun `missing parent resumes real alias resolution`() = withStoragePaths { f ->
        val root = f.dir("ce/no-backup")
        f.alias("ce/no-backup/alias", f.external)
        val path = root.resolve("missing/../alias/child")
        assertEquals(f.external.resolve("child"), resolveStorageDirectory(path.toFile()))
        assertNull(f.boundary(root).resolveChild(path.toFile()))
    }

    @Test
    fun `parent follows the resolved alias target`() = withStoragePaths { f ->
        val root = f.dir("ce/no-backup")
        val deep = f.dir("external/deep")
        val alias = f.alias("ce/no-backup/alias", deep)
        val path = alias.resolve("../child")
        assertEquals(f.external.resolve("child"), resolveStorageDirectory(path.toFile()))
        assertNull(f.boundary(root).resolveChild(path.toFile()))
    }

    @Test
    fun `regular dangling and cyclic entries fail closed`() = withStoragePaths { f ->
        val root = f.dir("ce/no-backup")
        val boundary = f.boundary(root)
        val regular = f.file("ce/no-backup/plain", "regular")
        listOf(regular, regular.resolve("child")).forEach { assertNull(boundary.resolveChild(it.toFile())) }
        val target = f.dir("target")
        val dangling = f.alias("ce/no-backup/dangling", target)
        f.removeDirectory(target)
        assertNotNull(actualStorageAttributes(dangling), "dangling is an existing entry")
        assertTrue(runCatching { dangling.toRealPath() }.isFailure)
        assertNull(boundary.resolveChild(dangling.toFile()))
        val first = f.alias("ce/no-backup/cycle-a", f.external)
        val second = f.alias("ce/no-backup/cycle-b", f.external)
        f.retarget(first, second)
        f.retarget(second, first, verifyTarget = false)
        assertNotNull(actualStorageAttributes(first), "cycle is an existing entry")
        assertTrue(runCatching { first.toRealPath() }.isFailure)
        assertNull(boundary.resolveChild(first.toFile()))
    }

    @Test
    fun `only entry NoSuchFileException denotes missing`() = withStoragePaths { f ->
        val path = f.ce.resolve("missing")
        val missing = NoSuchFileException(path.toString())
        assertEquals(path, resolveStorageDirectory(path.toFile()) { if (it == path) throw missing else actualStorageAttributes(it) })
        listOf(AccessDeniedException("private"), IOException("private"), SecurityException("private"), OutOfMemoryError("fatal")).forEach { failure ->
            val result = runCatching {
                resolveStorageDirectory(path.toFile()) { if (it == path) throw failure else actualStorageAttributes(it) }
            }
            assertSame(failure, result.exceptionOrNull(), "entry failure must propagate unchanged")
        }
        val target = f.dir("ce/target")
        var reads = 0
        val targetMissing = NoSuchFileException("target-lookup")
        val result = runCatching {
            resolveStorageDirectory(target.toFile()) {
                if (it == target && ++reads == 2) throw targetMissing
                actualStorageAttributes(it)
            }
        }
        assertSame(targetMissing, result.exceptionOrNull(), "only entry probe may classify absence")
    }

    @Test
    fun `resolved targets must be directories`() = withStoragePaths { f ->
        val target = f.dir("ce/target")
        val ordinaryFile = f.file("regular", "ordinary")
        var reads = 0
        val result = runCatching {
            resolveStorageDirectory(target.toFile()) {
                if (it == target && ++reads == 2) actualStorageAttributes(ordinaryFile) else actualStorageAttributes(it)
            }
        }
        assertTrue(result.exceptionOrNull() is Exception, "resolved target directory guard must refuse regular attributes")
    }

    @Test
    fun `saved DP exclusion does not follow a retargeted alias`() = withStoragePaths { f ->
        val root = f.dir("ce/no-backup")
        val forbidden = f.dir("ce/no-backup/forbidden")
        val alias = f.alias("dp-alias", forbidden)
        val boundary = assertNotNull(StoragePathBoundary.create(root.toFile(), f.ce.toFile(), alias.toFile()))
        f.retarget(alias, f.external)
        assertNull(boundary.resolveChild(forbidden.resolve("child").toFile()))
    }

    @Test
    fun `child toPath failures are closed with fatal identity`() = withStoragePaths { f ->
        val boundary = f.boundary(f.dir("ce/no-backup"))
        listOf(IOException("private"), SecurityException("private"), OutOfMemoryError("fatal"), ThreadDeath()).forEach { failure ->
            val result = runCatching { boundary.resolveChild(failingPath(File(boundary.directory, "child"), failure)) }
            if (failure is Error) assertSame(failure, result.exceptionOrNull()) else {
                assertTrue(result.isSuccess)
                assertNull(result.getOrNull())
            }
        }
    }
}

private fun failingPath(file: File, failure: Throwable): File = object : File(file.path) {
    override fun toPath(): Path = throw failure
}

private fun actualStorageAttributes(path: Path): BasicFileAttributes =
    Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)

internal fun withStoragePaths(test: (StoragePathFixture) -> Unit) {
    StoragePathFixture().use(test)
}

internal class StoragePathFixture : AutoCloseable {
    private val workspace = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.isDirectory(it.resolve("android")) }
    val root: Path = Files.createTempDirectory(
        Files.createDirectories(workspace.resolve("_local/storage-path-boundary/fixtures")), "case-",
    ).toRealPath()
    private val owned = linkedSetOf(root)
    private val links = linkedSetOf<Path>()
    val ce = dir("ce")
    val dp = dir("dp")
    val external = dir("external")

    fun dir(name: String): Path {
        val path = checked(root.resolve(name))
        var current = root
        for (part in root.relativize(path)) {
            current = current.resolve(part)
            if (!owned.contains(current)) {
                Files.createDirectory(current)
                owned.add(current)
            }
        }
        file(root.relativize(path.resolve(".marker")).toString(), "marker:$name")
        return path
    }

    fun file(name: String, content: String): Path = checked(root.resolve(name)).also {
        it.toFile().writeText(content, Charsets.UTF_8)
        owned.add(it)
    }

    fun boundary(path: Path): StoragePathBoundary =
        assertNotNull(StoragePathBoundary.create(path.toFile(), ce.toFile(), dp.toFile()))

    fun alias(name: String, target: Path, verifyTarget: Boolean = true): Path {
        val link = checked(root.resolve(name))
        checked(target)
        links.add(link)
        if (System.getProperty("os.name").startsWith("Windows")) {
            val process = ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), target.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            assertEquals(0, process.waitFor(), "Windows Junction creation: $output")
        } else Files.createSymbolicLink(link, target)
        if (verifyTarget) {
            assertEquals(target.toRealPath(), link.toRealPath(), "real alias target")
            assertEquals(target.resolve(".marker").toFile().readText(Charsets.UTF_8), link.resolve(".marker").toFile().readText(Charsets.UTF_8), "target marker")
        }
        return link
    }

    fun retarget(link: Path, target: Path, verifyTarget: Boolean = true) {
        Files.delete(checked(link))
        links.remove(link)
        alias(root.relativize(link).toString(), target, verifyTarget)
    }

    fun park(path: Path, name: String) {
        val destination = checked(root.resolve(name))
        Files.move(checked(path), destination)
        val moved = owned.filter { it.startsWith(path) }
        owned.removeAll(moved.toSet())
        moved.forEach { owned.add(destination.resolve(path.relativize(it))) }
    }

    fun removeDirectory(path: Path) {
        Files.delete(checked(path.resolve(".marker")))
        owned.remove(path.resolve(".marker"))
        Files.delete(checked(path))
        owned.remove(path)
    }

    private fun checked(path: Path): Path {
        val absolute = path.toAbsolutePath().normalize()
        require(absolute.startsWith(root) && absolute != root)
        return absolute
    }

    override fun close() {
        links.toList().asReversed().forEach { Files.deleteIfExists(checked(it)) }
        owned.filter { it.fileName.toString() == ".marker" }.forEach { assertTrue(Files.isRegularFile(it), "link deletion preserved target") }
        owned.sortedByDescending { it.nameCount }.forEach { Files.delete(it) }
        assertFalse(Files.exists(root, NOFOLLOW_LINKS), "owned fixture cleanup")
    }
}

/*
 * R4: 35/35 killed; c0/t1 = compile exit 0/test exit 1; each named primary is java.lang.AssertionError.
 * Production SHA-256: 41DCC7DFA834D3C04AF6EB2CF925B70FE5EF700C32EC739D0CEC54509204F3DB
 * Pre-receipt test SHA-256: 6CDE4BE12DD76B26056E645F03EF5415DCDC04DE0A1F2DAE67997433F87B1202
 * Commands: :app:compileDebugUnitTestKotlin; :app:testDebugUnitTest --tests nz.myinspection.app.platform.StoragePathBoundaryTest
 * Both use cmd /c android\gradlew.bat -p android --offline --no-daemon --no-build-cache -q.
 * Restored full DoD: exit 0,206 app/20 boundary,0 failures/errors/skips; post-tail same DoD required: :app:testDebugUnitTest :app:assembleDebug --rerun-tasks.
 * Windows real Junctions: each run 20 cases/0 skips, cleanup empty; POSIX not run. Secondary exceptions are not kill evidence.
 * Evidence: _local/storage-path-boundary/r4/Nxx/{receipt.json,boundary.xml,compile-exit.json,test-exit.json,mutant.bytes}.
 * Old Policy verdicts/pins remain historical; M34 stays with Policy integration, M07-11 with Android.
 * @@ -33 +33 @@ N01 test="real aliases resolve candidate app and DP roots" [M28] c0/t1 sha=E9F21C35C53EB6698E4AEC8CA3E5CBE26909B14D0F78B12C1AD3BE261988BFA4
 * -            val candidatePath = resolveStorageDirectory(candidate)
 * +            val candidatePath = candidate.toPath().toAbsolutePath().normalize()
 * @@ -34 +34 @@ N02 test="real aliases resolve candidate app and DP roots" [M29] c0/t1 sha=C95DCE5493C54E2BE0783835BB6B98EBB7A785645B3383984CA72B680946A174
 * -            val appPath = resolveStorageDirectory(appDataDir)
 * +            val appPath = appDataDir.toPath().toAbsolutePath().normalize()
 * @@ -35 +35 @@ N03 test="real aliases resolve candidate app and DP roots" [M30] c0/t1 sha=C4D1C2F80A4633A89C4ACDE8A94D8EEF725CEDBC512AB4F4FB2F4DEC64C33D1C
 * -            val dpPath = resolveStorageDirectory(deviceProtectedDataDir)
 * +            val dpPath = deviceProtectedDataDir.toPath().toAbsolutePath().normalize()
 * @@ -30 +29,0 @@ N04 test="each blank root refuses before any root is parsed" [M40] c0/t1 sha=696D068661B7FE46AFD1811CA92C22014FD2A1F9F255464ADB50954E6AA2C0B9
 * -            check(candidate.path.isNotBlank())
 * @@ -31 +30,0 @@ N05 test="each blank root refuses before any root is parsed" [M41] c0/t1 sha=BA320CB111DA13EB15A5AFCBB186A3844289A1AA6D40FC8E49E08ACB3FF0F583
 * -            check(appDataDir.path.isNotBlank())
 * @@ -32 +31,0 @@ N06 test="each blank root refuses before any root is parsed" [M42] c0/t1 sha=B52B2EA24695196AFC9746831C7916B6E94B4668834FFEB3CB69849A09100A4B
 * -            check(deviceProtectedDataDir.path.isNotBlank())
 * @@ -30,0 +31 @@ N07 test="each blank root refuses before any root is parsed" c0/t1 sha=F05C832B73FBB4825C825D267582E92024313901521853B700FA9A56F5BCF963
 * +            val candidatePath = resolveStorageDirectory(candidate)
 * @@ -33 +33,0 @@ N07 c0/t1
 * -            val candidatePath = resolveStorageDirectory(candidate)
 * @@ -36 +36 @@ N08 test="roots obey normalized strict CE and DP boundaries" [M31] c0/t1 sha=63FD175AA05EA79407210E9760A0DF418A34119DF7DAA31D3262CADF0D556478
 * -            if (candidatePath == appPath || !candidatePath.startsWith(appPath) || candidatePath.startsWith(dpPath)) {
 * +            if (!candidatePath.startsWith(appPath) || candidatePath.startsWith(dpPath)) {
 * @@ -36 +36 @@ N09 test="roots obey normalized strict CE and DP boundaries" [M32] c0/t1 sha=20CE8656CF453014722E5F8B431E232C447BC00BF672195C0F7EB9EABAEAA8BF
 * -            if (candidatePath == appPath || !candidatePath.startsWith(appPath) || candidatePath.startsWith(dpPath)) {
 * +            if (candidatePath == appPath || candidatePath.startsWith(dpPath)) {
 * @@ -36 +36 @@ N10 test="actual DP root is refused even when it is also app root" [M33] c0/t1 sha=01BEA6A52B5A0DC41A14189A1DC418ED9A8E105F283818EEFE6CBF3ED5DE7D35
 * -            if (candidatePath == appPath || !candidatePath.startsWith(appPath) || candidatePath.startsWith(dpPath)) {
 * +            if (candidatePath == appPath || !candidatePath.startsWith(appPath)) {
 * @@ -36 +36 @@ N11 test="roots obey normalized strict CE and DP boundaries" c0/t1 sha=353F00D5968C32653A344146E64040C016E1B026D83E0847E9E0C80C648D607F
 * -            if (candidatePath == appPath || !candidatePath.startsWith(appPath) || candidatePath.startsWith(dpPath)) {
 * +            if (candidatePath == appPath || !candidatePath.toString().startsWith(appPath.toString()) || candidatePath.startsWith(dpPath)) {
 * @@ -39 +39 @@ N12 test="saved candidate is the exact path checked once" c0/t1 sha=78C892F85234B7CF42343EB388A0CC8D79459FA20B6DA60ED371C59E6701CE78
 * -                StoragePathBoundary(candidatePath, dpPath)
 * +                StoragePathBoundary(resolveStorageDirectory(candidate), dpPath)
 * @@ -39 +39 @@ N13 test="source alias retarget does not move the saved root" c0/t1 sha=6FF24CDADBE174FDA215ED28817B7A0427AB8C099ADDAC5CF2D20D9654D82561
 * -                StoragePathBoundary(candidatePath, dpPath)
 * +                StoragePathBoundary(candidate.toPath().toAbsolutePath(), dpPath)
 * @@ -19 +19 @@ N14 test="replacement of saved directory refuses children" c0/t1 sha=CC6DF94ECE295C33A2D2633313F4DC1821B9853F19A6DCF7CE5038E57C39A9E2
 * -        if (child == rootPath || !child.startsWith(rootPath) || child.startsWith(deviceProtectedPath)) {
 * +        if (child == rootPath || !child.startsWith(rootPath.toRealPath()) || child.startsWith(deviceProtectedPath)) {
 * @@ -18 +18 @@ N15 test="missing suffix replaced by alias refuses children" c0/t1 sha=E9C482AC58215D4F8151867E6121EBA72F883679701C48D800F9C725D96B9F31
 * -        val child = resolveStorageDirectory(candidate)
 * +        val child = candidate.toPath().toAbsolutePath().normalize()
 * @@ -19 +19 @@ N16 test="child boundaries reject root siblings external and DP" c0/t1 sha=96642A0DFFCE305DE05493B67C3367AEFAC6C7D46316979944BE587307545853
 * -        if (child == rootPath || !child.startsWith(rootPath) || child.startsWith(deviceProtectedPath)) {
 * +        if (!child.startsWith(rootPath) || child.startsWith(deviceProtectedPath)) {
 * @@ -19 +19 @@ N17 test="child boundaries reject root siblings external and DP" c0/t1 sha=478FBB94EC6BD814017E7BC461ABF0910304411DCC46C5762E6B62537F340B12
 * -        if (child == rootPath || !child.startsWith(rootPath) || child.startsWith(deviceProtectedPath)) {
 * +        if (child == rootPath || child.startsWith(deviceProtectedPath)) {
 * @@ -19 +19 @@ N18 test="child boundaries reject root siblings external and DP" c0/t1 sha=407C02B74FC51877A3B7671E63872F9BFDC9C38E10C594CE8FD50C9995899841
 * -        if (child == rootPath || !child.startsWith(rootPath) || child.startsWith(deviceProtectedPath)) {
 * +        if (child == rootPath || !child.startsWith(rootPath)) {
 * @@ -19 +19 @@ N19 test="child boundaries reject root siblings external and DP" c0/t1 sha=27AADFE63BB992DF1E2A63F299AEBC43627DA7425C54E1BF11F09B3590F7F898
 * -        if (child == rootPath || !child.startsWith(rootPath) || child.startsWith(deviceProtectedPath)) {
 * +        if (child == rootPath || !child.toString().startsWith(rootPath.toString()) || child.startsWith(deviceProtectedPath)) {
 * @@ -22 +22 @@ N20 test="legal child alias returns exactly the checked target" c0/t1 sha=E6489F8BBE71D97C8B7E72B50D320172F8D262313E4AF90B191B2FEFB82281CB
 * -            child.toFile()
 * +            candidate
 * @@ -80 +80 @@ N21 test="real aliases resolve candidate app and DP roots" c0/t1 sha=618BD0ABD8F0E722FB52196FEAC3115177B4C96269F7579D1FC30064F5F4AF7A
 * -    val real = path.toRealPath()
 * +    val real = path.toAbsolutePath().normalize()
 * @@ -51 +51 @@ N22 test="parent follows the resolved alias target" c0/t1 sha=D360CCDE8B3CC10D24BB0AED9D8A3C578E6E2AFDD6C9CBA59C2AB573D29F9A72
 * -    val absolute = directory.toPath().toAbsolutePath()
 * +    val absolute = directory.toPath().toAbsolutePath().normalize()
 * @@ -55 +55 @@ N23 test="missing parent resumes real alias resolution" c0/t1 sha=15F233F120651BEB4E78FDF69F82101D406B6F8804DCCC541F1480AE078279CA
 * -    for (segment in absolute) {
 * +    for ((index, segment) in absolute.withIndex()) {
 * @@ -68 +68,8 @@ N23 c0/t1
 * -                current = if (present) existingStorageDirectory(next, readAttributes) else next
 * +                current = if (present) existingStorageDirectory(next, readAttributes) else {
 * +                    val suffix = if (index + 1 < absolute.nameCount) {
 * +                        absolute.subpath(index + 1, absolute.nameCount)
 * +                    } else {
 * +                        absolute.fileSystem.getPath("")
 * +                    }
 * +                    return next.resolve(suffix).normalize()
 * +                }
 * @@ -64 +64 @@ N24 test="only entry NoSuchFileException denotes missing" c0/t1 sha=4E7BC70E2D1B5464F7BB07852A71399DBF443AFEB81B143EDF5F7958BBCD4863
 * -                } catch (_: NoSuchFileException) {
 * +                } catch (_: java.io.IOException) {
 * @@ -64 +64,2 @@ N25 test="only entry NoSuchFileException denotes missing" c0/t1 sha=189924F1148FA7C7BF7F14D96568247DD168BB1881CED544442299C399DCFF0D
 * -                } catch (_: NoSuchFileException) {
 * +                } catch (failure: Exception) {
 * +                    if (failure !is NoSuchFileException && failure !is SecurityException) throw failure
 * @@ -61 +61 @@ N26 test="only entry NoSuchFileException denotes missing" c0/t1 sha=AF554604AFDE5AF03C6F9D9711CD3D75A0AC42F12C21D5188BF46629493FFC91
 * -                val present = try {
 * +                current = try {
 * @@ -63 +63 @@ N26 c0/t1
 * -                    true
 * +                    existingStorageDirectory(next, readAttributes)
 * @@ -65 +65 @@ N26 c0/t1
 * -                    false
 * +                    next
 * @@ -67,2 +66,0 @@ N26 c0/t1
 * -                // A missing segment can be followed by ".." and another existing alias.
 * -                current = if (present) existingStorageDirectory(next, readAttributes) else next
 * @@ -81 +81 @@ N27 test="resolved targets must be directories" c0/t1 sha=37205DC44B13257327D24BF50D5C0E6FCFBAD27F2485CEC15D3B6265EF1F231A
 * -    check(readAttributes(real).isDirectory)
 * +    readAttributes(real)
 * @@ -86 +86 @@ N28 test="regular dangling and cyclic entries fail closed" c0/t1 sha=EB6D0F84A4EA6E38095F5CE2563284B51AF0B3B4CC96D6A4E96E6C22273E0C09
 * -    Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
 * +    Files.readAttributes(path, BasicFileAttributes::class.java)
 * @@ -41 +41 @@ N29 test="root toPath fatal errors retain identity" [M37] c0/t1 sha=D510D678C51422977D802EF2BBA7010211EC5C19E7DCA548C4034DEFDEF54454
 * -        } catch (_: Exception) {
 * +        } catch (_: Throwable) {
 * @@ -41,2 +41,2 @@ N30 test="ordinary toPath failures return null" [M38] c0/t1 sha=CB4D7FF99047B56681127B929E67D71972E4E70E39539C3B281FA0AC0FB39AA5
 * -        } catch (_: Exception) {
 * -            null
 * +        } catch (failure: Exception) {
 * +            throw failure
 * @@ -24 +24 @@ N31 test="child toPath failures are closed with fatal identity" c0/t1 sha=200DD05D417E1FA51A9265B1E56C2F397AD166A5E950654CE481FBE5644CE487
 * -    } catch (_: Exception) {
 * +    } catch (_: Throwable) {
 * @@ -24,2 +24,2 @@ N32 test="child toPath failures are closed with fatal identity" c0/t1 sha=FF88141D51827855E3BA3DF4303BC46C79AD6D084C2C26FC9A9519898F9954BD
 * -    } catch (_: Exception) {
 * -        null
 * +    } catch (failure: Exception) {
 * +        throw failure
 * @@ -68 +68 @@ N33 test="absent roots and multilevel children stay uncreated" c0/t1 sha=F1205139C9484CEFE7710013C92D1BFD76DD769A013D9BC6236133571DBF1255
 * -                current = if (present) existingStorageDirectory(next, readAttributes) else next
 * +                current = if (present) existingStorageDirectory(next, readAttributes) else throw NoSuchFileException(next.toString())
 * @@ -58 +58 @@ N34 test="parent follows the resolved alias target" c0/t1 sha=758962344DCEF4B49C300EC08598D26E14FAEF75A07BAECD16DBC75FBA4F56C1
 * -            ".." -> current = current.parent ?: current
 * +            ".." -> Unit
 * @@ -39 +39 @@ N35 test="saved DP exclusion does not follow a retargeted alias" c0/t1 sha=897DEB322E4115CCBD0572E8B47623888EA97C16F4BB0747E199D342D6FA014C
 * -                StoragePathBoundary(candidatePath, dpPath)
 * +                StoragePathBoundary(candidatePath, deviceProtectedDataDir.toPath().toAbsolutePath())
 */
