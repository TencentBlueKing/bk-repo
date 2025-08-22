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

package com.tencent.bkrepo.replication.util

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.constant.PROJECT_ID
import com.tencent.bkrepo.common.artifact.constant.REPO_NAME
import com.tencent.bkrepo.replication.constant.BUILD_ID
import com.tencent.bkrepo.replication.constant.NAME
import com.tencent.bkrepo.replication.constant.PIPELINE_ID
import com.tencent.bkrepo.replication.constant.TASK_ID
import com.tencent.bkrepo.replication.pojo.metrics.ReplicationContent
import com.tencent.bkrepo.replication.pojo.metrics.ReplicationRecordDetailMetricsRecord
import com.tencent.bkrepo.replication.pojo.metrics.ReplicationTaskDetailMetricsRecord
import com.tencent.bkrepo.replication.pojo.metrics.ReplicationTaskMetricsRecord
import com.tencent.bkrepo.replication.pojo.record.ExecutionStatus
import com.tencent.bkrepo.replication.pojo.record.ReplicaRecordInfo
import com.tencent.bkrepo.replication.pojo.remote.request.RemoteConfigCreateRequest
import com.tencent.bkrepo.replication.pojo.task.ReplicaStatus
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskDetail
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskInfo
import com.tencent.bkrepo.replication.pojo.task.objects.PackageConstraint
import com.tencent.bkrepo.replication.pojo.task.objects.PathConstraint
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
            val registry: String = registry ?: clusterId!!
            return ReplicationTaskMetricsRecord(
                projectId = projectId,
                repoName = repoName,
                repoType = repoType,
                remoteProjectId = remoteProjectId.orEmpty(),
                remoteRepoName = remoteRepoName.orEmpty(),
                pipelineId = map[PIPELINE_ID].orEmpty(),
                buildId = map[BUILD_ID].orEmpty(),
                pipelineTaskId = map[TASK_ID].orEmpty(),
                repContent = convertReplicationContent(request),
                name = name,
                taskKey = replicaTaskInfo.key,
                registries = listOf(registry),
                replicaType = replicaType.name,
                enabled = enable,
                createDate = replicaTaskInfo.createdDate,
                modifyDate = replicaTaskInfo.lastModifiedDate,
                sourceType = description.orEmpty()
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
            val map = extractName(task.name)
            val pMap = map[NAME]?.let { splitName(it) } ?: emptyMap()
            val (remoteProjectId, remoteRepo) = getRemoteProjectIdAndRepo(taskDetail)
            return ReplicationTaskDetailMetricsRecord(
                taskKey = task.key,
                replicaType = task.replicaType.name,
                projectId = task.projectId,
                repoName = map[REPO_NAME].orEmpty(),
                remoteProjectId = remoteProjectId,
                remoteRepoName = remoteRepo,
                name = map[NAME].orEmpty(),
                pipelineId = pMap[PIPELINE_ID].orEmpty(),
                buildId = pMap[BUILD_ID].orEmpty(),
                pipelineTaskId = pMap[TASK_ID].orEmpty(),
                repContent = convertToReplicationContent(objects),
                taskStatus = taskStatus.name,
                recordId = record.id,
                executionStatus = status.name,
                executionStartTime = record.startTime.toString(),
                executionEndTime = if (status == ExecutionStatus.RUNNING) StringPool.EMPTY
                else LocalDateTime.now().toString(),
                errorReason = errorReason.orEmpty(),
                sourceType = task.description.orEmpty()
            )
        }
    }

    fun convertToReplicationRecordDetailMetricsRecord(
        task: ReplicaTaskInfo,
        recordId: String,
        packageName: String? = null,
        version: String? = null,
        path: String? = null,
        sha256: String? = null,
        size: String? = null,
        startTime: String,
        status: ExecutionStatus,
        errorReason: String? = null
    ): ReplicationRecordDetailMetricsRecord {
        val map = extractName(task.name)
        return ReplicationRecordDetailMetricsRecord(
            taskKey = task.key,
            projectId = task.projectId,
            repoName = map[REPO_NAME].orEmpty(),
            recordId = recordId,
            errorReason = errorReason.orEmpty(),
            sourceType = task.description.orEmpty(),
            packageName = packageName.orEmpty(),
            version = version.orEmpty(),
            path = path.orEmpty(),
            sha256 = sha256.orEmpty(),
            size = size.orEmpty(),
            status = status.name,
            startTime = startTime,
            endTime = LocalDateTime.now().toString()
        )
    }

    fun toJson(any: Any): String {
        return any.toJsonString().replace(System.lineSeparator(), "")
    }

    private fun getRemoteProjectIdAndRepo(taskDetail: ReplicaTaskDetail): Pair<String, String> {
        return Pair(
            taskDetail.objects.first().remoteProjectId.orEmpty(),
            taskDetail.objects.first().remoteRepoName.orEmpty()
        )
    }

    private fun convertToReplicationContent(objects: List<ReplicaObjectInfo>): List<ReplicationContent> {
        if (objects.isEmpty()) return emptyList()
        val contents = mutableListOf<ReplicationContent>()
        contents.addAll(convertPSToReplicationContent(objects.first().packageConstraints))
        contents.addAll(convertPathToReplicationContent(objects.first().pathConstraints))
        return contents
    }

    private fun convertReplicationContent(request: RemoteConfigCreateRequest): List<ReplicationContent> {
        val contents = mutableListOf<ReplicationContent>()
        contents.addAll(convertPSToReplicationContent(request.packageConstraints))
        contents.addAll(convertPathToReplicationContent(request.pathConstraints))
        return contents
    }

    private fun convertPSToReplicationContent(objects: List<PackageConstraint>?): List<ReplicationContent> {
        if (objects.isNullOrEmpty()) return emptyList()
        return objects.map {
            ReplicationContent(
                packageName = it.packageKey.orEmpty(),
                versions = it.versions.orEmpty()
            )
        }
    }

    private fun convertPathToReplicationContent(objects: List<PathConstraint>?): List<ReplicationContent> {
        if (objects.isNullOrEmpty()) return emptyList()
        return objects.map {
            ReplicationContent(
                path = it.path.orEmpty()
            )
        }
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
        val buildId = name.substring(buildIdIndex + 1, taskIdIndex)
        val taskId = name.substring(taskIdIndex + 1)
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
