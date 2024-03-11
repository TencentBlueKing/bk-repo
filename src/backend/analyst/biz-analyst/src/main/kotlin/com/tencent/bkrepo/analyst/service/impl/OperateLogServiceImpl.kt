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

package com.tencent.bkrepo.analyst.service.impl

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.metadata.pojo.operate.OpLogListOption
import com.tencent.bkrepo.common.metadata.pojo.operate.OperateLog
import com.tencent.bkrepo.common.metadata.pojo.operate.OperateLogResponse
import com.tencent.bkrepo.common.metadata.service.operate.impl.OperateLogService
import com.tencent.bkrepo.repository.api.OperateLogClient
import org.springframework.scheduling.annotation.Async

open class OperateLogServiceImpl(private val operateLogClient: OperateLogClient): OperateLogService {
    override fun saveEventAsync(event: ArtifactEvent, address: String) {
        throw UnsupportedOperationException()
    }

    override fun save(operateLog: OperateLog) {
        operateLogClient.save(operateLog)
    }

    override fun save(operateLogs: Collection<OperateLog>) {
        operateLogClient.batchSave(operateLogs.toList())
    }

    @Async
    override fun saveAsync(operateLog: OperateLog) {
        operateLogClient.save(operateLog)
    }

    @Async
    override fun saveAsync(operateLogs: Collection<OperateLog>) {
        operateLogClient.batchSave(operateLogs.toList())
    }

    override fun saveEventsAsync(eventList: List<ArtifactEvent>, address: String) {
        throw UnsupportedOperationException()
    }

    override fun listPage(option: OpLogListOption): Page<OperateLog> {
        throw UnsupportedOperationException()
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
        throw UnsupportedOperationException()
    }
}
