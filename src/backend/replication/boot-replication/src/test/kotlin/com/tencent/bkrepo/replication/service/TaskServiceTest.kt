package com.tencent.bkrepo.replication.service

import com.tencent.bkrepo.replication.pojo.request.ReplicationTaskCreateRequest
import com.tencent.bkrepo.replication.pojo.setting.RemoteClusterInfo
import com.tencent.bkrepo.replication.pojo.setting.ReplicationSetting
import com.tencent.bkrepo.replication.pojo.task.ReplicationType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@DisplayName("同步任务服务 测试")
@SpringBootTest
class TaskServiceTest {

    @Autowired
    private lateinit var taskService: TaskService

    @AfterEach
    fun tearDown() {
        taskService.list().forEach { taskService.delete(it.id) }
    }

    @Test
    @Disabled
    @DisplayName("相关任务查询")
    fun listRelativeTaskTest() {
        createTask()
        createTask()
        createTask(false, "p1")
        createTask(false, "p1")
        createTask(false, "p2")
        createTask(false, "p1", "r1")
        createTask(false, "p1", "r2")
        createTask(false, "p2", "r1")

        val list1 = taskService.listRelativeTask(
            type = ReplicationType.INCREMENTAL,
            localProjectId = "p1",
            localRepoName = "r1"
        )
        Assertions.assertEquals(5, list1.size)

        val list2 = taskService.listRelativeTask(
            type = ReplicationType.FULL,
            localProjectId = "p1",
            localRepoName = "r1"
        )
        Assertions.assertEquals(0, list2.size)
    }

    private fun createTask(
        includeAllProject: Boolean = true,
        localProjectId: String? = null,
        localRepoName: String? = null,
        remoteProjectId: String? = null,
        remoteRepoName: String? = null
    ) {
        val remoteClusterInfo = RemoteClusterInfo(url = "", username = "", password = "")
        val request = ReplicationTaskCreateRequest(
            type = ReplicationType.INCREMENTAL,
            includeAllProject = includeAllProject,
            localProjectId = localProjectId,
            localRepoName = localRepoName,
            remoteProjectId = remoteProjectId,
            remoteRepoName = remoteRepoName,
            setting = ReplicationSetting(remoteClusterInfo = remoteClusterInfo),
            validateConnectivity = false
        )
        taskService.create("system", request)
    }
}