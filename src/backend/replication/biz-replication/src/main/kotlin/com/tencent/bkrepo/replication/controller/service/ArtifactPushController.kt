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

package com.tencent.bkrepo.replication.controller.service

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.event.packages.VersionCreatedEvent
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.replication.api.ArtifactPushClient
import com.tencent.bkrepo.replication.pojo.remote.request.ArtifactPushRequest
import com.tencent.bkrepo.replication.replica.type.event.EventBasedReplicaJobExecutor
import com.tencent.bkrepo.replication.service.ReplicaTaskService
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.RestController

/**
 * 同步制品到远端集群
 */
@RestController
class ArtifactPushController(
    private val replicaTaskService: ReplicaTaskService,
    private val eventBasedReplicaJobExecutor: EventBasedReplicaJobExecutor
) : ArtifactPushClient {

    /**
     * 推送对应artifact到配置的外部仓库
     */
    override fun artifactPush(request: ArtifactPushRequest): Response<Void> {
        with(request) {
            val event = VersionCreatedEvent(
                projectId = projectId,
                repoName = repoName,
                packageKey = packageKey,
                packageVersion = packageVersion,
                userId = SecurityUtils.getUserId(),
                packageType = packageType,
                packageName = packageName,
                realIpAddress = null
            )
            replicaTaskService.listRealTimeTasks(event.projectId, event.repoName).forEach {
                eventBasedReplicaJobExecutor.execute(it, event)
            }
            return ResponseBuilder.success()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArtifactPushController::class.java)
    }
}
