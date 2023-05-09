/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.slf4j.LoggerFactory

/**
 * 本地节点资源
 * */
class LocalNodeResource(
    private val node: NodeInfo,
    private val range: Range,
    private val storageCredentials: StorageCredentials?,
    private val storageService: StorageService,
    private val storageCredentialsClient: StorageCredentialsClient
) : AbstractNodeResource() {

    private val digest = node.sha256.orEmpty()
    override fun exists(): Boolean {
        return storageService.exist(digest, storageCredentials)
    }

    override fun getArtifactInputStream(): ArtifactInputStream? {
        /*
        * 顺序查找
        * 1.当前仓库存储实例 (正常情况)
        * 2.拷贝存储实例（节点快速拷贝场景）
        * 3.旧存储实例（仓库迁移场景）
        * */
        return storageService.load(digest, range, storageCredentials)
            ?: loadFromCopyIfNecessary(node, range)
            ?: loadFromRepoOldIfNecessary(node, range, storageCredentials)
    }

    /**
     * 因为支持快速copy，也就是说源节点的数据可能还未完全上传成功，
     * 还在本地文件系统上，这时拷贝节点就会从源存储去加载数据。
     * */
    private fun loadFromCopyIfNecessary(
        node: NodeInfo,
        range: Range
    ): ArtifactInputStream? {
        node.copyFromCredentialsKey?.let {
            val digest = node.sha256!!
            logger.info("load data [$digest] from copy credentialsKey [$it]")
            val fromCredentialsKey = storageCredentialsClient.findByKey(it).data
            return storageService.load(digest, range, fromCredentialsKey)
        }
        return null
    }

    /**
     * 仓库迁移场景
     * 仓库还在迁移中，旧的数据还未存储到新的存储实例上去，所以从仓库之前的存储实例中加载
     * */
    private fun loadFromRepoOldIfNecessary(
        node: NodeInfo,
        range: Range,
        storageCredentials: StorageCredentials?
    ): ArtifactInputStream? {
        val repositoryDetail = getRepoDetail(node)
        val oldCredentials = findStorageCredentialsByKey(repositoryDetail.oldCredentialsKey)
        if (storageCredentials != oldCredentials) {
            logger.info(
                "load data [${node.sha256!!}] from" +
                    " repo old credentialsKey [${repositoryDetail.oldCredentialsKey}]"
            )
            return storageService.load(node.sha256!!, range, oldCredentials)
        }
        return null
    }

    /**
     * 获取RepoDetail
     * */
    private fun getRepoDetail(node: NodeInfo): RepositoryDetail {
        with(node) {
            // 如果当前上下文存在该node的repo信息则，返回上下文中的repo，大部分请求应该命中这
            ArtifactContextHolder.getRepoDetail()?.let {
                if (it.projectId == projectId && it.name == name) {
                    return it
                }
            }
            // 如果是异步或者请求上下文找不到，则通过查询，并进行缓存
            val repositoryId = ArtifactContextHolder.RepositoryId(
                projectId = projectId,
                repoName = repoName
            )
            return ArtifactContextHolder.getRepoDetail(repositoryId)
        }
    }

    /**
     * 根据credentialsKey查找StorageCredentials
     * */
    private fun findStorageCredentialsByKey(credentialsKey: String?): StorageCredentials? {
        credentialsKey ?: return null
        return storageCredentialsClient.findByKey(credentialsKey).data
    }

    companion object {
        private val logger = LoggerFactory.getLogger(LocalNodeResource::class.java)
    }
}
