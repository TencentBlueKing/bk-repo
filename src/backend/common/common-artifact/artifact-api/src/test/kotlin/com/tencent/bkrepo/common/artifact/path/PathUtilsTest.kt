package com.tencent.bkrepo.common.artifact.path

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.path.PathUtils.ROOT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("路径工具类测试")
class PathUtilsTest {

    @Test
    fun testNormalizeFullPath() {
        assertEquals(ROOT, PathUtils.normalizeFullPath("/"))
        assertEquals(ROOT, PathUtils.normalizeFullPath("/////\\\\//\\/"))
        assertEquals(ROOT, PathUtils.normalizeFullPath("\\"))
        assertEquals(ROOT, PathUtils.normalizeFullPath("  /   "))
        assertEquals(ROOT, PathUtils.normalizeFullPath("  "))
        assertEquals(ROOT, PathUtils.normalizeFullPath("/./"))
        assertEquals("/a", PathUtils.normalizeFullPath("  /   a"))
        assertEquals("/a/b", PathUtils.normalizeFullPath("  /   a  /b"))
        assertEquals("/a/b", PathUtils.normalizeFullPath("./a  \\b /"))
        assertEquals("/.1", PathUtils.normalizeFullPath("/.1/"))
        assertEquals("/....", PathUtils.normalizeFullPath("/..../"))
        assertEquals("/a/b/c", PathUtils.normalizeFullPath("./a/b/c"))
        assertEquals("/.a/b/c", PathUtils.normalizeFullPath("./.a/./b/c/."))

        // test ..
        assertEquals(ROOT, PathUtils.normalizeFullPath(".."))
        assertEquals(ROOT, PathUtils.normalizeFullPath("../"))
        assertEquals(ROOT, PathUtils.normalizeFullPath("../../../"))
        assertEquals("/a", PathUtils.normalizeFullPath("../a"))
        assertEquals("/a/..b", PathUtils.normalizeFullPath("../a/..b/"))
        assertEquals("/a/.b..", PathUtils.normalizeFullPath("../a/.b.."))
        assertEquals("/a", PathUtils.normalizeFullPath("../a/ . /"))
        assertEquals("/a/. .", PathUtils.normalizeFullPath("../a/ . . "))
        assertEquals(ROOT, PathUtils.normalizeFullPath("../a/  .. /"))
        assertEquals("/1/3/6", PathUtils.normalizeFullPath("..//1/2/..//3/4/5/../../6"))
    }

    @Test
    fun testNormalizePath() {
        assertEquals(ROOT, PathUtils.normalizePath(""))
        assertEquals(ROOT, PathUtils.normalizePath("/"))
        assertEquals("/.*|^/a/", PathUtils.normalizePath("/.*|^/a"))
        assertEquals("/.*|^/a", PathUtils.normalizeFullPath("/.*|^/a"))
        assertEquals("/a/b/", PathUtils.normalizePath("/a/b"))
        assertEquals("/a/b/", PathUtils.normalizePath("/a/b/"))
    }

    @Test
    fun testValidateFileName() {
        assertEquals("abc", PathUtils.validateFileName("abc"))
        assertEquals("中文测试", PathUtils.validateFileName("中文测试"))
        assertEquals("！@……&%#&¥*@#¥*（！——#！!@(#(!$", PathUtils.validateFileName("！@……&%#&¥*@#¥*（！——#！!@(#(!$"))
        assertThrows<ErrorCodeException> { PathUtils.validateFileName("") }
        assertThrows<ErrorCodeException> { PathUtils.validateFileName("   ") }
        assertThrows<ErrorCodeException> { PathUtils.validateFileName("..") }
        assertThrows<ErrorCodeException> { PathUtils.validateFileName(".") }
        assertThrows<ErrorCodeException> { PathUtils.validateFileName("dsjfkjafk/dsajdklsak") }
        assertThrows<ErrorCodeException> { PathUtils.validateFileName(StringPool.randomString(1025)) }

        val illegalString = StringBuilder().append("/a/").append(0.toByte()).toString()
        assertThrows<ErrorCodeException> { PathUtils.validateFileName(illegalString) }
    }

    @Test
    fun testCombineFullPath() {
        assertEquals("/a", PathUtils.combineFullPath("", "a"))
        assertEquals("/a/b", PathUtils.combineFullPath("/a", "b"))
        assertEquals("/a/b", PathUtils.combineFullPath("/a", "/b"))
        assertEquals("/a/b", PathUtils.combineFullPath("/a/", "b"))
        assertEquals("/a/b", PathUtils.combineFullPath("/a/", "/b"))
    }

    @Test
    fun testResolvePath() {
        assertEquals("/", PathUtils.resolvePath(""))
        assertEquals("/", PathUtils.resolvePath("/"))
        assertEquals("/a/", PathUtils.resolvePath("/a/b"))
        assertEquals("/a/", PathUtils.resolvePath("/a/b.txt"))
        assertEquals("/a/b/", PathUtils.resolvePath("/a/b/c/"))
        assertEquals("/", PathUtils.resolvePath("/a"))
    }

    @Test
    fun testResolveName() {
        assertEquals("", PathUtils.resolveName(""))
        assertEquals("", PathUtils.resolveName("/"))
        assertEquals("b", PathUtils.resolveName("/a/b"))
        assertEquals("b.txt", PathUtils.resolveName("/a/b.txt"))
        assertEquals("c", PathUtils.resolveName("/a/b/c/"))
    }

    @Test
    fun testEscapeRegex() {
        assertEquals("""\.\*""", PathUtils.escapeRegex(".*"))
        assertEquals("""/\.\*\|\^/a/""", PathUtils.escapeRegex("/.*|^/a/"))
    }
}
