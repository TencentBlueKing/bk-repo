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
import com.tencent.bkrepo.fs.server.constant.FAKE_SHA256
import com.tencent.bkrepo.job.separation.constant.FILE_REFERENCE_COLLECTION_NAME
import com.tencent.bkrepo.job.separation.constant.SEPARATE
import com.tencent.bkrepo.job.separation.model.TSeparationFailedRecord
import com.tencent.bkrepo.job.separation.pojo.NodeFilterInfo
import com.tencent.bkrepo.job.separation.pojo.PackageFilterInfo
import com.tencent.bkrepo.job.separation.pojo.query.FileReferenceInfo
import com.tencent.bkrepo.job.separation.pojo.record.SeparationContext
import com.tencent.bkrepo.job.separation.pojo.record.SeparationProgress
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import java.time.LocalDateTime

abstract class AbstractHandler(
    private val mongoTemplate: MongoTemplate,
) {

    fun validatePackageParams(pkg: PackageFilterInfo?) {
        if (pkg != null && pkg.packageName.isNullOrEmpty() && pkg.packageRegex.isNullOrEmpty()) {
            throw NotFoundException(
                CommonMessageCode.PARAMETER_MISSING,
                "packageName: ${pkg.packageName}|${pkg.packageRegex}"
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

    fun setSuccessProgress(
        separationProgress: SeparationProgress,
        packageId: String? = null, versionId: String? = null,
        nodeId: String? = null
    ) {
        separationProgress.success++
        separationProgress.packageId = packageId
        separationProgress.versionId = versionId
        separationProgress.nodeId = nodeId
    }

    fun setFailedProgress(
        separationProgress: SeparationProgress,
        packageId: String? = null, versionId: String? = null,
        nodeId: String? = null
    ) {
        separationProgress.failed++
        separationProgress.packageId = packageId
        separationProgress.versionId = versionId
        separationProgress.nodeId = nodeId
    }

    fun buildTSeparationFailedRecord(
        context: SeparationContext,
        actionDate: LocalDateTime,
        packageId: String? = null,
        versionId: String? = null,
        nodeId: String? = null,
        type: String = SEPARATE,
    ): TSeparationFailedRecord {
        return TSeparationFailedRecord(
            projectId = context.projectId,
            repoName = context.repoName,
            createdDate = LocalDateTime.now(),
            taskId = context.taskId,
            actionDate = actionDate,
            type = type,
            packageId = packageId,
            versionId = versionId,
            nodeId = nodeId
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AbstractHandler::class.java)
    }
}