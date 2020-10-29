package com.tencent.bkrepo.rpm.util

import com.tencent.bkrepo.rpm.pojo.IndexType
import com.tencent.bkrepo.rpm.util.XmlStrUtils.packagesModify
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import java.io.File
import java.util.regex.Pattern

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
        val start = System.currentTimeMillis()
        val file = File("/Users/weaving/Downloads/6e437f1af3f3db504cb1d2fe6d453fccb48d2b63-primary.xml")
        val resultFile = file.packagesModify(IndexType.PRIMARY, true, false)
        println(System.currentTimeMillis() - start)
        println(resultFile.absolutePath)
    }

    @Test
    fun packagesModifyTest01() {
        val regex = "^<metadata xmlns=\"http://linux.duke.edu/metadata/common\" xmlns:rpm=\"http://linux.duke" +
            ".edu/metadata/rpm\" packages=\"(\\d+)\">$"
        val str = "<metadata xmlns=\"http://linux.duke.edu/metadata/common\" xmlns:rpm=\"http://linux.duke" +
            ".edu/metadata/rpm\" packages=\"\">"
        val matcher = Pattern.compile(regex).matcher(str)
        if (matcher.find()) {
            println(matcher.group(1).toInt())
        }
    }
}
