package com.tencent.bkrepo.demo

import com.tencent.bkrepo.common.storage.innercos.InnerCosFileStorage
import com.tencent.bkrepo.common.storage.util.FileDigestUtils
import com.tencent.cos.transfer.TransferManager
import java.io.File
import java.util.concurrent.Executors
import org.apache.commons.io.IOUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ClassPathResource

/**
 *
 * @author: carrypan
 * @date: 2019-09-17
 */
@DisplayName("测试InnerCosFileStorage")
@SpringBootTest
class InnerCosFileStorageTest @Autowired constructor(val innerCosFileStorage: InnerCosFileStorage) {

    private val testFileName = "text.txt"

    @Test
    @DisplayName("文件操作集成测试")
    fun integrationTest() {
        // 准备文件
        val testFile = ClassPathResource(testFileName).file
        val originalContent = IOUtils.toString(testFile.inputStream())
        val fileSha256 = FileDigestUtils.fileSha256(listOf(testFile.inputStream()))
        println("content: $originalContent, sha256: $fileSha256")
        // 上传
        innerCosFileStorage.store(fileSha256, testFile.inputStream())
        // 判断是否存在
        assertTrue(innerCosFileStorage.exist(fileSha256))

        // 下载 对比
        val cosContent = IOUtils.toString(innerCosFileStorage.load(fileSha256))
        println("content: $cosContent")
        assertEquals(originalContent, cosContent)

        // 删除
        innerCosFileStorage.delete(fileSha256)

        // 判断是否存在
        assertFalse(innerCosFileStorage.exist(fileSha256))
    }

    @Test
    @DisplayName("文件上传测试")
    fun storeFileTest() {

        // 准备文件
        val testFile = File("/Users/carrypan/Downloads/Visual_Paradigm_CE_16_0_20190861_OSX_WithJRE.dmg")
        // val testFile = File("/Users/carrypan/Downloads/Typora.dmg")
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
            innerCosFileStorage.store(fileSha256, testFile.inputStream())
            val uploadConsume = (System.currentTimeMillis() - start) / 1000F
            println("上传耗时: ${uploadConsume}秒")

            println("上传平均速度: ${fileSize / uploadConsume} MB/S")
        }
    }

    @Test
    @DisplayName("分片上传测试")
    fun multipartUploadTest() {
        val testFile = File("/Users/carrypan/Downloads/Visual_Paradigm_CE_16_0_20190861_OSX_WithJRE.dmg")
        // val testFile = File("/Users/carrypan/Downloads/Typora.dmg")
        val fileSha256 = FileDigestUtils.fileSha256(listOf(testFile.inputStream()))
        val fileSize = testFile.length() / 1024F / 1024F
        println("文件大小: $fileSize MB")

        val start = System.currentTimeMillis()
        val client = innerCosFileStorage.createClient(innerCosFileStorage.defaultCredentials)
        val transferManager = TransferManager(client.cosClient, Executors.newFixedThreadPool(50))
        // transfermanger upload是异步上传
        val upload = transferManager.upload(client.bucketName, fileSha256, testFile)
        // 等待传输结束
        upload.waitForCompletion()
        transferManager.shutdownNow()
        val uploadConsume = (System.currentTimeMillis() - start) / 1000F
        println("上传耗时: ${uploadConsume}秒")

        println("上传平均速度: ${fileSize / uploadConsume} MB/S")
    }

    @Test
    @DisplayName("简单上传测试")
    fun simpleUploadTest() {
        val testFile = File("/Users/carrypan/Downloads/Typora.dmg")
        val fileSha256 = FileDigestUtils.fileSha256(listOf(testFile.inputStream()))
        val fileSize = testFile.length() / 1024F / 1024F
        println("文件大小: $fileSize MB")

        val start = System.currentTimeMillis()
        val client = innerCosFileStorage.createClient(innerCosFileStorage.defaultCredentials)
        client.cosClient.putObject(client.bucketName, fileSha256, testFile)
        val uploadConsume = (System.currentTimeMillis() - start) / 1000F
        println("上传耗时: ${uploadConsume}秒")

        println("上传平均速度: ${fileSize / uploadConsume} MB/S")
    }
}
