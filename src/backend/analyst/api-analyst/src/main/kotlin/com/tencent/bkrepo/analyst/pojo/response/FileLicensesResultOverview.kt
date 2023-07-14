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

@ApiModel("制品许可扫描结果预览")
class FileLicensesResultOverview(
    @ApiModelProperty("子扫描任务id")
    val subTaskId: String,
    @ApiModelProperty("制品名")
    val name: String,
    @ApiModelProperty("packageKey")
    val packageKey: String? = null,
    @ApiModelProperty("制品版本")
    val version: String? = null,
    @ApiModelProperty("制品路径")
    val fullPath: String? = null,
    @ApiModelProperty("仓库类型")
    val repoType: String,
    @ApiModelProperty("仓库名")
    val repoName: String,

    @ApiModelProperty("高风险许可数")
    val high: Long = 0,
    @ApiModelProperty("中风险许可数")
    val medium: Long = 0,
    @ApiModelProperty("低风险许可数")
    val low: Long = 0,
    @ApiModelProperty("无风险许可数")
    val nil: Long = 0,
    @ApiModelProperty("许可总数")
    val total: Long = 0,

    @ApiModelProperty("完成时间")
    val finishTime: String?,
    @ApiModelProperty("是否通过质量规则")
    val qualityRedLine: Boolean? = null,
    @ApiModelProperty("扫描时方案的质量规则")
    val scanQuality: Map<String, Any>? = null,
    @ApiModelProperty("扫描时长")
    val duration: Long,
    @ApiModelProperty("扫描状态")
    val scanStatus: String
)
