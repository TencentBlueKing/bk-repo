/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.replication.replica.type.federation

import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.common.service.otel.util.AsyncUtils.trace
import com.tencent.bkrepo.replication.replica.executor.FederationThreadPoolExecutor
import com.tencent.bkrepo.replication.replica.type.event.EventConsumer
import com.tencent.bkrepo.replication.service.ReplicaTaskService
import org.springframework.stereotype.Component

/**
 * 构件事件消费者，用于实时同步
 * 对应binding name为artifactEvent-in-0
 */
@Component
class FederationArtifactEventConsumer(
    private val replicaTaskService: ReplicaTaskService,
    private val federationBasedReplicaJobExecutor: FederationBasedReplicaJobExecutor,
) : EventConsumer() {

    private val federationExecutors = FederationThreadPoolExecutor.instance

    /**
     * 允许接收的事件类型
     */
    override fun getAcceptTypes(): Set<EventType> {
        return setOf(
            EventType.NODE_CREATED,
            EventType.NODE_DELETED,
        )
    }

    override fun sourceCheck(message: ArtifactEvent): Boolean {
        return !message.source.isNullOrEmpty()
    }

    override fun action(event: ArtifactEvent) {
        federationExecutors.execute(
            Runnable {
                replicaTaskService.listFederationTasks(event.projectId, event.repoName).forEach {
                    federationBasedReplicaJobExecutor.execute(it, event)
                }
            }.trace()
        )
    }
}
