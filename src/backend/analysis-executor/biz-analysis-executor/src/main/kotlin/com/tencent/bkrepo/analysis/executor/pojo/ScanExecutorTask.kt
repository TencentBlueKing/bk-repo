/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.analysis.executor.pojo

import com.tencent.bkrepo.common.analysis.pojo.scanner.Scanner
import java.io.File

/**
 * 扫描执行器扫描任务
 */
data class ScanExecutorTask(
    /**
     * 任务id
     */
    val taskId: String,
    /**
     * 所属扫描父任务id
     */
    val parentTaskId: String,
    /**
     * 扫描执行器
     */
    val scanner: Scanner,
    /**
     * 包名
     */
    val packageKey: String? = null,
    /**
     * 包版本
     */
    val packageVersion: String? = null,
    /**
     * 待扫描文件
     */
    val file: File,
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
     * 待扫描文件sha256
     */
    val sha256: String,
    /**
     * 扫描执行器需要的额外信息，用于扩展
     */
    val extra: Map<String, Any>? = null
)
