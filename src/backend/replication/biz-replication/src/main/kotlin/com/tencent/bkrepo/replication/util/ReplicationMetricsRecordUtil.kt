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

package com.tencent.bkrepo.replication.util

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.replication.constant.BUILD_ID
import com.tencent.bkrepo.replication.constant.NAME
import com.tencent.bkrepo.replication.constant.PIPELINE_ID
import com.tencent.bkrepo.replication.constant.PROJECT_ID
import com.tencent.bkrepo.replication.constant.REPO_NAME
import com.tencent.bkrepo.replication.constant.TASK_ID
import com.tencent.bkrepo.replication.pojo.metrics.ReplicationContent
import com.tencent.bkrepo.replication.pojo.metrics.ReplicationTaskDetailMetricsRecord
import com.tencent.bkrepo.replication.pojo.metrics.ReplicationTaskMetricsRecord
import com.tencent.bkrepo.replication.pojo.record.ExecutionStatus
import com.tencent.bkrepo.replication.pojo.record.ReplicaRecordInfo
import com.tencent.bkrepo.replication.pojo.remote.request.RemoteConfigCreateRequest
import com.tencent.bkrepo.replication.pojo.task.ReplicaStatus
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskDetail
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskInfo
import com.tencent.bkrepo.replication.pojo.task.objects.ReplicaObjectInfo
import java.time.LocalDateTime

/**
 * remote类型任务详情转换为任务指标记录
 */
object ReplicationMetricsRecordUtil {

    fun convertToReplicationTaskMetricsRecord(
        projectId: String,
        repoName: String,
        repoType: String,
        request: RemoteConfigCreateRequest,
        replicaTaskInfo: ReplicaTaskInfo
    ): ReplicationTaskMetricsRecord {
        with(request) {
            val map = splitName(name)
            return ReplicationTaskMetricsRecord(
                projectId = projectId,
                repoName = repoName,
                repoType = repoType,
                pipelineId = map[PIPELINE_ID].orEmpty(),
                buildId = map[BUILD_ID].orEmpty(),
                pipelineTaskId = map[TASK_ID].orEmpty(),
                name = name,
                taskKey = replicaTaskInfo.key,
                registries = listOf(registry),
                replicaType = replicaType.name,
                enabled = enable,
                createDate = replicaTaskInfo.createdDate,
                modifyDate = replicaTaskInfo.lastModifiedDate
            )
        }
    }

    fun convertToReplicationTaskDetailMetricsRecord(
        taskDetail: ReplicaTaskDetail,
        record: ReplicaRecordInfo,
        taskStatus: ReplicaStatus,
        status: ExecutionStatus,
        errorReason: String? = null
    ): ReplicationTaskDetailMetricsRecord {
        with(taskDetail) {
            return ReplicationTaskDetailMetricsRecord(
                taskKey = task.key,
                replicaType = task.replicaType.name,
                repContent = convertToReplicationContent(objects),
                taskStatus = taskStatus.name,
                recordId = record.id,
                executionStatus = status.name,
                executionStartTime = record.startTime.toString(),
                executionEndTime = if (status == ExecutionStatus.RUNNING) StringPool.EMPTY
                else LocalDateTime.now().toString(),
                errorReason = errorReason.orEmpty()
            )
        }
    }

    fun ReplicationTaskMetricsRecord.toJson(): String {
        return this.toJsonString().replace(System.lineSeparator(), "")
    }

    fun ReplicationTaskDetailMetricsRecord.toJson(): String {
        return this.toJsonString().replace(System.lineSeparator(), "")
    }

    private fun convertToReplicationContent(objects: List<ReplicaObjectInfo>): List<ReplicationContent> {
        if (objects.isEmpty()) return emptyList()
        return objects.first().packageConstraints?.map {
            ReplicationContent(
                packageName = it.packageKey.orEmpty(),
                versions = it.versions.orEmpty()
            )
        }.orEmpty()
    }

    /**
     * 从名字中解析出 pipelineId/buildId/taskId
     * p-*******-b-*******-e-******
     */
    private fun splitName(name: String): Map<String, String> {
        if (!name.startsWith("p-") || !name.contains("-b-") || !name.contains("-e-")) {
            return emptyMap()
        }
        val buildIdIndex = name.indexOf("-b-")
        val taskIdIndex = name.indexOf("-e-")
        val pipelineId = name.substring(0, buildIdIndex)
        val buildId = name.substring(buildIdIndex, taskIdIndex)
        val taskId = name.substring(taskIdIndex)
        return mapOf(
            PIPELINE_ID to pipelineId,
            BUILD_ID to buildId,
            TASK_ID to taskId
        )
    }

    /**
     * 存储的任务名为"{projectId}/{repoName}/{name}"
     */
    fun extractName(taskName: String): Map<String, String> {
        val list = taskName.split("/")
        return if (list.size < 3) {
            mapOf(
                PROJECT_ID to list.first(),
                REPO_NAME to StringPool.EMPTY,
                NAME to StringPool.EMPTY
            )
        } else {
            mapOf(
                PROJECT_ID to list.first(),
                REPO_NAME to list[1],
                NAME to list[2]
            )
        }
    }
}
