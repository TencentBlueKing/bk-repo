/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.metadata.interceptor.impl

import com.tencent.bkrepo.analyst.api.ScanClient
import com.tencent.bkrepo.common.api.constant.CharPool
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.constant.FORBID_REASON
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.metadata.interceptor.DownloadInterceptorFactory
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import org.slf4j.LoggerFactory
import java.util.Base64

class NodeForbiddenStatusInterceptor : NodeMetadataInterceptor(DownloadInterceptorFactory.forbidRule) {

    override fun intercept(projectId: String, artifact: NodeDetail) {
        // 扫描任务下载时跳过禁用检查
        if (isValidScanRequest()) {
            return
        }
        super.intercept(projectId, artifact)
    }

    override fun forbiddenException(projectId: String, artifact: NodeDetail): Exception {
        return ErrorCodeException(
            messageCode = ArtifactMessageCode.ARTIFACT_FORBIDDEN,
            params = arrayOf(
                artifact.fullPath,
                artifact.metadata[FORBID_REASON]?.toString().orEmpty(),
            ),
            status = HttpStatus.FORBIDDEN,
        )
    }

    /**
     * 验证扫描请求的有效性
     */
    private fun isValidScanRequest(): Boolean {
        return try {
            val ssid = HttpContextHolder.getRequestOrNull()?.getParameter("ssid")
            if (ssid.isNullOrBlank()) {
                return false
            }
            val ssidStr = String(Base64.getDecoder().decode(ssid.toByteArray()))
            val parts = ssidStr.split(CharPool.COLON)
            require(parts.size == 2)
            // 使用 ScanClient 验证 ssid
            val scanClient = SpringContextUtils.getBean<ScanClient>()
            val result = scanClient.verifyToken(subtaskId = parts[0], token = parts[1])
            
            if (result.isOk() && result.data == true) {
                true
            } else {
                logger.info("Invalid scan ssid: $ssid")
                false
            }
        } catch (e: Exception) {
            // ScanClient 不存在或验证失败
            logger.warn("Failed to verify scan ssid", e)
            false
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NodeForbiddenStatusInterceptor::class.java)
    }
}
