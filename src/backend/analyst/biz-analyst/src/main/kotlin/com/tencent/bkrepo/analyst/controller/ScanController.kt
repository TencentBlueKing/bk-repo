/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.analyst.controller

import com.tencent.bkrepo.analyst.api.ScanClient
import com.tencent.bkrepo.analyst.pojo.ScanTask
import com.tencent.bkrepo.analyst.pojo.ScanTriggerType
import com.tencent.bkrepo.analyst.pojo.SubScanTask
import com.tencent.bkrepo.analyst.pojo.license.SpdxLicenseInfo
import com.tencent.bkrepo.analyst.pojo.request.ReportResultRequest
import com.tencent.bkrepo.analyst.pojo.request.ScanRequest
import com.tencent.bkrepo.analyst.service.ScanService
import com.tencent.bkrepo.analyst.service.ScanTaskService
import com.tencent.bkrepo.analyst.service.SpdxLicenseService
import com.tencent.bkrepo.analyst.service.TemporaryScanTokenService
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class ScanController @Autowired constructor(
    private val scanService: ScanService,
    private val licenseService: SpdxLicenseService,
    private val tokenService: TemporaryScanTokenService,
    private val scanTaskService: ScanTaskService,
) : ScanClient {

    override fun scan(scanRequest: ScanRequest): Response<ScanTask> {
        return ResponseBuilder.success(
            scanService.scan(scanRequest, ScanTriggerType.ON_NEW_ARTIFACT, SecurityUtils.getUserId())
        )
    }

    override fun report(reportResultRequest: ReportResultRequest): Response<Void> {
        scanService.reportResult(reportResultRequest)
        return ResponseBuilder.success()
    }

    override fun pullSubTask(): Response<SubScanTask?> {
        return ResponseBuilder.success(scanService.pull())
    }

    override fun updateSubScanTaskStatus(subScanTaskId: String, status: String): Response<Boolean> {
        return ResponseBuilder.success(scanService.updateSubScanTaskStatus(subScanTaskId, status))
    }

    override fun heartbeat(subtaskId: String): Response<Boolean> {
        scanService.heartbeat(subtaskId)
        return ResponseBuilder.success()
    }

    override fun licenseInfoByIds(licenseIds: List<String>): Response<Map<String, SpdxLicenseInfo>> {
        return ResponseBuilder.success(licenseService.listLicenseByIds(licenseIds))
    }

    override fun verifyToken(subtaskId: String, token: String): Response<Boolean> {
        val ret = try {
            tokenService.checkToken(subtaskId, token)
            true
        } catch (e: AuthenticationException) {
            false
        }
        return ResponseBuilder.success(ret)
    }

    override fun getTask(taskId: String): Response<ScanTask> {
        return ResponseBuilder.success(scanTaskService.task(taskId))
    }
}
