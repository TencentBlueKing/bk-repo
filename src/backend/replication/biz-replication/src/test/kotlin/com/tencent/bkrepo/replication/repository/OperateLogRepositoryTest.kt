package com.tencent.bkrepo.replication.repository

import com.tencent.bkrepo.replication.model.TOperateLog
import com.tencent.bkrepo.repository.pojo.log.OperateType
import com.tencent.bkrepo.repository.pojo.log.ResourceType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import java.time.LocalDateTime

@DataMongoTest
class OperateLogRepositoryTest {

    @Autowired
    private lateinit var opLogRepository: OperateLogRepository

    @BeforeEach
    private fun beforeEach() {
        opLogRepository.deleteAll()
    }

    @Test
    fun testDelete() {
        Assertions.assertEquals(0, opLogRepository.findAll().size)
    }

    @Test
    fun testInsert() {
        Assertions.assertEquals(0,opLogRepository.findAll().size)
        opLogRepository.insert(createLog("test", "test", "/test/index.txt"))
        opLogRepository.insert(createLog("test", "test", "/test/index2.txt"))
        opLogRepository.insert(createLog("test", "test", "/test/index3.txt"))
        Assertions.assertEquals(3, opLogRepository.findAll().size)
    }

    private fun createLog(projectId: String, repoName: String, fullPath: String): TOperateLog {
        val description = mapOf("projectId" to projectId, "repoName" to repoName, "request" to "")
        return TOperateLog(
            createdDate = LocalDateTime.now(),
            resourceType = ResourceType.NODE,
            resourceKey = "/$projectId/$repoName/$fullPath",
            operateType = OperateType.CREATE,
            userId = "admin",
            clientAddress = "127.0.0.1",
            description = description
        )
    }

}