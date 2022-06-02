/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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
    var networkSpeed: Int = 0,
    var fileSize: Long = 0,
    var diffTime: Long = 0,
    var patchTime: Long = 0,
    var genericUploadTime: Long = 0,
    var projectId: String = "",
    var repoName: String = "",
    var pipelineId: String = "",
    var buildId: String = "",
    var ip: String = ""
) {
    fun setBasicMetrics(request: UploadRequest) {
        fileSize = request.file.length()
        projectId = request.projectId
        repoName = request.repoName
        val (pId, bId) = getPipelineIdAndBuildId(request.headers)
        pipelineId = pId
        buildId = bId
    }

    private fun getPipelineIdAndBuildId(headers: Map<String, String>): Pair<String, String> {
        val header = headers[BKREPO_META]
        var pipelineId = ""
        var buildId = ""
        try {
            val metadataUrl = String(Base64.getDecoder().decode(header))
            metadataUrl.split(CharPool.AND).forEach { part ->
                val pair = part.trim().split(CharPool.EQUAL, limit = 2)
                if (pair.size > 1 && pair[0].isNotBlank() && pair[1].isNotBlank()) {
                    val key = URLDecoder.decode(pair[0], StringPool.UTF_8)
                    val value = URLDecoder.decode(pair[1], StringPool.UTF_8)
                    when (key) {
                        PIPELINE_ID -> pipelineId = value
                        BUILD_ID -> buildId = value
                    }
                }
            }
        } catch (exception: IllegalArgumentException) {
            logger.warn("$header is not in valid Base64 scheme.")
        }
        return Pair(pipelineId, buildId)
    }

    override fun toString(): String {
        return "BkSyncMetrics {networkSpeed:$networkSpeed, fileSize:$fileSize, diffTime:$diffTime, " +
            "patchTime:$patchTime, genericUploadTime:$genericUploadTime, projectId:'$projectId', repoName:'$repoName', " +
            "pipelineId:'$pipelineId', buildId:'$buildId', ip:'$ip'}"
    }


    companion object {
        private val logger = LoggerFactory.getLogger(BkSyncMetrics::class.java)
        private const val BKREPO_META = "X-BKREPO-META"
        private const val PIPELINE_ID = "pipelineId"
        private const val BUILD_ID = "buildId"
    }
}
