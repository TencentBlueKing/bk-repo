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

package com.tencent.bkrepo.analyst.controller.user

import com.tencent.bkrepo.analyst.pojo.request.ReportResultRequest
import com.tencent.bkrepo.analyst.service.ScanService
import com.tencent.bkrepo.analyst.service.TemporaryScanTokenService
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.ToolInput
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Api("使用临时token访问扫描接口")
@RestController
@RequestMapping("/api/temporary")
class UserTemporaryScanController(
    private val temporaryScanTokenService: TemporaryScanTokenService,
    private val scanService: ScanService
) {

    @ApiOperation("获取扫描子任务信息")
    @GetMapping("/scan/subtask/{subtaskId}/input")
    fun getSubtask(
        @PathVariable subtaskId: String,
        @RequestParam token: String
    ): Response<ToolInput> {
        temporaryScanTokenService.checkToken(subtaskId, token)
        return ResponseBuilder.success(temporaryScanTokenService.getToolInput(subtaskId, token))
    }

    @ApiOperation("拉取扫描子任务")
    @GetMapping("/scan/subtask/input")
    fun pullSubtask(
        @RequestParam executionCluster: String,
        @RequestParam token: String
    ): Response<ToolInput?> {
        temporaryScanTokenService.checkToken(executionCluster, token)
        val toolInput = temporaryScanTokenService.pullToolInput(executionCluster, token)
        toolInput?.let { temporaryScanTokenService.setToken(it.taskId, token) }
        return ResponseBuilder.success(toolInput)
    }

    @ApiOperation("扫描结果上报")
    @PostMapping("/scan/report")
    fun report(@RequestBody reportResultRequest: ReportResultRequest): Response<Void> {
        temporaryScanTokenService.checkToken(reportResultRequest.subTaskId, reportResultRequest.token)
        scanService.reportResult(reportResultRequest)
        // 扫描结束上报结果后删除token
        temporaryScanTokenService.deleteToken(reportResultRequest.subTaskId)
        return ResponseBuilder.success()
    }

    @ApiOperation("扫描任务状态更新")
    @PutMapping("/scan/subtask/{subtaskId}/status")
    fun updateSubScanTaskStatus(
        @PathVariable subtaskId: String,
        @RequestParam status: String,
        @RequestParam token: String
    ): Response<Boolean> {
        temporaryScanTokenService.checkToken(subtaskId, token)
        return ResponseBuilder.success(scanService.updateSubScanTaskStatus(subtaskId, status))
    }

    @ApiOperation("维持任务心跳")
    @PostMapping("/scan/subtask/{subtaskId}/heartbeat")
    fun heartbeat(
        @PathVariable subtaskId: String,
        @RequestParam token: String
    ): Response<Void> {
        temporaryScanTokenService.checkToken(subtaskId, token)
        scanService.heartbeat(subtaskId)
        return ResponseBuilder.success()
    }
}
