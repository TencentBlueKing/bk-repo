package com.tencent.bkrepo.replication.replica.type.federation

import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.replication.service.FederationGroupService
import com.tencent.bkrepo.replication.service.FederationRepositoryService
import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/**
 * 监听仓库创建事件，对满足条件的联邦集群组自动开启联邦同步
 */
@Component
class FederationRepoEventConsumer(
    private val federationGroupService: FederationGroupService,
    private val federationRepositoryService: FederationRepositoryService,
) {

    fun accept(message: Message<ArtifactEvent>) {
        val event = message.payload
        if (event.type != EventType.REPO_CREATED) return
        handleRepoCreated(event)
    }

    private fun handleRepoCreated(event: ArtifactEvent) {
        val groups = federationGroupService.listAutoEnableGroups(event.projectId)
        if (groups.isEmpty()) return

        groups.forEach { group ->
            try {
                federationRepositoryService.autoEnableFederation(
                    projectId = event.projectId,
                    repoName = event.repoName,
                    federationGroupId = group.id!!,
                    currentClusterId = group.currentClusterId,
                    clusterIds = group.clusterIds
                )
                logger.info(
                    "Auto-enabled federation for repo [${event.projectId}|${event.repoName}] " +
                        "with group [${group.name}]"
                )
            } catch (e: Exception) {
                logger.warn(
                    "Failed to auto-enable federation for repo [${event.projectId}|${event.repoName}] " +
                        "with group [${group.name}]: ${e.message}"
                )
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FederationRepoEventConsumer::class.java)
    }
}
