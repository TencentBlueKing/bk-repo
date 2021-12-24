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

package com.tencent.bkrepo.repository.service.log.impl

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.repository.dao.OperateLogDao
import com.tencent.bkrepo.repository.model.TOperateLog
import com.tencent.bkrepo.repository.pojo.log.OpLogListOption
import com.tencent.bkrepo.repository.pojo.log.OperateLog
import com.tencent.bkrepo.repository.service.log.OperateLogService
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

/**
 * OperateLogService 实现类
 */
@Service
class OperateLogServiceImpl(
    private val operateLogDao: OperateLogDao
) : OperateLogService {

    @Async
    override fun saveEventAsync(event: ArtifactEvent, address: String) {
        val log = TOperateLog(
            type = event.type,
            resourceKey = event.resourceKey,
            projectId = event.projectId,
            repoName = event.repoName,
            description = event.data,
            userId = event.userId,
            clientAddress = address
        )
        operateLogDao.insert(log)
    }

    @Async
    override fun saveEventsAsync(eventList: List<ArtifactEvent>, address: String) {
        val logs = eventList.map {
            TOperateLog(
                type = it.type,
                resourceKey = it.resourceKey,
                projectId = it.projectId,
                repoName = it.repoName,
                description = it.data,
                userId = it.userId,
                clientAddress = address
            )
        }
        operateLogDao.insert(logs)
    }

    override fun listPage(option: OpLogListOption): Page<OperateLog> {
        with(option) {
            val criteria = where(TOperateLog::projectId).isEqualTo(projectId)
                .and(TOperateLog::repoName).isEqualTo(repoName)
                .and(TOperateLog::type).isEqualTo(eventType)
                .and(TOperateLog::createdDate).gte(startTime).lte(endTime)
                .apply {
                    userId?.run { and(TOperateLog::userId).isEqualTo(userId) }
                    sha256?.run { and("${TOperateLog::description.name}.sha256").isEqualTo(sha256) }
                    pipelineId?.run { and("${TOperateLog::description.name}.pipelineId").isEqualTo(pipelineId) }
                    buildId?.run { and("${TOperateLog::description.name}.buildId").isEqualTo(buildId) }
                }
            if (prefixSearch) {
                criteria.and(TOperateLog::resourceKey).regex("^$resourceKey")
            } else {
                criteria.and(TOperateLog::resourceKey).isEqualTo(resourceKey)
            }
            val query = Query(criteria)
            val totalCount = operateLogDao.count(query)
            val pageRequest = Pages.ofRequest(pageNumber, pageSize)
            val sort = Sort.by(Sort.Direction.valueOf(direction.toString()), TOperateLog::createdDate.name)
            val records = operateLogDao.find(query.with(pageRequest).with(sort)).map { transfer(it) }
            return Pages.ofResponse(pageRequest, totalCount, records)
        }
    }

    private fun transfer(tOperateLog: TOperateLog) : OperateLog {
        with(tOperateLog) {
            return OperateLog(
                createdDate = createdDate,
                type = type,
                projectId = projectId,
                repoName = repoName,
                resourceKey = resourceKey,
                userId = userId,
                clientAddress = clientAddress,
                description = description
            )
        }
    }
}
