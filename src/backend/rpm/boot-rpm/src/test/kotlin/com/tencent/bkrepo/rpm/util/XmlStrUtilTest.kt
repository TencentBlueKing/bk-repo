package com.tencent.bkrepo.rpm.util

import com.tencent.bkrepo.rpm.util.XmlStrUtil.packagesPlus
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import java.lang.StringBuilder

@SpringBootTest
class XmlStrUtilTest {
    /**
     * 按照仓库设置的repodata 深度分割请求参数
     */
    @Test
    fun splitUriByDepthTest() {
        val uri = "/7/os/x86_64/hello-world-1-1.x86_64.rpm"
        val depth = 3
        val repodataUri  = XmlStrUtil.splitUriByDepth(uri, depth)
        Assertions.assertEquals("7/os/x86_64/", repodataUri.repodataPath)
        Assertions.assertEquals("hello-world-1-1.x86_64.rpm", repodataUri.artifactRelativePath)
    }

    @Test
    fun packagesPlusTest() {
        val xml01 = "<metadata xmlns=\"http://linux.duke.edu/metadata/common\" xmlns:rpm=\"http://linux.duke" +
                ".edu/metadata/rpm\" packages=\"9\">"
        val stringBuilder01 = StringBuilder(xml01)
        val result01 = stringBuilder01.packagesPlus()

        val xml02 = "<metadata xmlns=\"http://linux.duke.edu/metadata/common\" xmlns:rpm=\"http://linux.duke" +
                ".edu/metadata/rpm\" packages=\"1\">"
        val stringBuilder02 = StringBuilder(xml02)
        val result02 = stringBuilder02.packagesPlus()
        Assertions.assertEquals("<metadata xmlns=\"http://linux.duke.edu/metadata/common\" xmlns:rpm=\"http://linux" +
                ".duke.edu/metadata/rpm\" packages=\"10\">", result01)
        Assertions.assertEquals("<metadata xmlns=\"http://linux.duke.edu/metadata/common\" xmlns:rpm=\"http://linux" +
                ".duke.edu/metadata/rpm\" packages=\"2\">", result02)
    }
}