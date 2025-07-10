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

package com.tencent.bkrepo.replication.pojo.metrics

import com.tencent.bkrepo.common.api.constant.StringPool

/**
 * 一次性分发任务执行详情指标
 */
data class ReplicationTaskDetailMetricsRecord(
    var tag: String = "ExecutionTaskTag",
    var taskKey: String = StringPool.EMPTY,
    var projectId: String = StringPool.EMPTY,
    var repoName: String = StringPool.EMPTY,
    var remoteProjectId: String = StringPool.EMPTY,
    var remoteRepoName: String = StringPool.EMPTY,
    var replicaType: String = StringPool.EMPTY,
    var pipelineId: String = StringPool.EMPTY,
    var buildId: String = StringPool.EMPTY,
    // 流水线中的任务id，非分发任务id
    var pipelineTaskId: String = StringPool.EMPTY,
    var name: String = StringPool.EMPTY,
    var repContent: List<ReplicationContent> = emptyList(),
    var recordId: String = StringPool.EMPTY,
    var taskStatus: String = StringPool.EMPTY,
    var executionStatus: String = StringPool.EMPTY,
    var executionStartTime: String = StringPool.EMPTY,
    var executionEndTime: String = StringPool.EMPTY,
    var errorReason: String = StringPool.EMPTY,
    var sourceType: String = StringPool.EMPTY
)
