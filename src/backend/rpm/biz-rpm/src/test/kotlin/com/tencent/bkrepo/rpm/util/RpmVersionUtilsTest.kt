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

    @Test
    fun checkFormat() {
        val file = File("/Users/weaving/Downloads/24c23c2606313a9578ab80e809ffcfef015eae5d-primary.xml")
        var line: String?
        val string = StringBuilder()
        BufferedReader(InputStreamReader(FileInputStream(file))).use { br ->
            while (br.readLine().also { line = it } != null) {
                string.append(line)
            }
        }

        val xml = string.toString()
        val primary = XStreamUtil.xmlToObject(xml)

    }
}
