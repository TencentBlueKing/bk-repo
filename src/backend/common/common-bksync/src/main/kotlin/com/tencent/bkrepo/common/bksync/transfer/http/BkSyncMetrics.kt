/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.bksync.transfer.http

import com.tencent.bkrepo.common.api.constant.CharPool
import com.tencent.bkrepo.common.api.constant.StringPool
import org.slf4j.LoggerFactory
import java.net.URLDecoder
import java.util.Base64

class BkSyncMetrics(
    // 网络速度，单位MB/s
    var networkSpeed: Int = 0,
    // 文件名
    var fileName: String = "",
    // 文件类型
    var fileType: String = "",
    // 文件大小，单位字节
    var fileSize: Long = 0,
    // 检测差异时间，单位毫秒
    var diffTime: Long = 0,
    // 重复块命中率
    var hitRate: Float = 0f,
    // 增量文件大小，单位字节
    var deltaFileSize: Long = 0,
    // 合并时间，单位毫秒
    var patchTime: Long = 0,
    // 普通上传时间，单位毫秒
    var genericUploadTime: Long = 0,
    // 历史普通上传速度，单位B/s
    var historyGenericUploadSpeed: Double = 0.0,
    // 上报指标时间戳
    var reportTimeStamp: Long = System.currentTimeMillis(),
    var projectId: String = "",
    var repoName: String = "",
    var pipelineId: String = "",
    var buildId: String = "",
    var taskId: String = "",
    var ip: String = ""
) {
    fun setBasicMetrics(request: UploadRequest) {
        fileName = request.file.name
        fileType = request.file.extension
        fileSize = request.file.length()
        projectId = request.projectId
        repoName = request.repoName
        parseMetadata(request.headers)
    }

    private fun parseMetadata(headers: Map<String, String>) {
        val header = headers[BKREPO_META]
        try {
            val metadataUrl = String(Base64.getDecoder().decode(header))
            metadataUrl.split(CharPool.AND).forEach { part ->
                val pair = part.trim().split(CharPool.EQUAL, limit = 2)
                if (pair.size < 2 || pair[0].isBlank() || pair[1].isBlank()) {
                    return@forEach
                }
                val key = URLDecoder.decode(pair[0], StringPool.UTF_8)
                val value = URLDecoder.decode(pair[1], StringPool.UTF_8)
                when (key) {
                    PIPELINE_ID -> pipelineId = value
                    BUILD_ID -> buildId = value
                    TASK_ID -> taskId = value
                }
            }
        } catch (exception: IllegalArgumentException) {
            logger.warn("$header is not in valid Base64 scheme.")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BkSyncMetrics::class.java)
        private const val BKREPO_META = "X-BKREPO-META"
        private const val PIPELINE_ID = "pipelineId"
        private const val BUILD_ID = "buildId"
        private const val TASK_ID = "taskId"
    }
}
