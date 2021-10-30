package com.tencent.bkrepo.executor.controller

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.executor.config.ExecutorConfig
import com.tencent.bkrepo.executor.pojo.context.FileScanContext
import com.tencent.bkrepo.executor.service.Task
import com.tencent.bkrepo.executor.util.TaskIdUtil
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.ProjectClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
class FullTask @Autowired constructor(
    private val nodeClient: NodeClient,
    private val projectClient: ProjectClient,
    private val repositoryClient: RepositoryClient
) {

    @Autowired
    private lateinit var scanTask: Task

    @Autowired
    private lateinit var config: ExecutorConfig

    @EventListener(ApplicationReadyEvent::class)
    fun runAll() {
        if (!config.full) {
            logger.info("skip run full task")
            return
        }
        val taskId = TaskIdUtil.build()
        getAllNode(taskId).forEach {
            val context = FileScanContext(
                taskId = it.taskId,
                config = config,
                projectId = it.projectId,
                repoName = it.repoName,
                fullPath = it.fullPath
            )
            scanTask.runFile(context)
        }
    }

    private fun getAllNode(taskId: String): List<FileScanContext> {
        val projectList = projectClient.listProject().data ?: run {
            return emptyList()
        }
        val contextList = mutableListOf<FileScanContext>()
        projectList.forEach { it ->
            val repoList = repositoryClient.listRepo(projectId = it.name, type = RepositoryType.GENERIC.toString())
                .data ?: return@forEach
            repoList.forEach { repo ->
                // list file
                val option = NodeListOption(pageNumber = 0, pageSize = 10, includeFolder = false)
                val nodes = nodeClient.listNodePage(repo.projectId, repo.name, "/", option).data ?: return@forEach
                nodes.records.forEach { node ->
                    val context = FileScanContext(
                        taskId = taskId,
                        config = config,
                        projectId = node.projectId,
                        repoName = node.name,
                        fullPath = node.fullPath
                    )
                    contextList.add(context)
                }
            }
        }
        return contextList
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FullTask::class.java)
    }
}
