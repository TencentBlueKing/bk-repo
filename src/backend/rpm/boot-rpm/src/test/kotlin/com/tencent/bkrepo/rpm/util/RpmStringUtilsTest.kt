package com.tencent.bkrepo.rpm.util

import com.tencent.bkrepo.rpm.util.RpmStringUtils.toRpmVersion
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class RpmStringUtilsTest {
    @Test
    fun toRpmVersionTest() {
        val str1 = "/os/7/x86_64/openresty-pcre-8.40-1.el7.centos.x86_64.rpm"
        val str2 = "/openresty-pcre-8.40-1.el7.centos.x86_64.rpm"
        val str3 = "openresty-pcre-8.40-1.el7.centos.x86_64.rpm"

        val rpmVersion1 = str1.toRpmVersion()
        Assertions.assertEquals(str3, rpmVersion1.toString())
        val rpmVersion2 = str2.toRpmVersion()
        Assertions.assertEquals(str3, rpmVersion2.toString())
        val rpmVersion3 = str3.toRpmVersion()
        Assertions.assertEquals(str3, rpmVersion3.toString())
    }
}
