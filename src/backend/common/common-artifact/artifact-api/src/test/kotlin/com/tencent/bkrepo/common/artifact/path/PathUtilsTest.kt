package com.tencent.bkrepo.common.artifact.path

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.path.PathUtils.ROOT
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("路径工具类测试")
class PathUtilsTest {

    @Test
    fun testParseDirName() {
        assertEquals(ROOT, PathUtils.parseFullPath("/"))
        assertEquals(ROOT, PathUtils.parseFullPath("  /   "))
        assertEquals(ROOT, PathUtils.parseFullPath("  "))
        assertEquals("/a", PathUtils.parseFullPath("  /   a"))
        assertEquals(
            "/a/b",
            PathUtils.parseFullPath("  /   a  /b")
        )
        assertEquals(
            "/a/b",
            PathUtils.parseFullPath("  /   a  /b/")
        )

        Assertions.assertDoesNotThrow { PathUtils.parseFullPath("/1/2/3/4/5/6/7/8/9/10") }
        assertThrows<ErrorCodeException> {
            PathUtils.parseFullPath(
                "/../"
            )
        }
        assertThrows<ErrorCodeException> {
            PathUtils.parseFullPath(
                "/./"
            )
        }
        Assertions.assertDoesNotThrow { PathUtils.parseFullPath("/.1/") }
        Assertions.assertDoesNotThrow { PathUtils.parseFullPath("/..../") }
    }

    @Test
    fun testParseFileName() {
        assertEquals("abc", PathUtils.parseFileName("abc"))
        assertEquals("中文测试", PathUtils.parseFileName("中文测试"))
        assertEquals(
            "！@……&%#&¥*@#¥*（！——#！!@(#(!\$",
            PathUtils.parseFileName("！@……&%#&¥*@#¥*（！——#！!@(#(!$")
        )
        assertThrows<ErrorCodeException> {
            PathUtils.parseFileName(
                ""
            )
        }
        assertThrows<ErrorCodeException> {
            PathUtils.parseFileName(
                "   "
            )
        }
        assertThrows<ErrorCodeException> {
            PathUtils.parseFileName(
                ".."
            )
        }
        assertThrows<ErrorCodeException> {
            PathUtils.parseFileName(
                "."
            )
        }
        assertThrows<ErrorCodeException> {
            PathUtils.parseFileName(
                "dsjfkjafk/dsajdklsak"
            )
        }
    }

    @Test
    fun testCombineFullPath() {
        assertEquals("/a", PathUtils.combineFullPath("", "a"))
        assertEquals("/a/b", PathUtils.combineFullPath("/a", "b"))
        assertEquals(
            "/a/b",
            PathUtils.combineFullPath("/a/", "b")
        )
    }

    @Test
    fun testGetParentPath() {
        assertEquals("/a/", PathUtils.resolvePath("/a/b"))
        assertEquals("/a/", PathUtils.resolvePath("/a/b.txt"))
        assertEquals("/a/b/", PathUtils.resolvePath("/a/b/c/"))
        assertEquals("/", PathUtils.resolvePath("/a"))
        assertEquals("/", PathUtils.resolvePath("/"))
    }

    @Test
    fun testGetName() {
        assertEquals("b", PathUtils.resolveName("/a/b"))
        assertEquals("b.txt", PathUtils.resolveName("/a/b.txt"))
        assertEquals("", PathUtils.resolveName("/"))
        assertEquals("c", PathUtils.resolveName("/a/b/c/"))
    }

    @Test
    fun testEscapeRegex() {
        assertEquals("""\.\*""", PathUtils.escapeRegex(".*"))
        assertEquals(
            """/\.\*\|\^/a/""",
            PathUtils.escapeRegex("/.*|^/a/")
        )
    }

    @Test
    fun testFormatPath() {
        assertEquals("/.*|^/a/", PathUtils.formatPath("/.*|^/a"))
        assertEquals(
            "/.*|^/a",
            PathUtils.formatFullPath("/.*|^/a")
        )

        assertEquals("/a/b/", PathUtils.formatPath("/a/b"))
        assertEquals("/a/b/", PathUtils.formatPath("/a/b/"))
    }
}
