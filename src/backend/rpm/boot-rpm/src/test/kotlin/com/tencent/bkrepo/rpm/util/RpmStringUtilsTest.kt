package com.tencent.bkrepo.rpm.util

import com.tencent.bkrepo.rpm.util.RpmStringUtils.format
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

internal class RpmStringUtilsTest {

    @Test
    fun formatTest() {
        assertEquals("7.os.x86_64", "7/os/x86_64".format("/", ".") )
    }
}