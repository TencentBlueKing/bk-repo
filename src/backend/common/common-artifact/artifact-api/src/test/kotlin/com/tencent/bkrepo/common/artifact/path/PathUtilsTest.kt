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
    fun testValidateFullPath() {
        assertEquals(ROOT, PathUtils.validateFullPath("/"))
        assertEquals(ROOT, PathUtils.validateFullPath("  /   "))
        assertEquals(ROOT, PathUtils.validateFullPath("  "))
        assertEquals("/a", PathUtils.validateFullPath("  /   a"))
        assertEquals(
            "/a/b",
            PathUtils.validateFullPath("  /   a  /b")
        )
        assertEquals(
            "/a/b",
            PathUtils.validateFullPath("  /   a  /b/")
        )

        Assertions.assertDoesNotThrow { PathUtils.validateFullPath("/1/2/3/4/5/6/7/8/9/10") }
        assertThrows<ErrorCodeException> { PathUtils.validateFullPath("/../") }
        assertEquals(ROOT, PathUtils.validateFullPath("/./"))
        assertEquals("/.1", PathUtils.validateFullPath("/.1/"))
        assertEquals("/....", PathUtils.validateFullPath("/..../"))
    }

    @Test
    fun testValidateFileName() {
        assertEquals("abc", PathUtils.validateFileName("abc"))
        assertEquals("中文测试", PathUtils.validateFileName("中文测试"))
        assertEquals(
            "！@……&%#&¥*@#¥*（！——#！!@(#(!\$",
            PathUtils.validateFileName("！@……&%#&¥*@#¥*（！——#！!@(#(!$")
        )
        assertThrows<ErrorCodeException> {
            PathUtils.validateFileName(
                ""
            )
        }
        assertThrows<ErrorCodeException> {
            PathUtils.validateFileName(
                "   "
            )
        }
        assertThrows<ErrorCodeException> {
            PathUtils.validateFileName(
                ".."
            )
        }
        assertThrows<ErrorCodeException> {
            PathUtils.validateFileName(
                "."
            )
        }
        assertThrows<ErrorCodeException> {
            PathUtils.validateFileName(
                "dsjfkjafk/dsajdklsak"
            )
        }
    }

    @Test
    fun testCombineFullPath() {
        assertEquals("/a", PathUtils.combineFullPath("", "a"))
        assertEquals("/a/b", PathUtils.combineFullPath("/a", "b"))
        assertEquals("/a/b", PathUtils.combineFullPath("/a/", "b"))
    }

    @Test
    fun testResolvePath() {
        assertEquals("/a/", PathUtils.resolvePath("/a/b"))
        assertEquals("/a/", PathUtils.resolvePath("/a/b.txt"))
        assertEquals("/a/b/", PathUtils.resolvePath("/a/b/c/"))
        assertEquals("/", PathUtils.resolvePath("/a"))
        assertEquals("/", PathUtils.resolvePath("/"))
    }

    @Test
    fun testResolveName() {
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
        assertEquals("/.*|^/a/", PathUtils.normalizePath("/.*|^/a"))
        assertEquals(
            "/.*|^/a",
            PathUtils.normalizeFullPath("/.*|^/a")
        )

        assertEquals("/a/b/c", PathUtils.normalizeFullPath("./a/b/c"))
        assertEquals("/.a/b/c", PathUtils.normalizeFullPath("./.a/./b/c/."))

        assertEquals("/a/b/", PathUtils.normalizePath("/a/b"))
        assertEquals("/a/b/", PathUtils.normalizePath("/a/b/"))
    }
}
