package com.tencent.bkrepo.rpm.util

import com.tencent.bkrepo.rpm.util.XmlStrUtils.packagesModify
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import java.io.File

@SpringBootTest
class XmlStrUtilsTest {
    /**
     * 按照仓库设置的repodata 深度分割请求参数
     */
    @Test
    fun splitUriByDepthTest() {
        val uri = "/7/os/x86_64/hello-world-1-1.x86_64.rpm"
        val depth = 3
        val repodataUri = XmlStrUtils.splitUriByDepth(uri, depth)
        Assertions.assertEquals("7/os/x86_64/", repodataUri.repodataPath)
        Assertions.assertEquals("hello-world-1-1.x86_64.rpm", repodataUri.artifactRelativePath)
    }

    @Test
    fun packagesModifyTest() {
        val file = File("/Users/weaving/Downloads/0aab2adc94b2eef328f6d4f7ee1d686c816d124d-primary.xml")
        val resultFile = file.packagesModify("primary", true)
        println(resultFile.absolutePath)
    }
}
