package com.tencent.bkrepo.migrate.job

import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.migrate.BKREPO
import com.tencent.bkrepo.migrate.SYNCREPO
import com.tencent.bkrepo.migrate.REQUESTJSON
import com.tencent.bkrepo.migrate.PENDING
import com.tencent.bkrepo.migrate.FINISH
import com.tencent.bkrepo.migrate.MIGRATE_OPERATOR
import com.tencent.bkrepo.migrate.pojo.MavenSyncInfo
import com.tencent.bkrepo.migrate.pojo.SyncRequest
import com.tencent.bkrepo.migrate.pojo.suyan.SuyanSyncRequest
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.repository.pojo.node.service.NodeMoveCopyRequest
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.util.StopWatch

@Service
class RequestSyncJob(
    private val jobService: JobService,
    private val nodeClient: NodeClient,
    private val storageService: StorageService
) {

    @Scheduled(fixedDelay = 30 * 1000)
    @SchedulerLock(name = "RequestSyncJob", lockAtMostFor = "PT60M")
    fun syncRequest() {
        // 查询待处理请求
        val nodeListOption = NodeListOption(1, 10, false, includeMetadata = true)
        val pendingRequests = nodeClient.listNodePage(BKREPO, SYNCREPO, "$REQUESTJSON$PENDING", nodeListOption)
            .data?.records
        if (pendingRequests == null || pendingRequests.isEmpty()) {
            logger.info("Not found pending json")
            return
        }
        logger.info("Found pending json: sum = ${pendingRequests.size}")
        for (pendingJsonNode in pendingRequests) {
            // 读取json文件内容
            val bkSyncRequest = readJsonNode(pendingJsonNode) ?: return
            logger.info("Found json: ${pendingJsonNode.fullPath}")
            val stopWatch = StopWatch("sync mvn task:${pendingJsonNode.fullPath}")
            stopWatch.start()
            try {
                jobService.syncArtifact(transfer(bkSyncRequest))
                moveFinishNode(pendingJsonNode)
            } catch (e: Exception) {
                logger.error("Sync json failed: ${pendingJsonNode.fullPath}")
                logger.error("", e)
            }
            stopWatch.stop()
            logger.info("Success move: ${pendingJsonNode.fullPath}, $stopWatch")
        }
    }

    private fun moveFinishNode(nodeInfo: NodeInfo) {
        val nodeMoveRequest = NodeMoveCopyRequest(
            srcProjectId = nodeInfo.projectId,
            srcRepoName = nodeInfo.repoName,
            srcFullPath = nodeInfo.fullPath,
            destProjectId = nodeInfo.projectId,
            destRepoName = nodeInfo.repoName,
            destFullPath = nodeInfo.fullPath
                .replace("$REQUESTJSON$PENDING", "$REQUESTJSON$FINISH"),
            overwrite = true,
            operator = MIGRATE_OPERATOR
        )
        nodeClient.moveNode(nodeMoveRequest)
    }

    private fun readJsonNode(nodeInfo: NodeInfo): SuyanSyncRequest? {
        return storageService.load(nodeInfo.sha256!!, Range.full(nodeInfo.size), null)?.use {
            it.readJsonString<SuyanSyncRequest>()
        }
    }

    private fun transfer(suyanSyncRequest: SuyanSyncRequest): SyncRequest {
        val mavenSyncInfo = MavenSyncInfo(
            repositoryName = suyanSyncRequest.repositoryName,
            groupId = suyanSyncRequest.groupId,
            artifactId = suyanSyncRequest.artifactId,
            version = suyanSyncRequest.version,
            packaging = suyanSyncRequest.packaging,
            name = suyanSyncRequest.name,
            artifactList = suyanSyncRequest.artifactList
        )
        return SyncRequest(
            maven = mavenSyncInfo,
            docker = suyanSyncRequest.docker?.map { it.transfer() },
            productList = suyanSyncRequest.productList
        )
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(RequestSyncJob::class.java)
    }
}
