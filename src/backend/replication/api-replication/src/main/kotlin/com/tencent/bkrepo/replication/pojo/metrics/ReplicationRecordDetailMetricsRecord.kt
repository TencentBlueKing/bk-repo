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
 * 一次性分发任务具体package或者path同步执行记录
 */
data class ReplicationRecordDetailMetricsRecord(
    var tag: String = "TaskRecordDetailTag",
    var taskKey: String = StringPool.EMPTY,
    var recordId: String = StringPool.EMPTY,
    var projectId: String = StringPool.EMPTY,
    var repoName: String = StringPool.EMPTY,
    var packageName: String = StringPool.EMPTY,
    var version: String = StringPool.EMPTY,
    var path: String = StringPool.EMPTY,
    var sha256: String = StringPool.EMPTY,
    var size: String = StringPool.EMPTY,
    var status: String = StringPool.EMPTY,
    var startTime: String = StringPool.EMPTY,
    var endTime: String = StringPool.EMPTY,
    var errorReason: String = StringPool.EMPTY,
    var sourceType: String = StringPool.EMPTY
)
