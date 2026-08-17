package nz.myinspection.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 存储布局唯一派生点（关键不变量：全仓禁手拼路径）。断言精确串形而非子串，
 * 否则实现把段序、分隔符或扩展名换掉都能蒙混过关（L165）。
 */
class MediaPathsTest {
    @Test
    fun `photoRelPath derives the exact photos slash property slash inspection slash photoId dot jpg layout`() {
        assertEquals(
            "photos/prop-1/insp-1/photo-1.jpg",
            MediaPaths.photoRelPath(propertyId = "prop-1", inspectionId = "insp-1", photoId = "photo-1"),
        )
    }

    @Test
    fun `changing only the photoId changes only the file segment, proving the path is derived not templated by a wider key`() {
        val a = MediaPaths.photoRelPath(propertyId = "p", inspectionId = "i", photoId = "photo-a")
        val b = MediaPaths.photoRelPath(propertyId = "p", inspectionId = "i", photoId = "photo-b")
        assertEquals("photos/p/i/photo-a.jpg", a)
        assertEquals("photos/p/i/photo-b.jpg", b)
    }

    @Test
    fun `rejects a segment containing a path separator, which would let a corrupted id escape its own directory`() {
        assertFailsWith<IllegalArgumentException> {
            MediaPaths.photoRelPath(propertyId = "../etc", inspectionId = "insp-1", photoId = "photo-1")
        }
        assertFailsWith<IllegalArgumentException> {
            MediaPaths.photoRelPath(propertyId = "prop-1", inspectionId = "a/b", photoId = "photo-1")
        }
        assertFailsWith<IllegalArgumentException> {
            MediaPaths.photoRelPath(propertyId = "prop-1", inspectionId = "insp-1", photoId = "..")
        }
    }

    @Test
    fun `rejects a blank segment`() {
        assertFailsWith<IllegalArgumentException> {
            MediaPaths.photoRelPath(propertyId = "", inspectionId = "insp-1", photoId = "photo-1")
        }
    }

    // 下面三条各自只打一个守卫子句（反斜杠 / 空白 / 单点）——每条都是"删掉那一句就变绿"的那个缺口的
    // 唯一杀手：空串向量杀不掉 isNotBlank→isNotEmpty，".." 向量杀不掉 `value != "."`，
    // 正斜杠向量杀不掉 `!value.contains('\\')`。派生侧与形状侧各断一次，两条链路都不能漏。

    @Test
    fun `rejects a segment containing a backslash, which Windows-side callers would read as a separator`() {
        assertFailsWith<IllegalArgumentException> {
            MediaPaths.photoRelPath(propertyId = "prop\\1", inspectionId = "insp-1", photoId = "photo-1")
        }
        assertFalse(MediaPaths.isPhotoRelPathShape("photos/prop\\1/insp-1/photo-1.jpg"), "a backslash segment must not pass the shape gate")
    }

    @Test
    fun `rejects a whitespace-only segment, not just an empty one`() {
        assertFailsWith<IllegalArgumentException> {
            MediaPaths.photoRelPath(propertyId = "   ", inspectionId = "insp-1", photoId = "photo-1")
        }
        assertFalse(MediaPaths.isPhotoRelPathShape("photos/   /insp-1/photo-1.jpg"), "a whitespace-only segment must not pass the shape gate")
    }

    @Test
    fun `rejects a single-dot segment, not just a double-dot one`() {
        assertFailsWith<IllegalArgumentException> {
            MediaPaths.photoRelPath(propertyId = ".", inspectionId = "insp-1", photoId = "photo-1")
        }
        assertFalse(MediaPaths.isPhotoRelPathShape("photos/./insp-1/photo-1.jpg"), "a single-dot segment must not pass the shape gate")
    }

    @Test
    fun `isPhotoRelPathShape accepts exactly what photoRelPath derives`() {
        val derived = MediaPaths.photoRelPath(propertyId = "prop-1", inspectionId = "insp-1", photoId = "photo-1")
        assertTrue(MediaPaths.isPhotoRelPathShape(derived))
    }

    @Test
    fun `isPhotoRelPathShape rejects a path outside the photos namespace, a wrong segment count, a wrong extension, and a traversal segment`() {
        // A corrupted/cross-table orphan row could carry any of these — the cleanup path must refuse
        // every one of them, not just the ones that also happen to escape the media root.
        assertFalse(MediaPaths.isPhotoRelPathShape("audio/x/y/z.m4a"), "wrong top-level namespace")
        assertFalse(MediaPaths.isPhotoRelPathShape("photos/a.jpg"), "too few segments (missing property/inspection)")
        assertFalse(MediaPaths.isPhotoRelPathShape("photos/a/b/c/d.jpg"), "too many segments")
        assertFalse(MediaPaths.isPhotoRelPathShape("photos/a/b/c.png"), "wrong extension")
        assertFalse(MediaPaths.isPhotoRelPathShape("."), "must not resolve to the media root itself")
        assertFalse(MediaPaths.isPhotoRelPathShape("photos/../b/c.jpg"), "a segment that is itself a traversal token, even with the right segment count")
        assertFalse(MediaPaths.isPhotoRelPathShape("photos/a/b/c.JPG"), "extension case must match exactly (photoRelPath always emits lowercase .jpg)")
    }
}
