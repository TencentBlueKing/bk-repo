package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.repository.pojo.download.count.service.DownloadCountCreateRequest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

@SpringBootTest
internal class DownloadCountServiceTest @Autowired constructor(
    private val downloadCountService: ArtifactDownloadCountService
) {
    @Test
    @DisplayName("创建下载量相关测试")
    fun createTest() {
        (1 until 10).forEach {
            val request =
                DownloadCountCreateRequest("test", "npm-local", "helloworld-npm-publish", "1.0.2")
            println(request)
            downloadCountService.create(request)
        }
    }

    @Test
    @DisplayName("查询下载量相关测试")
    fun queryTest() {
        val result = downloadCountService.query("test","npm-local","helloworld-npm-publish",null)
        System.err.println(result)
    }

    @Test
    @DisplayName("查询下载量相关测试")
    fun findTest() {
        val result = downloadCountService.find("test","npm-local","helloworld-npm-publish",null,LocalDate.now().minusDays(1),
            LocalDate.now().plusDays(5))
        System.err.println(result)
    }
}