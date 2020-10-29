package com.tencent.bkrepo.rpm.artifact

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactTransferContext
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class StorageManager {

    @Autowired
    private lateinit var nodeClient: NodeClient

    @Autowired
    private lateinit var storageService: StorageService

    fun store(context: ArtifactTransferContext, node: NodeCreateRequest, artifactFile: ArtifactFile) {
        storageService.store(node.sha256!!, artifactFile, context.storageCredentials)
        artifactFile.delete()
        with(node) { logger.info("Success to store $projectId/$repoName/$fullPath") }
        nodeClient.create(node)
        logger.info("Success to insert $node")
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(StorageManager::class.java)
    }
}
