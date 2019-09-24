package com.tencent.bkrepo.repository.util

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.repository.util.NodeUtils.ROOT_DIR
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * 节点工具类测试
 *
 * @author: carrypan
 * @date: 2019-09-24
 */
@DisplayName("节点工具类测试")
internal class NodeUtilsTest {

    @Test
    fun parseDirName() {
        assertEquals(ROOT_DIR, NodeUtils.parseDirName("/"))
        assertEquals(ROOT_DIR, NodeUtils.parseDirName("  /   "))
        assertEquals("/a", NodeUtils.parseDirName("  /   a"))
        assertEquals("/a/b", NodeUtils.parseDirName("  /   a  /b"))
        assertEquals("/a/b", NodeUtils.parseDirName("  /   a  /b/"))

        assertThrows<ErrorCodeException> { NodeUtils.parseDirName(" ") }
        assertDoesNotThrow { NodeUtils.parseDirName("/1/2/3/4/5/6/7/8/9/10") }
        assertThrows<ErrorCodeException> { NodeUtils.parseDirName("/1/2/3/4/5/6/7/8/9/10/11") }
        assertThrows<ErrorCodeException> { NodeUtils.parseDirName("/../") }
        assertThrows<ErrorCodeException> { NodeUtils.parseDirName("/./") }
        assertDoesNotThrow { NodeUtils.parseDirName("/.1/") }
        assertDoesNotThrow { NodeUtils.parseDirName("/..../") }
    }

    @Test
    fun parseFileName() {
        assertEquals("abc", NodeUtils.parseFileName("abc"))
        assertEquals("中文测试", NodeUtils.parseFileName("中文测试"))
        assertEquals("！@……&%#&¥*@#¥*（！——#！!@(#(!\$", NodeUtils.parseFileName("！@……&%#&¥*@#¥*（！——#！!@(#(!$"))
        assertThrows<ErrorCodeException> { NodeUtils.parseFileName("") }
        assertThrows<ErrorCodeException> { NodeUtils.parseFileName("   ") }
        assertThrows<ErrorCodeException> { NodeUtils.parseFileName("..") }
        assertThrows<ErrorCodeException> { NodeUtils.parseFileName(".") }
        assertThrows<ErrorCodeException> { NodeUtils.parseFileName("dsjfkjafk/dsajdklsak") }
    }

    @Test
    fun combineFullPath() {
        assertEquals("/a", NodeUtils.combineFullPath("/", "a"))
        assertEquals("/a/b", NodeUtils.combineFullPath("/a", "b"))
    }

    @Test
    fun getParentPath() {
        assertEquals("/a", NodeUtils.getParentPath("/a/b"))
        assertEquals("/a", NodeUtils.getParentPath("/a/b.txt"))
        assertEquals("/", NodeUtils.getParentPath("/a"))
        assertEquals("", NodeUtils.getParentPath("/"))
    }

    @Test
    fun getName() {
        assertEquals("b", NodeUtils.getName("/a/b"))
        assertEquals("b.txt", NodeUtils.getName("/a/b.txt"))
        assertEquals("/", NodeUtils.getName("/"))
    }
}
