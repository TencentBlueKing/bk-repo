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

package com.tencent.bkrepo.cargo.service.impl

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.exception.RepoNotFoundException
import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.lock.service.LockOperation
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class CommonService {

    @Autowired
    lateinit var lockOperation: LockOperation

    @Autowired
    lateinit var storageManager: StorageManager

    @Autowired
    lateinit var repositoryService: RepositoryService

    @Autowired
    lateinit var nodeService: NodeService

    /**
     * 针对自旋达到次数后，还没有获取到锁的情况默认也会执行所传入的方法,确保业务流程不中断
     */
    fun <T> lockAction(projectId: String, repoName: String, path: String, action: () -> T): T {
        val lockKey = buildRedisKey(projectId, repoName, path)
        val lock = lockOperation.getLock(lockKey)
        return if (lockOperation.getSpinLock(lockKey, lock)) {
            logger.info("Lock for key cargo $lockKey has been acquired.")
            try {
                action()
            } finally {
                lockOperation.close(lockKey, lock)
                logger.info("Lock for cargo key $lockKey has been released.")
            }
        } else {
            action()
        }
    }

    fun getIndexOfCrate(projectId: String, repoName: String, fullPath: String): ArtifactInputStream? {
        val nodeDetail = nodeService.getNodeDetail(ArtifactInfo(projectId, repoName, fullPath))
        val storageCredentials = getStorageCredentials(projectId, repoName)
        return storageManager.loadArtifactInputStream(nodeDetail, storageCredentials)
    }

    fun getStorageCredentials(projectId: String, repoName: String): StorageCredentials? {
        val repository = repositoryService.getRepoDetail(projectId, repoName, RepositoryType.CARGO.name)
            ?: throw RepoNotFoundException("Repository[$repoName] does not exist")
        return repository.storageCredentials
    }

    /**
     * upload index of crate
     */
    fun uploadIndexOfCrate(artifactFile: ArtifactFile, nodeCreateRequest: NodeCreateRequest) {
        val repository = repositoryService.getRepoDetail(
            nodeCreateRequest.projectId,
            nodeCreateRequest.repoName,
            RepositoryType.CARGO.name
        )
            ?: throw RepoNotFoundException("Repository[${nodeCreateRequest.repoName}] does not exist")
        storageManager.storeArtifactFile(nodeCreateRequest, artifactFile, repository.storageCredentials)
    }

    private fun buildRedisKey(projectId: String, repoName: String, revPath: String): String {
        return "$REDIS_LOCK_KEY_PREFIX$projectId/$repoName/$revPath"
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CommonService::class.java)
        const val REDIS_LOCK_KEY_PREFIX = "cargo:lock:index:"

    }
}