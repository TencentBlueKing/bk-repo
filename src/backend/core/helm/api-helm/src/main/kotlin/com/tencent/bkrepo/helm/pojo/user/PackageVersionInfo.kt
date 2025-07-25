/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.helm.pojo.user

import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "npm版本详情页返回包装模型")
data class PackageVersionInfo(
    @get:Schema(title = "基础信息")
    val basic: BasicInfo,
    @get:Schema(title = "元数据信息")
    val metadata: List<MetadataModel>
)

@Schema(title = "基础信息")
data class BasicInfo(
    @get:Schema(title = "版本字段")
    val version: String,
    @get:Schema(title = "完整路径")
    val fullPath: String,
    @get:Schema(title = "文件大小，单位byte")
    val size: Long,
    @get:Schema(title = "文件sha256")
    val sha256: String,
    @get:Schema(title = "文件md5")
    val md5: String,
    @get:Schema(title = "晋级状态标签")
    val stageTag: List<String>,
    @get:Schema(title = "所属项目id")
    val projectId: String,
    @get:Schema(title = "所属仓库名称")
    val repoName: String,
    @get:Schema(title = "下载次数")
    val downloadCount: Long,
    @get:Schema(title = "创建者")
    val createdBy: String,
    @get:Schema(title = "创建时间")
    val createdDate: String,
    @get:Schema(title = "修改者")
    val lastModifiedBy: String,
    @get:Schema(title = "修改时间")
    val lastModifiedDate: String
)
