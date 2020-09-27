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
class PackageDownloadStatisticsServiceTest @Autowired constructor(
    private val packageDownloadStatisticsService: PackageDownloadStatisticsService
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
            "npm://helloworld-npm-publish",
            "helloworld-npm-publish",
            "1.0.0"
        )
        repeat(count) {
            val thread = thread {
                cyclicBarrier.await()
                packageDownloadStatisticsService.add(request)
            }
            threadList.add(thread)
        }
        threadList.forEach { it.join() }
        val result = packageDownloadStatisticsService.query(
            projectId = "test",
            repoName = "npm-local",
            packageKey = "npm://helloworld-npm-publish",
            version = null,
            startDay = LocalDate.now(),
            endDay = LocalDate.now()
        )
        Assertions.assertEquals(count, result.count)
    }

    @Test
    @DisplayName("查询下载量相关测试")
    fun testQueryForSpecial() {
        packageDownloadStatisticsService.queryForSpecial("test", "npm-local", "npm://helloworld-npm-publish")
    }

    @Test
    @DisplayName("查询下载量相关测试")
    fun testQuery() {
        packageDownloadStatisticsService.query(
            "test", "npm-local", "npm://helloworld-npm-publish", null, LocalDate.now().minusDays(1),
            LocalDate.now().plusDays(5)
        )
    }
}
