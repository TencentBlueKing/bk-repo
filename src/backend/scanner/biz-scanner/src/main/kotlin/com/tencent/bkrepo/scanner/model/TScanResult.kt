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

import com.tencent.bkrepo.common.mongo.dao.sharding.ShardingDocument
import com.tencent.bkrepo.common.mongo.dao.sharding.ShardingKey
import com.tencent.bkrepo.repository.constant.SHARDING_COUNT
import com.tencent.bkrepo.scanner.pojo.Node
import com.tencent.bkrepo.scanner.pojo.ScanResultOverview
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes

/**
 * 文件扫描结果
 */
@ShardingDocument("file_scan_result")
@CompoundIndexes(
    CompoundIndex(name = "sha256_idx", def = "{'sha256': 1}", background = true)
)
data class TFileScanResult(
    val id: String,
    /**
     * 文件sha256
     */
    @ShardingKey(count = SHARDING_COUNT)
    val sha256: String,
    /**
     * 文件使用不同扫描器的扫描结果列表
     */
    val scanResult: List<ScanResult>
)

/**
 * 扫描结果
 */
data class ScanResult(
    /**
     * 最后一次是在哪个扫描任务中扫描的
     */
    val taskId: String,
    /**
     * 文件开始扫描的时间戳
     */
    val startTime: Long,
    /**
     * 文件扫描结束的时间戳
     */
    val finishedTime: Long,
    /**
     * 扫描器
     */
    val scannerKey: String,
    /**
     * 扫描器类型
     */
    val scannerType: String,
    /**
     * 扫描器版本
     */
    val scannerVersion: String,
    /**
     * 文件扫描路径
     */
    val reportNode: Node,
    /**
     * 扫描结果统计
     */
    val overview: ScanResultOverview
)
