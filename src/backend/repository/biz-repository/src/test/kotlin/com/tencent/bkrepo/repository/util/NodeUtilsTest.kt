package com.tencent.bkrepo.repository.util

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.repository.util.PathUtils.ROOT
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("节点工具类测试")
class NodeUtilsTest {

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

        assertDoesNotThrow { PathUtils.parseFullPath("/1/2/3/4/5/6/7/8/9/10") }
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
        assertDoesNotThrow { PathUtils.parseFullPath("/.1/") }
        assertDoesNotThrow { PathUtils.parseFullPath("/..../") }
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
        assertEquals("/a/", PathUtils.getParentPath("/a/b"))
        assertEquals("/a/", PathUtils.getParentPath("/a/b.txt"))
        assertEquals("/a/b/", PathUtils.getParentPath("/a/b/c/"))
        assertEquals("/", PathUtils.getParentPath("/a"))
        assertEquals("/", PathUtils.getParentPath("/"))
    }

    @Test
    fun testGetName() {
        assertEquals("b", PathUtils.getName("/a/b"))
        assertEquals("b.txt", PathUtils.getName("/a/b.txt"))
        assertEquals("", PathUtils.getName("/"))
        assertEquals("c", PathUtils.getName("/a/b/c/"))
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
