/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.job.migrate

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.common.api.exception.BadRequestException
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.metadata.dao.node.NodeDao
import com.tencent.bkrepo.common.metadata.dao.repo.RepositoryDao
import com.tencent.bkrepo.common.metadata.model.TFileReference
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.metadata.service.file.FileReferenceService
import com.tencent.bkrepo.common.metadata.service.repo.StorageCredentialService
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.storage.config.StorageProperties
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.job.BATCH_SIZE
import com.tencent.bkrepo.job.batch.utils.NodeCommonUtils
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.migrate.dao.MigrateFailedNodeDao
import com.tencent.bkrepo.job.migrate.dao.MigrateRepoStorageTaskDao
import com.tencent.bkrepo.job.migrate.model.TMigrateFailedNode
import com.tencent.bkrepo.job.migrate.model.TMigrateRepoStorageTask
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState
import com.tencent.bkrepo.job.migrate.strategy.MigrateFailedNodeFixer
import com.tencent.bkrepo.job.service.MigrateArchivedFileService
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong

/**
 * 处理迁移失败node服务
 */
@Service
class MigrateFailedNodeService(
    private val migrateRepoStorageTaskDao: MigrateRepoStorageTaskDao,
    private val migrateFailedNodeDao: MigrateFailedNodeDao,
    private val migrateFailedNodeFixer: MigrateFailedNodeFixer,
    private val fileReferenceService: FileReferenceService,
    private val storageCredentialsService: StorageCredentialService,
    private val repositoryDao: RepositoryDao,
    private val nodeDao: NodeDao,
    private val storageService: StorageService,
    private val migrateArchivedFileService: MigrateArchivedFileService,
    private val storageProperties: StorageProperties,
) {
    /**
     * 无法处理时，或已经手动处理成功则可以移除迁移失败的node
     *
     * @param projectId 项目id
     * @param repoName 仓库名
     * @param fullPath 迁移失败的node完整路径
     */
    fun removeFailedNode(projectId: String, repoName: String, fullPath: String?) {
        val result = migrateFailedNodeDao.remove(projectId, repoName, fullPath)
        logger.info("remove [${result.deletedCount}] failed node of [$projectId/$repoName$fullPath]")
    }

    /**
     * 排查并修复异常后，重置迁移失败的node重试次数以便继续重试
     *
     * @param projectId 项目id
     * @param repoName 仓库名
     * @param fullPath 迁移失败的node完整路径
     */
    fun resetRetryCount(projectId: String, repoName: String, fullPath: String?) {
        val result = migrateFailedNodeDao.resetRetryCount(projectId, repoName, fullPath)
        logger.info("reset [${result.modifiedCount}] retry count of [$projectId/$repoName$fullPath]")
    }

    /**
     * 尝试自动修复所有失败node都已经重试并再次失败的项目
     */
    @Async
    fun autoFix() {
        var pageRequest = Pages.ofRequest(DEFAULT_PAGE_NUMBER, DEFAULT_PAGE_SIZE)
        var tasks: List<TMigrateRepoStorageTask>
        do {
            tasks = migrateRepoStorageTaskDao.find(MigrateRepoStorageTaskState.MIGRATING_FAILED_NODE.name, pageRequest)
            tasks.forEach {
                val existsFailedNode = migrateFailedNodeDao.existsFailedNode(it.projectId, it.repoName)
                val existsRetryableNode = migrateFailedNodeDao.existsRetryableNode(it.projectId, it.repoName)
                // 存在无法继续重试的node时尝试修复
                if (existsFailedNode && !existsRetryableNode) {
                    autoFix(it.projectId, it.repoName)
                }
            }
            pageRequest = pageRequest.withPage(pageRequest.pageNumber + 1)
        } while (tasks.isNotEmpty())
    }

    /**
     * 尝试自动修复迁移失败的node
     *
     * @param projectId 项目id
     * @param repoName 仓库名
     */
    @Async
    fun autoFix(projectId: String, repoName: String) {
        migrateFailedNodeFixer.fix(projectId, repoName)
    }

    /**
     * 整块存储迁移结束后可能存在源存储引用计数未正常减为0的情况，可调用该方法进行修复
     *
     * @param srcCredentialKey 源存储
     * @param dstCredentialKey 目标存储
     */
    @Async
    fun correctMigratedStorageFileReference(srcCredentialKey: String?, dstCredentialKey: String?) {
        checkCredential(srcCredentialKey, dstCredentialKey)

        val query = Query(Criteria.where(TFileReference::credentialsKey.name).isEqualTo(srcCredentialKey))
        val cleaned = AtomicLong(0L)
        NodeCommonUtils.forEachRefByCollectionParallel(query, BATCH_SIZE) { ref ->
            val sha256 = ref[TFileReference::sha256.name] as String
            val count = ref[TFileReference::count.name].toString().toLong()
            logger.info("start clean ref[$sha256], ref count[$count]")
            if (!fileReferenceService.exists(sha256, dstCredentialKey)) {
                // 创建目标存储引用并设置引用数为0
                // 如果还存在Node，在FileReferenceCleanupJob中会修复引用数为非0，如果没有对应的Node则引用会被清理
                fileReferenceService.increment(sha256, dstCredentialKey, 0L)
            }

            if (count > 0) {
                // 将源存储引用减为0后，FileReferenceCleanupJob中会清理该引用
                fileReferenceService.increment(sha256, srcCredentialKey, -count)
            }
            logger.info("finish clean ref[$sha256], cleaned[${cleaned.incrementAndGet()}]")
        }
    }

    /**
     * 更新node归档状态
     */
    fun updateNodeArchiveStatus(projectId: String, nodeId: String, archived: Boolean = true) {
        nodeDao.setNodeArchived(projectId, nodeId, archived)
        logger.info("set node $nodeId archived[$archived]")
    }

    /**
     * 修复缺失的失败node
     */
    @Async
    fun fixMissingFailedNode() {
        logger.info("start fix missing failed node")
        val taskCache = HashMap<String, TMigrateRepoStorageTask>()
        migrateFailedNodeDao.iterate(null, null, null) { failedNode ->
            val task = taskCache.getOrPut(failedNode.taskId) {
                migrateRepoStorageTaskDao.find(failedNode.projectId, failedNode.repoName)!!
            }
            nodeDao.findNodeIncludeDeleted(failedNode.projectId, failedNode.repoName, failedNode.fullPath)
                .distinctBy { it.sha256 }
                .filter { it.sha256 != null && it.sha256 != failedNode.sha256 }
                .forEach { node ->
                    try {
                        tryCreateFailedNodeFor(task, node)
                    } catch (e: Exception) {
                        logger.error("create failed node for [${node}] failed", e)
                    }
                }
        }
        logger.info("fix missing failed node finished")
    }

    private fun tryCreateFailedNodeFor(task: TMigrateRepoStorageTask, node: TNode) {
        with(node) {
            val fileExist = storageService.exist(sha256!!, getStorageCredentials(task.dstStorageKey))
            val archivedFileExist = node.archived == true &&
                    migrateArchivedFileService.archivedFileCompleted(task.dstStorageKey, sha256!!)
            if (!fileExist && !archivedFileExist) {
                val now = LocalDateTime.now()
                migrateFailedNodeDao.insert(
                    TMigrateFailedNode(
                        id = null,
                        createdDate = now,
                        lastModifiedDate = now,
                        nodeId = node.id!!,
                        taskId = task.id!!,
                        projectId = projectId,
                        repoName = repoName,
                        fullPath = fullPath,
                        sha256 = sha256!!,
                        md5 = md5!!,
                        size = size,
                        retryTimes = 0,
                    )
                )
                logger.info("create failed node[$projectId/$repoName/$fullPath][$sha256] success")
            } else {
                logger.info("failed node[$projectId/$repoName/$fullPath][$sha256] already exists]")
            }
        }
    }

    private fun getStorageCredentials(key: String?): StorageCredentials {
        return if (key == null) {
            storageProperties.defaultStorageCredentials()
        } else {
            RepositoryCommonUtils.getStorageCredentials(key)!!
        }
    }

    private fun checkCredential(srcCredentialKey: String?, dstCredentialKey: String?) {
        if (storageCredentialsService.findByKey(srcCredentialKey) == null ||
            storageCredentialsService.findByKey(dstCredentialKey) == null
        ) {
            throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, srcCredentialKey!!, dstCredentialKey!!)
        }

        // 限制源存储未使用才允许清理引用
        if (repositoryDao.existsByCredentialsKey(srcCredentialKey)) {
            throw BadRequestException(CommonMessageCode.PARAMETER_INVALID, "storage [$srcCredentialKey] in use")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MigrateFailedNodeService::class.java)
    }
}
