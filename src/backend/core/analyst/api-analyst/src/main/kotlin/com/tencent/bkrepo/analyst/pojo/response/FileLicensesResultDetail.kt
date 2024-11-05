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

package com.tencent.bkrepo.analyst.pojo.response

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("制品许可详细信息")
data class FileLicensesResultDetail(
    @ApiModelProperty("许可id")
    val licenseId: String,
    @ApiModelProperty("许可全称")
    val fullName: String,
    @ApiModelProperty("风险等级")
    val riskLevel: String?,
    @ApiModelProperty("合规性")
    val compliance: Boolean?,
    @ApiModelProperty("OSI认证")
    val isOsiApproved: Boolean?,
    @ApiModelProperty("是否 FSF 认证免费")
    val isFsfLibre: Boolean?,
    @ApiModelProperty("是否推荐使用")
    @Deprecated("兼容旧接口，前端调整后移除该字段", replaceWith = ReplaceWith("deprecated?.not()"))
    val recommended: Boolean?,
    @ApiModelProperty("是否已弃用")
    val deprecated: Boolean?,
    @ApiModelProperty("依赖路径")
    val dependentPath: String,
    @ApiModelProperty("描述")
    val description: String,
    @ApiModelProperty("所属依赖")
    val pkgName: String? = null
)
