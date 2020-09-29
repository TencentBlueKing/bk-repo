package com.tencent.bkrepo.rpm.util

import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import java.util.regex.Pattern

internal class RpmStringUtilsTest {

    @Test
    fun formatTest() {
        assertEquals("7.os.x86_64", "7/os/x86_64".format("/", ".") )
    }

    @Test
    fun test() {
        val url = "/os/7/x86_64/hello-world-1-1.x86_64.rpm"
        assertEquals("/os/7/x86_64", url.substringBeforeLast("/"))
        assertEquals("hello-world-1-1.x86_64.rpm", url.substringAfterLast("/"))
        val regex = """^(.+)-([0-9a-zA-Z\.]+)-([0-9a-zA-Z\.]+)\.(x86_64|i386|i586|i686|noarch])\.rpm$"""
        val matcher = Pattern.compile(regex).matcher("hello-world-1-1.x86_64.rpm")
        if (matcher.find()) {
            assertEquals("hello-world", matcher.group(1))
            assertEquals("1", matcher.group(2))
            assertEquals("1", matcher.group(3))
            assertEquals("x86_64", matcher.group(4))
        }
    }
}