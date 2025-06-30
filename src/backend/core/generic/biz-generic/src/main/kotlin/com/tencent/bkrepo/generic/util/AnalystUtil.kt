/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.generic.util

import com.tencent.bkrepo.analyst.api.ScanQualityClient
import com.tencent.bkrepo.common.metadata.properties.AnalystProperties
import com.tencent.bkrepo.common.metadata.util.MetadataUtils.generateForbidMetadata
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.pojo.metadata.FORBID_REASON_NOT_SCANNED
import com.tencent.bkrepo.repository.pojo.metadata.ForbidType
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class AnalystUtil(
    private val analystProperties: AnalystProperties,
    private val scanQualityClient: ScanQualityClient,
) {
    /**
     * 判断制品是否需要禁用，需要禁用时返回制品禁用元数据，用于添加到制品元数据中
     */
    fun resolveForbidMetadata(projectId: String, repoName: String, fullPath: String): List<MetadataModel>? {
        if (!analystProperties.enableForbidNotScanned) {
            return null
        }

        try {
            if (scanQualityClient.shouldForbidBeforeScanned(projectId, repoName, fullPath).data == true) {
                return generateForbidMetadata(true, FORBID_REASON_NOT_SCANNED, ForbidType.NOT_SCANNED, SYSTEM_USER)
            }
        } catch (e: Exception) {
            logger.error("Pre-check forbid status for [$projectId/$repoName$fullPath] failed", e)
        }
        return emptyList()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AnalystUtil::class.java)
    }
}
