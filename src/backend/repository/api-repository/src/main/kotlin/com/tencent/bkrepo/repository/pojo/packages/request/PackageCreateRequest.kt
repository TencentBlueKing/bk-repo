/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.repository.pojo.packages.request

import com.tencent.bkrepo.repository.pojo.packages.PackageType
import io.swagger.annotations.ApiModelProperty

data class PackageCreateRequest(
    @ApiModelProperty("项目id")
    val projectId: String,
    @ApiModelProperty("仓库名称")
    val repoName: String,
    @ApiModelProperty("包名称")
    val packageName: String,
    @ApiModelProperty("包唯一标识符")
    val packageKey: String,
    @ApiModelProperty("包类型")
    val packageType: PackageType,
    @ApiModelProperty("包简要描述")
    val packageDescription: String? = null,
    @ApiModelProperty("包扩展字段")
    val packageExtension: Map<String, Any>? = null,
    @ApiModelProperty("标签")
    val tags: List<String>? = null,
    @ApiModelProperty("是否允许覆盖")
    val overwrite: Boolean = false,
    @ApiModelProperty("创建人")
    val createdBy: String,
)
