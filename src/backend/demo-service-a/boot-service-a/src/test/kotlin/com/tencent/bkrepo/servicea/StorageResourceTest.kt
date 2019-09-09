package com.tencent.bkrepo.servicea

import com.tencent.bkrepo.storage.api.StorageResource
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockMultipartFile

@DisplayName("测试文件上传/下载")
@SpringBootTest
class StorageResourceTest @Autowired constructor(
        val storageResource: StorageResource
) {

    @Test
    @DisplayName("上传测试")
    fun uploadTest() {

        val multipartFile = MockMultipartFile("file", "test.txt",
                null, "Spring Framework".toByteArray())
        val result = storageResource.store(multipartFile)
        assertTrue(result.isOk())
    }
}