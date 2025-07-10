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

package com.tencent.bkrepo.common.artifact.metrics

/**
 * 分块上传/下载指标
 */
data class ChunkArtifactTransferMetrics(
    var tag: String = "ChunkArtifactTransferMetrics",
    // 类型
    var type: String = UPLOAD,
    // 文件路径
    var fullPath: String = "",
    // 文件大小，单位字节
    var fileSize: Long = 0,
    // 文件sha256
    var sha256: String = "",
    // client ip
    var clientIp: String = "",
    // 速率
    var average: Long = 0,
    // 上传或下载时间，NanoTime
    var costTime: Long = 0,
    // 成功或者失败
    var success: Boolean = true,
    //  失败原因
    var failedReason: String = "",
    // 上报指标时间戳
    var reportTimeStamp: Long = System.currentTimeMillis(),
    var projectId: String = "",
    var repoName: String = "",
    var pipelineId: String = "",
    var buildId: String = "",
    var taskId: String = "",
    var storage: String = ""
) {
    companion object {
        const val UPLOAD = "UPLOAD"
        const val DOWNLOAD = "DOWNLOAD"
    }
}


