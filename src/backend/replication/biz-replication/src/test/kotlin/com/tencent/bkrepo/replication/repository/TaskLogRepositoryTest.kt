package com.tencent.bkrepo.replication.repository

import com.tencent.bkrepo.replication.model.TReplicationTaskLog
import com.tencent.bkrepo.replication.pojo.task.ReplicationProgress
import com.tencent.bkrepo.replication.pojo.task.ReplicationStatus
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import java.time.LocalDateTime

@DataMongoTest
internal class TaskLogRepositoryTest {

    @Autowired
    private lateinit var taskLogRepository: TaskLogRepository

    @BeforeEach
    private fun beforeEach() {
        taskLogRepository.deleteAll()
    }

    @Test
    fun testDeleteByTaskKey() {
        Assertions.assertEquals(0, taskLogRepository.findAll().size)
        taskLogRepository.insert(createLog(taskKey = TEST_KEY))
        taskLogRepository.insert(createLog(taskKey = TEST_KEY))
        taskLogRepository.insert(createLog(taskKey = TEST_KEY))
        taskLogRepository.insert(createLog(taskKey = "another"))
        Assertions.assertEquals(4, taskLogRepository.findAll().size)
        taskLogRepository.deleteByTaskKey("non-exist")
        Assertions.assertEquals(4, taskLogRepository.findAll().size)
        taskLogRepository.deleteByTaskKey(TEST_KEY)
        Assertions.assertEquals(1, taskLogRepository.findAll().size)
        taskLogRepository.deleteByTaskKey("another")
        Assertions.assertEquals(0, taskLogRepository.findAll().size)
    }

    @Test
    fun testFindFirstByTaskKeyOrderByStartTimeDesc() {
        taskLogRepository.insert(createLog(startTime = LocalDateTime.now().plusDays(1)))
        taskLogRepository.insert(createLog(startTime = LocalDateTime.now().plusDays(2)))
        val log3 = taskLogRepository.insert(createLog(startTime = LocalDateTime.now().plusDays(3)))
        val log = taskLogRepository.findFirstByTaskKeyOrderByStartTimeDesc(TEST_KEY)
        val anotherLog = taskLogRepository.findFirstByTaskKeyOrderByStartTimeDesc("another")
        Assertions.assertNull(anotherLog)
        Assertions.assertEquals(log3.id!!, log!!.id)
    }

    @Test
    fun testFindByTaskKeyOrderByStartTimeDesc() {
        val log1 = taskLogRepository.insert(createLog(startTime = LocalDateTime.now().plusDays(1)))
        val log2 = taskLogRepository.insert(createLog(startTime = LocalDateTime.now().plusDays(2)))
        val log3 = taskLogRepository.insert(createLog(startTime = LocalDateTime.now().plusDays(3)))
        taskLogRepository.insert(createLog(taskKey = "another"))
        val logList = taskLogRepository.findByTaskKeyOrderByStartTimeDesc(TEST_KEY)
        val anotherLogList = taskLogRepository.findByTaskKeyOrderByStartTimeDesc("another")
        val nonExistLogList = taskLogRepository.findByTaskKeyOrderByStartTimeDesc("non-exist")
        Assertions.assertEquals(3, logList.size)
        Assertions.assertEquals(1, anotherLogList.size)
        Assertions.assertEquals(0, nonExistLogList.size)
        Assertions.assertEquals(log3.id!!, logList[0].id!!)
        Assertions.assertEquals(log2.id!!, logList[1].id!!)
        Assertions.assertEquals(log1.id!!, logList[2].id!!)
    }

    private fun createLog(
        taskKey: String = TEST_KEY,
        status: ReplicationStatus = ReplicationStatus.SUCCESS,
        startTime: LocalDateTime = LocalDateTime.now()
    ): TReplicationTaskLog {
        return TReplicationTaskLog(
            taskKey = taskKey,
            status = status,
            replicationProgress = ReplicationProgress(),
            startTime = startTime,
            endTime = LocalDateTime.now()
        )
    }

    companion object {
        private const val TEST_KEY = "testKey"
    }
}
