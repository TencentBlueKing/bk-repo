package com.tencent.bkrepo.demo

import com.qcloud.s1.cos.utils.IOUtils.toString
import com.tencent.bkrepo.common.storage.innercos.InnerCosFileStorage
import com.tencent.bkrepo.common.storage.util.FileDigestUtils
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun storeFileTest() {
        // 准备文件
        val testFile = ClassPathResource(testFileName).file
        val originalContent = toString(testFile.inputStream())
        val fileSha256 = FileDigestUtils.fileSha256(listOf(testFile.inputStream()))
        println("content: $originalContent, sha256: $fileSha256")
        // 上传
        innerCosFileStorage.store(fileSha256, testFile.inputStream())
        // 判断是否存在
        assertTrue(innerCosFileStorage.exist(fileSha256))

        // 下载 对比
        val cosContent = toString(innerCosFileStorage.load(fileSha256))
        println("content: $cosContent")
        assertEquals(originalContent, cosContent)

        // 删除
        innerCosFileStorage.delete(fileSha256)

        // 判断是否存在
        assertFalse(innerCosFileStorage.exist(fileSha256))

    }

}
