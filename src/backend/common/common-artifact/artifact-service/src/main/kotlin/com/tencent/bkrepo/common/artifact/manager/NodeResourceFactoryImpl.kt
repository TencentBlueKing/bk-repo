/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.artifact.manager

import com.tencent.bkrepo.archive.api.ArchiveClient
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.ClusterArchitecture
import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.common.artifact.manager.resource.FsNodeResource
import com.tencent.bkrepo.common.artifact.manager.resource.LocalNodeResource
import com.tencent.bkrepo.common.artifact.manager.resource.NodeResource
import com.tencent.bkrepo.common.artifact.manager.resource.RemoteNodeResource
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.metadata.service.blocknode.BlockNodeService
import com.tencent.bkrepo.common.metadata.service.repo.StorageCredentialService
import com.tencent.bkrepo.common.service.cluster.ClusterInfo
import com.tencent.bkrepo.common.service.cluster.properties.ClusterProperties
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.fs.server.constant.FS_ATTR_KEY
import com.tencent.bkrepo.replication.api.ClusterNodeClient
import com.tencent.bkrepo.replication.constant.FEDERATED
import com.tencent.bkrepo.replication.exception.ReplicationMessageCode
import com.tencent.bkrepo.repository.pojo.node.NodeInfo

class NodeResourceFactoryImpl(
    private val clusterProperties: ClusterProperties,
    private val storageService: StorageService,
    private val storageCredentialService: StorageCredentialService,
    private val blockNodeService: BlockNodeService,
    private val clusterNodeClient: ClusterNodeClient,
    private val archiveClient: ArchiveClient,
) : NodeResourceFactory {

    private val centerClusterInfo = ClusterInfo(
        url = clusterProperties.center.url,
        certificate = clusterProperties.center.certificate.orEmpty(),
        appId = clusterProperties.center.appId,
        accessKey = clusterProperties.center.accessKey,
        secretKey = clusterProperties.center.secretKey,
    )

    override fun getNodeResource(
        nodeInfo: NodeInfo,
        range: Range,
        storageCredentials: StorageCredentials?,
    ): NodeResource {
        val digest = nodeInfo.sha256.orEmpty()
        if (isFsFile(nodeInfo)) {
            return FsNodeResource(nodeInfo, blockNodeService, range, storageService, storageCredentials)
        }
        if (isFederating(nodeInfo)) {
            val clusterInfo = getClusterInfo(nodeInfo.federatedSource!!)
                ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, nodeInfo.federatedSource!!)
            return RemoteNodeResource(digest, range, storageCredentials, clusterInfo, storageService)
        }
        if (clusterProperties.role == ClusterNodeType.EDGE &&
            clusterProperties.architecture != ClusterArchitecture.COMMIT_EDGE) {
            return RemoteNodeResource(digest, range, storageCredentials, centerClusterInfo, storageService)
        }
        val clusterName = getClusterName(nodeInfo)
        if (!inLocal(nodeInfo) && clusterName != null) {
            val clusterInfo = getClusterInfo(clusterName)
                ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, clusterName)
            return RemoteNodeResource(digest, range, storageCredentials, clusterInfo, storageService, false)
        }
        return LocalNodeResource(
            nodeInfo,
            range,
            storageCredentials,
            storageService,
            storageCredentialService,
            archiveClient,
        )
    }

    private fun isFederating(node: NodeInfo): Boolean {
        val federatedMetadata = node.nodeMetadata?.firstOrNull { it.key == FEDERATED }
        val federated = federatedMetadata?.value as? Boolean ?: true
        return !node.federatedSource.isNullOrEmpty() && !federated
    }

    private fun isFsFile(node: NodeInfo): Boolean {
        return node.metadata?.containsKey(FS_ATTR_KEY) == true
    }

    private fun getClusterName(node: NodeInfo): String? {
        return node.clusterNames?.firstOrNull { it != clusterProperties.self.name }
    }

    private fun inLocal(node: NodeInfo): Boolean {
        return node.clusterNames?.contains(clusterProperties.self.name) ?: false
    }

    private fun getClusterInfo(name: String): ClusterInfo? {
        return clusterNodeClient.getClusterInfo(name).data?.let {
            ClusterInfo(
                url = it.url,
                certificate = it.certificate.orEmpty(),
                appId = it.appId,
                accessKey = it.accessKey,
                secretKey = it.secretKey,
                username = it.username,
                password = it.password,
            )
        }
    }
}
