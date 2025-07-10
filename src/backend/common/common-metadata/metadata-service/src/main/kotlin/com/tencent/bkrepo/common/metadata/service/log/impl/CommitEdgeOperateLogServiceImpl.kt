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

import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.common.metadata.dao.log.OperateLogDao
import com.tencent.bkrepo.common.metadata.pojo.log.OperateLog
import com.tencent.bkrepo.common.metadata.properties.OperateProperties
import com.tencent.bkrepo.common.service.cluster.properties.ClusterProperties
import com.tencent.bkrepo.common.service.feign.FeignClientFactory
import com.tencent.bkrepo.repository.api.cluster.ClusterOperateLogClient

open class CommitEdgeOperateLogServiceImpl(
    operateProperties: OperateProperties,
    operateLogDao: OperateLogDao,
    clusterProperties: ClusterProperties
) : OperateLogServiceImpl(
    operateProperties,
    operateLogDao,
) {

    private val centerOpLogClient: ClusterOperateLogClient by lazy {
        FeignClientFactory.create(
            clusterProperties.center,
            "repository",
            clusterProperties.self.name
        )
    }

    override fun saveEventAsync(event: ArtifactEvent, address: String) {
        if (notNeedRecord(event.type.name, event.projectId, event.repoName)) {
            return
        }
        super.saveEventAsync(event, address)
        if (event.type == EventType.NODE_DOWNLOADED) {
            val log = OperateLog(
                type = event.type.name,
                resourceKey = event.resourceKey,
                projectId = event.projectId,
                repoName = event.repoName,
                description = event.data,
                userId = event.userId,
                clientAddress = address
            )
            centerOpLogClient.save(log)
        }
    }

    override fun saveEventsAsync(eventList: List<ArtifactEvent>, address: String) {
        super.saveEventsAsync(eventList, address)
        centerOpLogClient.batchRecord(eventList)
    }
}
