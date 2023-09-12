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

package com.tencent.bkrepo.analyst.api

import com.tencent.bkrepo.analyst.pojo.ScanTask
import com.tencent.bkrepo.analyst.pojo.SubScanTask
import com.tencent.bkrepo.analyst.pojo.license.SpdxLicenseInfo
import com.tencent.bkrepo.analyst.pojo.request.ReportResultRequest
import com.tencent.bkrepo.analyst.pojo.request.ScanRequest
import com.tencent.bkrepo.common.api.constant.SCANNER_SERVICE_NAME
import com.tencent.bkrepo.common.api.pojo.Response
import io.swagger.annotations.ApiParam
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(SCANNER_SERVICE_NAME, contextId = "ScanClient")
@RequestMapping("/service/scan")
interface ScanClient {

    /**
     * 创建扫描任务
     * @param scanRequest 扫描请求
     * @return 创建的扫描任务
     */
    @PostMapping
    fun scan(@RequestBody scanRequest: ScanRequest): Response<ScanTask>

    /**
     * 扫描结果上报
     * @param reportResultRequest 扫描结果上报请求
     */
    @PostMapping("/report")
    fun report(@RequestBody reportResultRequest: ReportResultRequest): Response<Void>

    /**
     * 拉取任务，没有待扫描的任务时返回null
     */
    @GetMapping("/subtask")
    fun pullSubTask(): Response<SubScanTask?>

    /**
     * 更新子任务扫描状态
     *
     * @return 更新是否成功
     */
    @PutMapping("/subtask/{subScanTaskId}/status")
    fun updateSubScanTaskStatus(
        @PathVariable("subScanTaskId")subScanTaskId: String,
        @RequestParam status: String
    ): Response<Boolean>

    /**
     * 维持任务心跳
     */
    @PostMapping("/subtask/{subtaskId}/heartbeat")
    fun heartbeat(
        @PathVariable("subtaskId") subtaskId: String,
    ): Response<Boolean>

    /**
     * 根据许可id列表查询许可详细信息
     *
     * @return 许可id:许可详细信息
     */
    @PostMapping("/licenseIds")
    fun licenseInfoByIds(
        @ApiParam(value = "许可证唯一标识集合")
        @RequestBody licenseIds: List<String>
    ): Response<Map<String, SpdxLicenseInfo>>

    /**
     * 校验token
     *
     * @return 通过返回true,否则返回false
     * */
    @GetMapping("/token/verify")
    fun verifyToken(@RequestParam subtaskId: String, @RequestParam token: String): Response<Boolean>

    /**
     * 查询task状态
     * */
    @GetMapping("/task/{taskId}")
    fun getTask(@PathVariable taskId: String): Response<ScanTask>
}
