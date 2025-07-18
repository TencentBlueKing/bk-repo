/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.replication.pojo.record

/**
 * 同步进度
 */
data class ReplicaProgress(
    /**
     * 成功数量
     */
    var success: Long = 0,
    /**
     * 跳过数量
     */
    var skip: Long = 0,
    /**
     * 失败数量
     */
    var failed: Long = 0,
    /**
     * 数据大小, 单位bytes
     */
    var totalSize: Long = 0,
    /**
     * 冲突数量
     */
    var conflict: Long = 0
) {

    operator fun plus(replicaProgress: ReplicaProgress): ReplicaProgress {
        return ReplicaProgress(
            success = this.success + replicaProgress.success,
            skip = this.skip + replicaProgress.skip,
            failed = this.failed + replicaProgress.failed,
            totalSize = this.totalSize + replicaProgress.totalSize,
            conflict = this.conflict + replicaProgress.conflict
        )
    }
}
