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

package com.tencent.bkrepo.analyst.service

import com.tencent.bkrepo.analyst.pojo.ScanTask
import com.tencent.bkrepo.analyst.pojo.request.ArtifactVulnerabilityRequest
import com.tencent.bkrepo.analyst.pojo.request.FileScanResultDetailRequest
import com.tencent.bkrepo.analyst.pojo.request.FileScanResultOverviewRequest
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.analyst.pojo.request.ScanTaskQuery
import com.tencent.bkrepo.analyst.pojo.request.SubtaskInfoRequest
import com.tencent.bkrepo.analyst.pojo.request.scancodetoolkit.ArtifactLicensesDetailRequest
import com.tencent.bkrepo.analyst.pojo.response.SubtaskResultOverview
import com.tencent.bkrepo.analyst.pojo.response.ArtifactVulnerabilityInfo
import com.tencent.bkrepo.analyst.pojo.response.FileLicensesResultDetail
import com.tencent.bkrepo.analyst.pojo.response.FileLicensesResultOverview
import com.tencent.bkrepo.analyst.pojo.response.FileScanResultDetail
import com.tencent.bkrepo.analyst.pojo.response.FileScanResultOverview
import com.tencent.bkrepo.analyst.pojo.response.SubtaskInfo

interface ScanTaskService {
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
     * 获取属于某个扫描任务的扫描子任务结果预览
     *
     * @param subtaskId 子扫描任务id
     *
     * @return 制品扫描结果预览信息
     */
    fun subtaskOverview(subtaskId: String): SubtaskResultOverview

    /**
     * 分页获取扫描子任务
     */
    fun subtasks(request: SubtaskInfoRequest): Page<SubtaskInfo>

    /**
     * 分页获取使用指定扫描方案扫描过的制品
     *
     * @param request 获取扫描方案关联的制品请求，包含扫描方案信息和制品筛选条件
     *
     * @return 扫描方案扫描的制品信息
     */
    fun planArtifactSubtaskPage(request: SubtaskInfoRequest): Page<SubtaskInfo>

    /**
     * 导出扫描方案记录
     */
    fun exportScanPlanRecords(request: SubtaskInfoRequest)

    /**
     * 获取属于某个扫描方案的扫描子任务结果预览
     *
     * @param subtaskId 子扫描任务id
     *
     * @return 制品扫描结果预览信息
     */
    fun planArtifactSubtaskOverview(subtaskId: String): SubtaskResultOverview


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
     * 获取属于某个扫描方案的扫描子任务扫描报告详情
     */
    fun resultDetail(request: ArtifactVulnerabilityRequest): Page<ArtifactVulnerabilityInfo>

    /**
     * 导出漏洞详情数据
     */
    fun exportLeakDetail(request: ArtifactVulnerabilityRequest)

    /**
     * 获取属于某个扫描任务的扫描子任务扫描报告详情
     */
    fun archiveSubtaskResultDetail(request: ArtifactVulnerabilityRequest): Page<ArtifactVulnerabilityInfo>

    /**
     * 获取文件扫描报告详情
     */
    fun resultDetail(request: ArtifactLicensesDetailRequest): Page<FileLicensesResultDetail>

    /**
     * 获取制品扫描结果预览
     *
     * @param projectId 制品所属项目
     * @param subScanTaskId 子扫描任务id
     *
     * @return 制品扫描结果预览信息
     */
    fun planLicensesArtifact(projectId: String, subScanTaskId: String): FileLicensesResultOverview

    /**
     * 获取属于某个扫描任务的扫描子任务许可扫描报告详情
     */
    fun archiveSubtaskResultDetail(request: ArtifactLicensesDetailRequest): Page<FileLicensesResultDetail>

    /**
     * 获取属于某个扫描任务的扫描子任务结果预览
     *
     * @param subtaskId 子扫描任务id
     *
     * @return 制品扫描结果预览信息
     */
    fun subtaskLicenseOverview(subtaskId: String): FileLicensesResultOverview
}
