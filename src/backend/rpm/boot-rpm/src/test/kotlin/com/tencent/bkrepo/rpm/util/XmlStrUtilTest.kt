package com.tencent.bkrepo.rpm.util

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

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
}