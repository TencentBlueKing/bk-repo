package com.tencent.bkrepo.proxy.artifact

import com.tencent.bkrepo.archive.api.ArchiveClient
import com.tencent.bkrepo.common.artifact.manager.NodeResourceFactory
import com.tencent.bkrepo.common.artifact.manager.resource.LocalNodeResource
import com.tencent.bkrepo.common.artifact.manager.resource.NodeResource
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.metadata.service.repo.StorageCredentialService
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

@Component
@Primary
class ProxyNodeResourceFactory(
    private val storageService: StorageService,
    private val storageCredentialService: StorageCredentialService,
    private val archiveClient: ArchiveClient,
): NodeResourceFactory {
    override fun getNodeResource(
        nodeInfo: NodeInfo,
        range: Range,
        storageCredentials: StorageCredentials?
    ): NodeResource {
        return LocalNodeResource(
            nodeInfo,
            range,
            storageCredentials,
            storageService,
            storageCredentialService,
            archiveClient,
        )
    }
}