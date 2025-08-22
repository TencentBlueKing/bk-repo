/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.metadata.service.log.impl

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.metadata.dao.log.OperateLogDao
import com.tencent.bkrepo.common.metadata.model.TOperateLog
import com.tencent.bkrepo.common.metadata.pojo.log.OpLogListOption
import com.tencent.bkrepo.common.metadata.pojo.log.OperateLog
import com.tencent.bkrepo.common.metadata.pojo.log.OperateLogResponse
import com.tencent.bkrepo.common.metadata.properties.OperateProperties
import com.tencent.bkrepo.common.metadata.service.log.OperateLogService
import com.tencent.bkrepo.common.metadata.util.OperateLogServiceHelper.buildListQuery
import com.tencent.bkrepo.common.metadata.util.OperateLogServiceHelper.buildLog
import com.tencent.bkrepo.common.metadata.util.OperateLogServiceHelper.buildOperateLogPageQuery
import com.tencent.bkrepo.common.metadata.util.OperateLogServiceHelper.convert
import com.tencent.bkrepo.common.metadata.util.OperateLogServiceHelper.match
import com.tencent.bkrepo.common.metadata.util.OperateLogServiceHelper.transfer
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import org.springframework.data.domain.Sort
import org.springframework.scheduling.annotation.Async

/**
 * OperateLogService 实现类
 */
open class OperateLogServiceImpl(
    private val operateProperties: OperateProperties,
    private val operateLogDao: OperateLogDao,
) : OperateLogService {

    @Async
    override fun saveEventAsync(event: ArtifactEvent, address: String) {
        if (notNeedRecord(event.type.name, event.projectId, event.repoName)) {
            return
        }
        val log = buildLog(event, address)
        operateLogDao.insert(log)
    }

    override fun save(operateLog: OperateLog) {
        with(operateLog) {
            if (notNeedRecord(type, projectId, repoName)) {
                return
            }
            operateLogDao.insert(convert(operateLog))
        }
    }

    override fun save(operateLogs: Collection<OperateLog>) {
        val logs = ArrayList<TOperateLog>(operateLogs.size)
        for (operateLog in operateLogs) {
            if (notNeedRecord(operateLog.type, operateLog.projectId, operateLog.repoName)) {
                continue
            }
            logs.add(convert(operateLog))
        }

        if (logs.isNotEmpty()) {
            operateLogDao.insert(logs)
        }
    }

    @Async
    override fun saveAsync(operateLog: OperateLog) {
        save(operateLog)
    }

    @Async
    override fun saveAsync(operateLogs: Collection<OperateLog>) {
        save(operateLogs)
    }

    @Async
    override fun saveEventsAsync(eventList: List<ArtifactEvent>, address: String) {
        val logs = mutableListOf<TOperateLog>()
        eventList.forEach {
            if (notNeedRecord(it.type.name, it.projectId, it.repoName)) {
                return@forEach
            }
            logs.add(buildLog(it, address))
        }
        if (logs.isNotEmpty()) {
            operateLogDao.insert(logs)
        }
    }

    override fun listPage(option: OpLogListOption): Page<OperateLog> {
        with(option) {
            val query = buildListQuery()
            val totalCount = operateLogDao.count(query)
            val pageRequest = Pages.ofRequest(pageNumber, pageSize)
            val sort = Sort.by(Sort.Direction.valueOf(direction.toString()), TOperateLog::createdDate.name)
            val records = operateLogDao.find(query.with(pageRequest).with(sort)).map { transfer(it) }
            return Pages.ofResponse(pageRequest, totalCount, records)
        }
    }

    override fun page(
        type: String?,
        projectId: String?,
        repoName: String?,
        operator: String?,
        startTime: String?,
        endTime: String?,
        pageNumber: Int,
        pageSize: Int
    ): Page<OperateLogResponse?> {
        val pageRequest = Pages.ofRequest(pageNumber, pageSize)
        val query = buildOperateLogPageQuery(type, projectId, repoName, operator, startTime, endTime)
        val totalRecords = operateLogDao.count(query)
        val records = operateLogDao.find(query.with(pageRequest)).map { convert(it) }
        return Pages.ofResponse(pageRequest, totalRecords, records)
    }

    protected fun notNeedRecord(type: String, projectId: String?, repoName: String?): Boolean {
        val projectRepoKey = "$projectId/$repoName"
        if (match(operateProperties.eventType, type)) {
            return true
        }
        if (match(operateProperties.projectRepoKey, projectRepoKey)) {
            return true
        }
        return false
    }
}
