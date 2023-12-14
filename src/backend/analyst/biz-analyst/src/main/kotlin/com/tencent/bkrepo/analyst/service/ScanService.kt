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
import com.tencent.bkrepo.analyst.pojo.ScanTriggerType
import com.tencent.bkrepo.analyst.pojo.SubScanTask
import com.tencent.bkrepo.analyst.pojo.request.GlobalScanRequest
import com.tencent.bkrepo.analyst.pojo.request.PipelineScanRequest
import com.tencent.bkrepo.analyst.pojo.request.ReportResultRequest
import com.tencent.bkrepo.analyst.pojo.request.ScanRequest

/**
 * 扫描服务
 */
interface ScanService {
    /**
     * 触发全局扫描
     *
     * @param request 全局扫描请求
     *
     * @return 创建的扫描任务
     */
    fun globalScan(request: GlobalScanRequest): ScanTask

    /**
     * 创建扫描任务，启动扫描
     *
     * @param scanRequest 扫描参数，指定使用的扫描器和需要扫描的文件
     * @param triggerType 触发类型
     * @param userId 用户id，传入null时表示系统触发扫描，不校验用户权限
     */
    fun scan(scanRequest: ScanRequest, triggerType: ScanTriggerType, userId: String): ScanTask

    /**
     * 从流水线创建扫描任务
     */
    fun pipelineScan(pipelineScanRequest: PipelineScanRequest): ScanTask

    /**
     * 停止子任务
     *
     * @param projectId 项目id
     * @param subtaskId 使用特定扫描方案的制品最新一次扫描记录id
     *
     * @return true 停止成功，false 停止失败
     */
    fun stopByPlanArtifactLatestSubtaskId(projectId: String, subtaskId: String): Boolean

    /**
     * 停止方案下所有子任务
     *
     * @param projectId 项目id
     * @param planId 方案id
     *
     * @return true 停止成功，false 停止失败
     */
    fun stopScanPlan(projectId: String, planId: String): Boolean

    /**
     * 停止子任务
     *
     * @param projectId 项目id
     * @param subtaskId 子任务id
     *
     * @return true 停止成功，false 停止失败
     */
    fun stopSubtask(projectId: String, subtaskId: String): Boolean

    /**
     * 停止任务
     *
     * @param projectId 项目id
     * @param taskId 子任务id
     *
     * @return true 停止成功，false 停止失败
     */
    fun stopTask(projectId: String?, taskId: String): Boolean

    /**
     * 扫描结果上报
     *
     * @param reportResultRequest 扫描结果上报请求
     */
    fun reportResult(reportResultRequest: ReportResultRequest)

    /**
     * 更新子扫描任务状态
     *
     * @param subScanTaskId 子任务id
     * @param subScanTaskStatus 要更新成哪个状态
     *
     * @return 是否更新成功
     */
    fun updateSubScanTaskStatus(subScanTaskId: String, subScanTaskStatus: String): Boolean

    /**
     * 记录任务心跳
     */
    fun heartbeat(subScanTaskId: String)

    /**
     * 拉取子任务
     * @param dispatcher 指定子任务分发器
     *
     * @return 没有可执行的任务时返回null，否则返回一个待执行的任务
     */
    fun pull(dispatcher: String? = null): SubScanTask?

    /**
     * 仅查询出子任务，不改变子任务状态
     *
     * @param dispatcher 指定子任务分发器
     *
     * @return 没有可执行的任务时返回null，否则返回一个待执行的任务
     */
    fun peek(dispatcher: String? = null): SubScanTask?

    /**
     * 获取扫描任务
     *
     * @param subtaskId 任务id
     *
     * @return 扫描任务
     */
    fun get(subtaskId: String): SubScanTask
}
