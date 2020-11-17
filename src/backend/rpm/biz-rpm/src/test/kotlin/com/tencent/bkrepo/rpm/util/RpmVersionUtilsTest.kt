package com.tencent.bkrepo.rpm.util

import com.tencent.bkrepo.rpm.util.RpmVersionUtils.toRpmPackagePojo
import com.tencent.bkrepo.rpm.util.xStream.XStreamUtil
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.lang.StringBuilder

class RpmVersionUtilsTest {
    @Test
    fun resolverRpmVersionTest() {
        val str = "httpd-2.4.6-93.el7.centos.x86_64.rpm"
        Assertions.assertEquals(str, RpmVersionUtils.resolverRpmVersion(str).toString())
    }

    @Test
    fun toRpmPackagePojoTest() {
        val str = "/7/httpd-2.4.6-93.el7.centos.x86_64.rpm"
        Assertions.assertEquals("7", str.toRpmPackagePojo().path)
        Assertions.assertEquals("httpd", str.toRpmPackagePojo().name)
        Assertions.assertEquals("2.4.6-93.el7.centos.x86_64", str.toRpmPackagePojo().version)
    }
}
