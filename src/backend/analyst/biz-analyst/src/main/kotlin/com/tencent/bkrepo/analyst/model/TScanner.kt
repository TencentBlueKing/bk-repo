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

package com.tencent.bkrepo.analyst.model

import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.util.unit.DataSize
import java.time.LocalDateTime

@Document("scanner")
@CompoundIndexes(
    CompoundIndex(name = "name_idx", def = "{'name': 1, 'deleted': 1}", unique = true)
)
data class TScanner(
    val id: String? = null,
    val createdBy: String,
    val createdDate: LocalDateTime,
    val lastModifiedBy: String,
    val lastModifiedDate: LocalDateTime,
    val deleted: LocalDateTime? = null,

    /**
     * 扫描器名
     */
    val name: String,
    /**
     * 扫描器类型
     */
    val type: String,
    /**
     * 扫描器版本
     */
    val version: String,
    /**
     * 扫描器描述信息
     */
    val description: String = "",
    /**
     * 扫描器配置
     */
    val config: String,
    /**
     * 支持扫描的文件的文件名后缀
     */
    val supportFileNameExt: List<String> = emptyList(),
    /**
     * 支持扫描的包类型[com.tencent.bkrepo.common.artifact.pojo.RepositoryType]
     */
    val supportPackageTypes: List<String> = emptyList(),
    /**
     * 支持扫描的类型[com.tencent.bkrepo.common.analysis.pojo.scanner.ScanType]
     */
    val supportScanTypes: List<String> = emptyList(),
    /**
     * 扫描速率
     */
    val scanRate: Long? = DataSize.ofMegabytes(20).toBytes()
)
