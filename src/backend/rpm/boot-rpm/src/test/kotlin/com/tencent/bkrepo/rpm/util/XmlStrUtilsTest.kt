package com.tencent.bkrepo.rpm.util

import com.tencent.bkrepo.rpm.pojo.IndexType
import com.tencent.bkrepo.rpm.util.GZipUtils.unGzipInputStream
import com.tencent.bkrepo.rpm.util.XmlStrUtils.findPackageIndex
import com.tencent.bkrepo.rpm.util.XmlStrUtils.indexOf
import com.tencent.bkrepo.rpm.util.XmlStrUtils.updatePackageCount
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File
import java.io.RandomAccessFile
import java.util.regex.Pattern

class XmlStrUtilsTest {
    /**
     * 按照仓库设置的repodata 深度分割请求参数
     */
    @Test
    fun resolveRepodataUriTest() {
        val uri = "/7/os/x86_64/hello-world-1-1.x86_64.rpm"
        val depth = 3
        val repodataUri = XmlStrUtils.resolveRepodataUri(uri, depth)
        Assertions.assertEquals("7/os/x86_64/", repodataUri.repodataPath)
        Assertions.assertEquals("hello-world-1-1.x86_64.rpm", repodataUri.artifactRelativePath)
    }

    @Test
    fun updatePackageCountTest() {
        val start = System.currentTimeMillis()
        val file = File("/Downloads/60M.xml")
        val randomAccessFile = RandomAccessFile(file, "rw")
        updatePackageCount(randomAccessFile, IndexType.PRIMARY, 0, true)
        println(System.currentTimeMillis() - start)
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

    @Test
    fun indexOfTest() {
        val file = File("/Users/weaving/Downloads/filelist/21e8c7280184d7428e4fa259c669fa4b2cfef05f-filelists.xml")
        val randomAccessFile = RandomAccessFile(file, "r")
        val index = indexOf(randomAccessFile, """<package pkgid="cb764f7906736425286341f6c5939347b01c5c17" name="httpd" arch="x86_64">""")
        Assertions.assertEquals(287, index)
    }

    @Test
    fun indexPackageTest() {
        val file = File("others.xml")
        val randomAccessFile = RandomAccessFile(file, "r")
        val prefixStr = "  <package pkgid="
        val locationStr = """name="trpc-go-helloword">
    <version epoch="0" ver="0.0.1" rel="1"/>"""
        val suffixStr = "</package>"
        val xmlIndex = findPackageIndex(randomAccessFile, prefixStr, locationStr, suffixStr)
        if (xmlIndex != null) {
            println(xmlIndex.prefixIndex)
            println(xmlIndex.locationIndex)
            println(xmlIndex.suffixIndex)
            println(xmlIndex.suffixEndIndex)
        }
    }

    @Test
    fun updateFileTest() {
        val file = File("others.xml")
        XmlStrUtils.updatePackageXml(RandomAccessFile(file, "rw"), 3, 1, "a")
    }

    @Test
    fun resolvePackageCountTest() {
        val file = File("${System.getenv("HOME")}/Downloads/63da8904a2791e4965dcda350b26ffa3d1eda27b-primary")
        val randomAccessFile = RandomAccessFile(file, "r")
        val count = XmlStrUtils.resolvePackageCount(randomAccessFile, IndexType.PRIMARY)
        print("count: $count")
    }

    @Test
    fun outFileTest() {
        val file = File("${System.getenv("HOME")}/Downloads/63da8904a2791e4965dcda350b26ffa3d1eda27b-primary.xml.gz")
        val unzipFile = file.inputStream().unGzipInputStream()
        val randomAccessFile = RandomAccessFile(unzipFile, "rw")
        randomAccessFile.seek(0)
        var line: String?
        randomAccessFile.use { raf ->
            while (raf.readLine().also { line = it } != null) {
                println(line)
            }
            println("")
        }
    }
}