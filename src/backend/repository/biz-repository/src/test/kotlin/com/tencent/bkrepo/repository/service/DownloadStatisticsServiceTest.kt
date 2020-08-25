package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.repository.pojo.download.service.DownloadStatisticsAddRequest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.boot.test.mock.mockito.MockBean
import java.time.LocalDate
import java.util.concurrent.CyclicBarrier
import kotlin.concurrent.thread

@DisplayName("下载统计服务测试")
@DataMongoTest
class DownloadStatisticsServiceTest @Autowired constructor(
    private val downloadStatisticsService: DownloadStatisticsService
) : ServiceBaseTest() {

    @MockBean
    private lateinit var repositoryService: RepositoryService

    @Test
    @DisplayName("创建下载量相关测试")
    fun createTest() {
        val count = 100
        val cyclicBarrier = CyclicBarrier(count)
        val threadList = mutableListOf<Thread>()
        val request = DownloadStatisticsAddRequest(
            "test",
            "npm-local",
            "helloworld-npm-publish",
            null
        )
        repeat(count) {
            val thread = thread {
                cyclicBarrier.await()
                downloadStatisticsService.add(request)
            }
            threadList.add(thread)
        }
        threadList.forEach { it.join() }
        val result = downloadStatisticsService.query(
            projectId = "test",
            repoName = "npm-local",
            artifact = "helloworld-npm-publish",
            version = null,
            startDate = LocalDate.now(),
            endDate = LocalDate.now()
        )
        Assertions.assertEquals(count, result.count)
    }

    @Test
    @DisplayName("查询下载量相关测试")
    fun testQueryForSpecial() {
        downloadStatisticsService.queryForSpecial("test", "npm-local", "helloworld-npm-publish", null)
    }

    @Test
    @DisplayName("查询下载量相关测试")
    fun testQuery() {
        downloadStatisticsService.query(
            "test", "npm-local", "helloworld-npm-publish", null, LocalDate.now().minusDays(1),
            LocalDate.now().plusDays(5)
        )
    }
}
