package com.tencent.bkrepo.demo

import com.tencent.bkrepo.common.storage.innercos.InnerCosFileStorage
import com.tencent.bkrepo.common.storage.innercos.InnerCosProperties
import com.tencent.bkrepo.common.storage.strategy.LocateStrategy
import com.tencent.bkrepo.common.storage.util.FileDigestUtils
import org.apache.commons.io.IOUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ClassPathResource
import java.io.File

/**
 *
 * @author: carrypan
 * @date: 2019-09-17
 */
@DisplayName("测试InnerCosFileStorage")
@SpringBootTest
class InnerCosFileStorageTest{

    private val testFileName = "text.txt"

    @Autowired
    private lateinit var properties: InnerCosProperties

    @Autowired
    private lateinit var locateStrategy: LocateStrategy

    private var fileStorage: InnerCosFileStorage? = null

    @BeforeEach
    fun beforeEach() {
        properties.localCache.path = "/Users/carrypan/Desktop/test"
        fileStorage = InnerCosFileStorage(locateStrategy, properties)
    }

    @Test
    fun uploadTest() {
        //
    }

    @Test
    @DisplayName("文件操作集成测试")
    fun integrationTest() {
        // 准备文件
        val testFile = ClassPathResource(testFileName).file
        val originalContent = IOUtils.toString(testFile.inputStream())
        val fileSha256 = FileDigestUtils.fileSha256(listOf(testFile.inputStream()))
        println("content: $originalContent, sha256: $fileSha256")
        // 上传
        fileStorage!!.store(fileSha256, testFile.inputStream())
        // 判断是否存在
        assertTrue(fileStorage!!.exist(fileSha256))

        // 下载 对比
        val cosContent = IOUtils.toString(fileStorage!!.load(fileSha256))
        println("content: $cosContent")
        assertEquals(originalContent, cosContent)

        // 删除
        fileStorage!!.delete(fileSha256)

        // 判断是否存在
        assertFalse(fileStorage!!.exist(fileSha256))
    }

    @Test
    @DisplayName("文件上传测试")
    fun storeFileTest() {

        // 准备文件
        //val testFile = File("/Users/carrypan/Downloads/Visual_Paradigm_CE_16_0_20190861_OSX_WithJRE.dmg")
         val testFile = File("/Users/carrypan/Downloads/Typora.dmg")
        // val testFile = File("/Users/carrypan/Desktop/test.txt")

        if (testFile.exists()) {
            val fileSize = testFile.length() / 1024F / 1024F
            println("文件大小: $fileSize MB")

            // 计算sha256
            var start = System.currentTimeMillis()
            val fileSha256 = FileDigestUtils.fileSha256(listOf(testFile.inputStream()))
            println("文件SHA256: $fileSha256, 计算耗时: ${(System.currentTimeMillis() - start) / 1000F}秒")

            // 上传
            start = System.currentTimeMillis()
            fileStorage!!.store(fileSha256, testFile.inputStream())
            val uploadConsume = (System.currentTimeMillis() - start) / 1000F
            println("上传耗时: ${uploadConsume}秒")

            println("上传平均速度: ${fileSize / uploadConsume} MB/S")
        }
    }

}
