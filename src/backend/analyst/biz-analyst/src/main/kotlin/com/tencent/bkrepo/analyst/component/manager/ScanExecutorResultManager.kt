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

package com.tencent.bkrepo.analyst.component.manager

import com.tencent.bkrepo.analyst.pojo.request.LoadResultArguments
import com.tencent.bkrepo.analyst.pojo.request.SaveResultArguments
import com.tencent.bkrepo.common.analysis.pojo.scanner.ScanExecutorResult
import com.tencent.bkrepo.common.analysis.pojo.scanner.Scanner

/**
 * 详细扫描结果管理
 */
interface ScanExecutorResultManager {
    /**
     * 保存扫描结果详情
     *
     * @param credentialsKey 被扫描文件所在存储， 为null时表示在默认存储
     * @param sha256 被扫描文件sha256
     * @param scanner 使用的扫描器
     * @param result 扫描结果详情
     * @param arguments 参数
     *
     */
    fun save(
        credentialsKey: String?,
        sha256: String,
        scanner: Scanner,
        result: ScanExecutorResult,
        arguments: SaveResultArguments? = null
    )

    /**
     * 分页获取指定类型的扫描结果详情
     *
     * @param credentialsKey 被扫描文件所在存储， 为null时表示在默认存储
     * @param sha256 被扫描文件sha256
     * @param scanner 使用的扫描器
     * @param arguments 参数
     *
     * @return 扫描结果详情
     */
    fun load(
        credentialsKey: String?,
        sha256: String,
        scanner: Scanner,
        arguments: LoadResultArguments?
    ): Any?

    /**
     * 清理结果
     *
     * @param credentialsKey 被扫描文件所在存储， 为null时表示在默认存储
     * @param sha256 被扫描文件sha256
     * @param scannerName 使用的扫描器
     *
     * @return 清理的结果数量
     */
    fun clean(
        credentialsKey: String?,
        sha256: String,
        scannerName: String,
    ): Long
}
