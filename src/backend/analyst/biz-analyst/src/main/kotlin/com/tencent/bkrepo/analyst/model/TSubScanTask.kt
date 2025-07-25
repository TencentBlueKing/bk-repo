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

package com.tencent.bkrepo.analyst.model

import com.tencent.bkrepo.analyst.pojo.TaskMetadata
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/**
 * 正在扫描的子任务，查询比较频繁，单独一张表
 */
@Document("sub_scan_task")
@CompoundIndexes(
    CompoundIndex(
        name = "credentialsKey_sha256_idx",
        def = "{'credentialsKey': 1, 'sha256': 1}",
        background = true
    ),
    CompoundIndex(
        name = "lastModifiedDate_idx",
        def = "{'lastModifiedDate': 1}",
        background = true
    ),
    CompoundIndex(
        name = "timeoutDateTime_idx",
        def = "{'timeoutDateTime': 1}",
        background = true
    ),
    CompoundIndex(
        name = "projectId_status_idx",
        def = "{'projectId': 1, 'status': 1}",
        background = true
    ),
    CompoundIndex(
        name = "status_metadata_idx",
        def = "{'status': 1, 'metadata.key': 1, 'metadata.value': 1}",
        background = true
    ),
    CompoundIndex(
        name = "parentScanTaskId_idx",
        def = "{'parentScanTaskId': 1}",
        background = true
    )
)
@Suppress("LongParameterList")
class TSubScanTask(
    id: String? = null,
    createdDate: LocalDateTime,
    createdBy: String,
    lastModifiedDate: LocalDateTime,
    lastModifiedBy: String,
    startDateTime: LocalDateTime? = null,
    /**
     * 执行超时时间点
     */
    val timeoutDateTime: LocalDateTime? = null,
    /**
     * 任务上次心跳时间
     */
    val heartbeatDateTime: LocalDateTime? = null,
    triggerType: String? = null,
    parentScanTaskId: String,
    planId: String?,

    projectId: String,
    repoName: String,
    repoType: String,
    packageKey: String? = null,
    version: String? = null,
    fullPath: String,
    artifactName: String,

    status: String,
    /**
     * 已经执行的次数
     */
    val executedTimes: Int,
    scanner: String,
    scannerType: String,
    sha256: String,
    size: Long,
    packageSize: Long = size,
    credentialsKey: String?,
    /**
     * 扫描时方案的质量规则
     */
    scanQuality: Map<String, Any>? = null,
    /**
     * 扫描任务元数据
     */
    val metadata: List<TaskMetadata> = emptyList()
) : SubScanTaskDefinition(
    id = id,
    createdDate = createdDate,
    createdBy = createdBy,
    lastModifiedDate = lastModifiedDate,
    lastModifiedBy = lastModifiedBy,
    startDateTime = startDateTime,
    triggerType = triggerType,
    parentScanTaskId = parentScanTaskId,
    planId = planId,

    projectId = projectId,
    repoName = repoName,
    repoType = repoType,
    packageKey = packageKey,
    version = version,
    fullPath = fullPath,
    artifactName = artifactName,

    status = status,
    scanner = scanner,
    scannerType = scannerType,
    sha256 = sha256,
    size = size,
    packageSize = packageSize,
    credentialsKey = credentialsKey,
    scanResultOverview = null,
    scanQuality = scanQuality
)
