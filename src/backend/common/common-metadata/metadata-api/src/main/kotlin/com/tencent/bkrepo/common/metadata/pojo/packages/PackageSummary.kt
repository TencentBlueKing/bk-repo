/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.metadata.pojo.packages

import com.tencent.bkrepo.common.metadata.pojo.metadata.MetadataModel
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

/**
 * 包总览信息
 */
@Schema(title = "包总览信息")
data class PackageSummary(
    @get:Schema(title = "id")
    val id: String,
    @get:Schema(title = "创建者")
    val createdBy: String,
    @get:Schema(title = "创建时间")
    val createdDate: LocalDateTime,
    @get:Schema(title = "修改者")
    val lastModifiedBy: String,
    @get:Schema(title = "修改时间")
    val lastModifiedDate: LocalDateTime,

    @get:Schema(title = "所属项目id")
    val projectId: String,
    @get:Schema(title = "所属仓库名称")
    val repoName: String,
    @get:Schema(title = "包名称")
    val name: String,
    @get:Schema(title = "包唯一key")
    val key: String,
    @get:Schema(title = "包类型")
    var type: PackageType,
    @get:Schema(title = "最新版名称")
    val latest: String,
    @get:Schema(title = "下载次数")
    val downloads: Long,
    @get:Schema(title = "版本数量")
    var versions: Long,
    @get:Schema(title = "包简要描述")
    var description: String? = null,
    @get:Schema(title = "版本标签")
    val versionTag: Map<String, String>,
    @get:Schema(title = "扩展字段")
    val extension: Map<String, Any>,
    @get:Schema(title = "历史版本")
    val historyVersion: Set<String>,
    @get:Schema(title = "包元数据")
    val metadata: List<MetadataModel>? = null,
)
