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

package com.tencent.bkrepo.analysis.executor.api

import com.tencent.bkrepo.analyst.pojo.SubScanTask
import com.tencent.bkrepo.common.api.constant.ANALYSIS_EXECUTOR_SERVICE_NAME
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(ANALYSIS_EXECUTOR_SERVICE_NAME, contextId = "ExecutorClient")
@RequestMapping("/service/executor")
interface ExecutorClient {
    /**
     * 执行扫描任务
     * @param subtask 扫描任务
     * @return 是否成功提交执行
     */
    @PostMapping("/execute")
    fun execute(@RequestBody subtask: SubScanTask): Boolean

    /**
     * 执行扫描任务，执行器会通过[subtaskId]到analysis拉取任务详情，同时到generic服务拉取制品
     * @param subtaskId 扫描任务id
     * @param token 用于更新
     * @return 是否成功执行
     */
    @PostMapping("/dispatch")
    fun dispatch(@RequestParam subtaskId: String, @RequestParam token: String): Boolean

    /**
     * 停止扫描任务
     * @param subtaskId 扫描任务id
     * @return 是否成功停止
     */
    @PostMapping("/stop")
    fun stop(@RequestParam subtaskId: String): Boolean
}
