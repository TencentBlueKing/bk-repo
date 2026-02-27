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

package com.tencent.bkrepo.common.artifact.manager.resource

import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.metadata.service.blocknode.BlockNodeService
import com.tencent.bkrepo.common.metadata.service.repo.StorageCredentialService
import com.tencent.bkrepo.common.service.cluster.ClusterInfo
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.pojo.RegionResource
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import org.slf4j.LoggerFactory

/**
 * 文件节点
 * */
class FsNodeResource(
    private val node: NodeInfo,
    private val blockNodeService: BlockNodeService,
    private val range: Range,
    private val storageService: StorageService,
    private val storageCredentials: StorageCredentials?,
    private val storageCredentialService: StorageCredentialService,
    private val isFederating: Boolean = false,
    private val clusterInfo: ClusterInfo? = null
) : AbstractNodeResource() {
    override fun getArtifactInputStream(): ArtifactInputStream? {
        with(node) {
            val blocks = blockNodeService.info(nodeDetail = NodeDetail(this), range = range)
            /*
             * 顺序查找
             * 1.当前仓库存储实例 (正常情况)
             * 2.拷贝存储实例（节点快速拷贝场景）
             * */
            val copyFromCredentialsKey = node.copyFromCredentialsKey
            return storageService.load(blocks, range) { regionResource ->
                var input = storageService.loadResource(regionResource, storageCredentials)
                    ?: loadFromCopyIfNecessary(regionResource, copyFromCredentialsKey)
                if (isFederating && clusterInfo != null) {
                    val blockRange = Range(
                        regionResource.off, regionResource.off + regionResource.len - 1, regionResource.size
                    )
                    val remoteNodeResource = RemoteNodeResource(
                        regionResource.digest, blockRange, storageCredentials, clusterInfo, storageService
                    )
                    input = remoteNodeResource.getArtifactInputStream()
                }
                check(input != null) { "Block[${regionResource.digest}] miss." }
                input
            }
        }
    }

    /**
     * 因为支持快速copy，也就是说源节点的数据可能还未完全上传成功，
     * 还在本地文件系统上，这时拷贝节点就会从源存储去加载数据。
     * */
    private fun loadFromCopyIfNecessary(
        resource: RegionResource,
        copyFromCredentialsKey: String?,
    ): ArtifactInputStream? {
        copyFromCredentialsKey?.let {
            if (logger.isDebugEnabled) {
                logger.debug(
                    "load region [${node.projectId}/${node.repoName}/${node.fullPath}] from copy credentialsKey[$it]"
                )
            }
            return storageService.loadResource(resource, storageCredentialService.findByKey(it))
        }
        return null
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FsNodeResource::class.java)
    }
}
