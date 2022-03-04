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

package com.tencent.bkrepo.scanner.service

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.scanner.pojo.request.ScanRequest
import com.tencent.bkrepo.scanner.pojo.ScanTask
import com.tencent.bkrepo.scanner.pojo.ScanTriggerType
import com.tencent.bkrepo.scanner.pojo.SubScanTask
import com.tencent.bkrepo.scanner.pojo.request.FileScanResultDetailRequest
import com.tencent.bkrepo.scanner.pojo.request.FileScanResultOverviewRequest
import com.tencent.bkrepo.scanner.pojo.request.ReportResultRequest
import com.tencent.bkrepo.scanner.pojo.request.ScanTaskQuery
import com.tencent.bkrepo.scanner.pojo.response.FileScanResultDetail
import com.tencent.bkrepo.scanner.pojo.response.FileScanResultOverview

/**
 * 扫描服务
 */
interface ScanService {
    /**
     * 创建扫描任务，启动扫描
     *
     * @param scanRequest 扫描参数，指定使用的扫描器和需要扫描的文件
     * @param triggerType 触发类型
     */
    fun scan(scanRequest: ScanRequest, triggerType: ScanTriggerType): ScanTask

    /**
     * 获取扫描任务
     *
     * @param taskId 任务id
     */
    fun task(taskId: String): ScanTask

    /**
     * 分页获取扫描任务
     */
    fun tasks(scanTaskQuery: ScanTaskQuery, pageLimit: PageLimit): Page<ScanTask>

    /**
     * 扫描结果上报
     *
     * @param reportResultRequest 扫描结果上报请求
     */
    fun reportResult(reportResultRequest: ReportResultRequest)

    /**
     * 获取扫描结果预览
     *
     * @param request 扫描预览请求参数
     *
     * @return 扫描结果预览数据
     */
    fun resultOverview(request: FileScanResultOverviewRequest): List<FileScanResultOverview>

    /**
     * 获取文件扫描报告详情
     *
     * @param request 获取文件扫描报告请求参数
     *
     * @return 文件扫描报告详情
     */
    fun resultDetail(request: FileScanResultDetailRequest): FileScanResultDetail

    /**
     * 拉取子任务
     *
     * @return 没有可执行的任务时返回null，否则返回一个待执行的任务
     */
    fun pullSubScanTask(): SubScanTask?
}
