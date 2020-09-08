package com.tencent.bkrepo.common.artifact.hash

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class HashAlgorithmTest {

    private val content = "Hello, world!"
    private val md5 = "6cd3556deb0da54bca060b4c39479839"
    private val sha1 = "943a702d06f34599aee1f8da8ef9f7296031d699"
    private val sha256 = "315f5bdb76d078c43b8ac0064e4a0164612b1fce77c869345bfc94c75894edd3"

    @Test
    fun testInputStream() {
        Assertions.assertEquals(md5, content.byteInputStream().md5())
        Assertions.assertEquals(sha1, content.byteInputStream().sha1())
        Assertions.assertEquals(sha256, content.byteInputStream().sha256())
    }

    @Test
    fun testString() {
        Assertions.assertEquals(md5, content.md5())
        Assertions.assertEquals(sha1, content.sha1())
        Assertions.assertEquals(sha256, content.sha256())
    }
}
