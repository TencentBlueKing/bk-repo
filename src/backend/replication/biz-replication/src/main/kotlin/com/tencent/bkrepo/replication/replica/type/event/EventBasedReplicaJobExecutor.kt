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

package com.tencent.bkrepo.replication.replica.type.event

import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.pojo.request.ReplicaObjectType
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskDetail
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.service.ReplicaRecordService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 基于事件消息的实时同步逻辑实现类
 */
@Suppress("TooGenericExceptionCaught")
@Component
class EventBasedReplicaJobExecutor(
    clusterNodeService: ClusterNodeService,
    localDataManager: LocalDataManager,
    replicaService: EventBasedReplicaService,
    replicationProperties: ReplicationProperties,
    replicaRecordService: ReplicaRecordService,
) : CommonBasedReplicaJobExecutor(
    clusterNodeService, localDataManager, replicaService, replicationProperties, replicaRecordService
) {


    /**
     * 判断分发配置内容是否与待分发事件匹配
     */
    override fun replicaObjectCheck(task: ReplicaTaskDetail, event: ArtifactEvent): Boolean {
        if (!task.task.enabled) return false
        return when (task.task.replicaObjectType) {
            ReplicaObjectType.PATH -> {
                pathCheck(event, task)
            }

            ReplicaObjectType.PACKAGE -> {
                packageCheck(event, task)
            }

            else -> true
        }
    }

    private fun pathCheck(event: ArtifactEvent, task: ReplicaTaskDetail): Boolean {
        if (event.type != EventType.NODE_CREATED) return false
        task.objects.forEach {
            it.pathConstraints?.forEach {
                if (it.path.isNullOrEmpty()) {
                    return false
                }
                val fullPath = PathUtils.toFullPath(it.path!!)
                if (event.resourceKey == fullPath) return true
                val ancestorFolder = PathUtils.resolveAncestor(event.resourceKey)
                val existPath = ancestorFolder.firstOrNull { PathUtils.toFullPath(it) == fullPath }
                if (existPath != null) return true
            }
        }
        return false
    }

    private fun packageCheck(event: ArtifactEvent, task: ReplicaTaskDetail): Boolean {
        if (event.type != EventType.VERSION_CREATED && event.type != EventType.VERSION_UPDATED) return false
        val packageKey = event.data["packageKey"].toString()
        val packageVersion = event.data["packageVersion"].toString()
        task.objects.forEach {
            it.packageConstraints?.forEach {
                if (packageKey != it.packageKey) return false
                if (it.versions.isNullOrEmpty() || it.versions!!.contains(packageVersion)) {
                    return true
                }
            }
        }
        return false
    }

    companion object {
        private val logger = LoggerFactory.getLogger(EventBasedReplicaJobExecutor::class.java)
    }
}
