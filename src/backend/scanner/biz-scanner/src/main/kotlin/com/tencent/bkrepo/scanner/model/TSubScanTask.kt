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

package com.tencent.bkrepo.scanner.model

import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/**
 * 扫描子任务
 */
@Document("sub_scan_task")
@CompoundIndexes(
    CompoundIndex(
        name = "credentialsKey_sha256_idx",
        def = "{'credentialsKey': 1, 'sha256': 1}",
        background = true
    ),
    CompoundIndex(
        name = "lastModifiedDate_idx",
        def = "{'lastModifiedDate': 1}",
        background = true
    )
)
data class TSubScanTask(
    val id: String? = null,
    val createdDate: LocalDateTime,
    val lastModifiedDate: LocalDateTime,
    /**
     * 所属扫描任务
     */
    val parentScanTaskId: String,
    /**
     * 子任务状态
     */
    val status: String,
    /**
     * 使用的扫描器
     */
    val scanner: String,
    /**
     * 文件sha256
     */
    val sha256: String,
    /**
     * 文件大小
     */
    val size: Long,
    /**
     * 文件所在存储使用的凭据
     */
    val credentialsKey: String?
)
