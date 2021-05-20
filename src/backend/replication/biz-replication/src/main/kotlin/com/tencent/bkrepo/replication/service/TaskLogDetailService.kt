/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.replication.service

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.replication.model.TReplicationTaskLogDetail
import com.tencent.bkrepo.replication.pojo.log.LogDetailListOption
import com.tencent.bkrepo.replication.pojo.log.ReplicationTaskLogDetail
import com.tencent.bkrepo.replication.repository.TaskLogDetailRepository
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Service

@Service
class TaskLogDetailService(
    private val taskLogDetailRepository: TaskLogDetailRepository,
    private val mongoTemplate: MongoTemplate
) {
    fun list(taskKey: String): List<ReplicationTaskLogDetail> {
        return taskLogDetailRepository.findByTaskLogKey(taskKey).map { convert(it)!! }
    }

    fun listLogDetailPage(taskLogKey: String, option: LogDetailListOption): Page<ReplicationTaskLogDetail> {
        val pageNumber = option.pageNumber
        val pageSize = option.pageSize
        Preconditions.checkArgument(pageNumber >= 0, "pageNumber")
        Preconditions.checkArgument(pageSize >= 0, "pageSize")
        val query =
            logDetailListQuery(taskLogKey, option.packageName, option.slaveName, option.status, option.repoName)
        val pageRequest = Pages.ofRequest(pageNumber, pageSize)
        val totalRecords = mongoTemplate.count(query, TReplicationTaskLogDetail::class.java)
        val records = mongoTemplate.find(
            query.with(pageRequest),
            TReplicationTaskLogDetail::class.java
        ).map { convert(it)!! }
        return Pages.ofResponse(pageRequest, totalRecords, records)
    }

    private fun logDetailListQuery(
        taskLogKey: String,
        packageName: String? = null,
        slaveName: String? = null,
        status: String? = null,
        repoName: String? = null
    ): Query {
        val criteria = where(TReplicationTaskLogDetail::taskLogKey).isEqualTo(taskLogKey)
        if (packageName != null && packageName.isNotBlank()) {
            criteria.and(TReplicationTaskLogDetail::packageName).regex("^$packageName")
        }
        if (slaveName != null && slaveName.isNotBlank()) {
            criteria.and(TReplicationTaskLogDetail::slaveName).isEqualTo(slaveName)
        }
        if (status != null && status.isNotBlank()) {
            criteria.and(TReplicationTaskLogDetail::status).isEqualTo(status.toUpperCase())
        }
        if (repoName != null && repoName.isNotBlank()) {
            criteria.and(TReplicationTaskLogDetail::repoName).isEqualTo(repoName)
        }
        return Query(criteria)
    }

    companion object {
        private fun convert(log: TReplicationTaskLogDetail?): ReplicationTaskLogDetail? {
            return log?.let {
                ReplicationTaskLogDetail(
                    taskLogKey = it.taskLogKey,
                    status = it.status,
                    masterName = it.masterName,
                    slaveName = it.slaveName,
                    projectId = it.projectId,
                    repoName = it.repoName,
                    packageName = it.packageName,
                    packageKey = it.packageKey,
                    type = it.type,
                    version = it.version,
                    failLevelArtifact = it.failLevelArtifact,
                    errorReason = it.errorReason
                )
            }
        }
    }
}
