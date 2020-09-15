package com.tencent.bkrepo.rpm.util

import com.tencent.bkrepo.rpm.util.FileInputStreamUtils.indexPackage
import com.tencent.bkrepo.rpm.util.FileInputStreamUtils.rpmIndex
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

import org.springframework.boot.test.context.SpringBootTest
import java.io.File

@SpringBootTest
class FileInputStreamUtilsTest {

    @Test
    fun searchString() {
        val file = File("/Users/weaving/Downloads/filelist/21e8c7280184d7428e4fa259c669fa4b2cfef05f-filelists.xml")
        val index = file.rpmIndex(
            "<package pkgid=\"cb764f7906736425286341f6c5939347b01c5c17\" name=\"httpd\" " +
                "arch=\"x86_64\">"
        )
        Assertions.assertEquals(287, index)
    }

    @Test
    fun indexPackageTest() {
        val file = File("/Users/weaving/Downloads/c031e58d486851e84d51703b07197b1af0bedf78-others (1).xml")
        val prefixStr = "  <package pkgid="
        val locationStr = "name=\"bkrepo-test\">\n" +
            "    <version epoch=\"0\" ver=\"1\" rel=\"1\"/>"
        val suffixStr = "</package>"
        val xmlIndex = file.indexPackage(
            prefixStr,
            locationStr,
            suffixStr
        )
        println(xmlIndex.prefixIndex)
        println(xmlIndex.locationIndex)
        println(xmlIndex.suffixIndex)
    }
}
