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

package com.tencent.bkrepo.analyst.pojo

import com.tencent.bkrepo.analyst.pojo.TaskMetadata.Companion.TASK_METADATA_FILE_NAME
import com.tencent.bkrepo.common.analysis.pojo.scanner.Scanner
import com.tencent.bkrepo.common.api.constant.StringPool

/**
 * 提交给扫描执行服务的子扫描任务
 */
data class SubScanTask(
    /**
     * 子扫描任务id
     */
    val taskId: String,
    /**
     * 所属扫描任务
     */
    val parentScanTaskId: String,
    /**
     * 使用的扫描器
     */
    val scanner: Scanner,
    /**
     * 文件所属项目
     */
    val projectId: String,
    /**
     * 文件所属仓库
     */
    val repoName: String,
    /**
     * 仓库类型
     */
    val repoType: String,
    /**
     * 包名
     */
    val packageKey: String? = null,
    /**
     * 包版本
     */
    val version: String? = null,
    /**
     * 文件完整路径
     */
    val fullPath: String,
    /**
     * 文件sha256
     */
    val sha256: String,
    /**
     * 文件大小
     */
    val size: Long,
    /**
     * 包大小
     */
    val packageSize: Long,
    /**
     * 文件所在存储使用的凭据
     */
    val credentialsKey: String?,
    /**
     * 创建任务的用户
     */
    val createdBy: String,
    /**
     * 文件下载链接，容器化扫描时使用
     */
    val url: String? = null,
    /**
     * 用于容器化扫描时，状态、结果上报接口鉴权
     */
    var token: String? = null,
    /**
     * 扫描执行器需要的额外信息，用于扩展
     */
    val extra: Map<String, Any>? = null,
) {
    fun fileName() = extra?.get(TASK_METADATA_FILE_NAME)?.toString() ?: fullPath.substringAfterLast(StringPool.SLASH)

    /**
     * 拼接任务ID字符串用于日志输出，方便排查问题时通过日志追踪任务
     */
    fun trace() = "subtask[$taskId], parent[$parentScanTaskId]"
}
