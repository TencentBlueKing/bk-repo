/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.job.separation.service.impl

import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.fs.server.constant.FAKE_SHA256
import com.tencent.bkrepo.job.FILE_REFERENCE_COLLECTION_NAME
import com.tencent.bkrepo.job.separation.dao.SeparationFailedRecordDao
import com.tencent.bkrepo.job.separation.dao.SeparationTaskDao
import com.tencent.bkrepo.job.separation.pojo.NodeFilterInfo
import com.tencent.bkrepo.job.separation.pojo.PackageFilterInfo
import com.tencent.bkrepo.job.separation.pojo.query.FileReferenceInfo
import com.tencent.bkrepo.job.separation.pojo.query.NodeDetailInfo
import com.tencent.bkrepo.job.separation.pojo.record.SeparationContext
import com.tencent.bkrepo.job.separation.pojo.record.SeparationProgress
import com.tencent.bkrepo.job.separation.pojo.task.SeparationCount
import com.tencent.bkrepo.job.separation.pojo.task.SeparationTaskState
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import java.time.LocalDateTime

open class AbstractHandler(
    private val mongoTemplate: MongoTemplate,
    private val separationFailedRecordDao: SeparationFailedRecordDao,
    private val separationTaskDao: SeparationTaskDao,
) {

    fun validatePackageParams(pkg: PackageFilterInfo?) {
        if (pkg != null && pkg.packageKey.isNullOrEmpty() && pkg.packageKeyRegex.isNullOrEmpty()) {
            throw NotFoundException(
                CommonMessageCode.PARAMETER_MISSING,
                "packageName: ${pkg.packageKey}|${pkg.packageKeyRegex}"
            )
        }
    }

    fun validateNodeParams(node: NodeFilterInfo?) {
        if (node != null && node.path.isNullOrEmpty() && node.pathRegex.isNullOrEmpty()) {
            throw NotFoundException(
                CommonMessageCode.PARAMETER_MISSING,
                "nodePath: ${node.path}|${node.pathRegex}"
            )
        }
    }

    fun sha256Check(folder: Boolean, sha256: String?): Boolean {
        // 增加对应cold node 的文件引用
        if (folder || sha256.isNullOrBlank() || sha256 == FAKE_SHA256) {
            return false
        }
        return true
    }

    fun removeNodeFromSource(
        context: SeparationContext,
        nodeCollectionName: String,
        idSha256Map: MutableMap<String, String>
    ) {
        with(context) {
            idSha256Map.forEach {
                val nodeQuery = Query(Criteria.where(ID).isEqualTo(it.key))
                // 逻辑删除， 同时删除索引
                val update = Update()
                    .set(NodeDetailInfo::lastModifiedBy.name, SYSTEM_USER)
                    .set(NodeDetailInfo::deleted.name, LocalDateTime.now())
                val updateResult = mongoTemplate.updateFirst(nodeQuery, update, nodeCollectionName)
                if (updateResult.modifiedCount != 1L) {
                    logger.error(
                        "delete hot node failed with id ${it.key} " +
                            "and fullPath ${it.value} in $projectId|$repoName"
                    )
                } else {
                    logger.info(
                        "delete hot node success with id ${it.key} " +
                            "and fullPath ${it.value} in $projectId|$repoName"
                    )
                }
            }
        }
    }

    fun increment(sha256: String, credentialsKey: String?, inc: Long) {
        val criteria = Criteria.where(FileReferenceInfo::sha256.name).`is`(sha256)
            .and(FileReferenceInfo::credentialsKey.name).`is`(credentialsKey)
        val query = Query(criteria)
        val update = Update().inc(FileReferenceInfo::count.name, inc)
        try {
            mongoTemplate.upsert(query, update, FILE_REFERENCE_COLLECTION_NAME)
        } catch (exception: DuplicateKeyException) {
            // retry because upsert operation is not atomic
            mongoTemplate.upsert(query, update, FILE_REFERENCE_COLLECTION_NAME)
        }
        logger.info("Increment hot node reference [$inc] of file [$sha256] on credentialsKey [$credentialsKey].")
    }

    fun setSkippedProgress(
        taskId: String,
        separationProgress: SeparationProgress,
        packageId: String? = null, versionId: String? = null,
        nodeId: String? = null
    ) {
        separationProgress.skipped++
        separationProgress.packageId = packageId
        separationProgress.versionId = versionId
        separationProgress.nodeId = nodeId
        updateStatus(taskId, separationProgress)
    }

    fun setSuccessProgress(
        taskId: String,
        separationProgress: SeparationProgress,
        packageId: String? = null, versionId: String? = null,
        nodeId: String? = null
    ) {
        separationProgress.success++
        separationProgress.packageId = packageId
        separationProgress.versionId = versionId
        separationProgress.nodeId = nodeId
        updateStatus(taskId, separationProgress)
    }

    fun setFailedProgress(
        taskId: String,
        separationProgress: SeparationProgress,
        packageId: String? = null, versionId: String? = null,
        nodeId: String? = null
    ) {
        separationProgress.failed++
        separationProgress.packageId = packageId
        separationProgress.versionId = versionId
        separationProgress.nodeId = nodeId
        updateStatus(taskId, separationProgress)
    }

    private fun updateStatus(
        taskId: String,
        separationProgress: SeparationProgress,
    ) {
        val count = SeparationCount(
            separationProgress.success, separationProgress.failed, separationProgress.skipped
        )
        separationTaskDao.updateState(
            taskId,
            SeparationTaskState.RUNNING,
            count,
        )
    }

    fun saveFailedRecord(
        context: SeparationContext,
        packageId: String? = null,
        versionId: String? = null,
        nodeId: String? = null,
    ) {
        separationFailedRecordDao.saveFailedRecord(
            projectId = context.projectId,
            repoName = context.repoName,
            type = context.type,
            taskId = context.taskId,
            packageId = packageId,
            versionId = versionId,
            nodeId = nodeId,
            actionDate = context.separationDate
        )
    }

    fun removeFailedRecord(
        context: SeparationContext,
        packageId: String? = null,
        versionId: String? = null,
        nodeId: String? = null,
    ) {
        separationFailedRecordDao.removeFailedRecord(
            projectId = context.projectId,
            repoName = context.repoName,
            type = context.type,
            taskId = context.taskId,
            packageId = packageId,
            versionId = versionId,
            nodeId = nodeId,
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AbstractHandler::class.java)
    }
}