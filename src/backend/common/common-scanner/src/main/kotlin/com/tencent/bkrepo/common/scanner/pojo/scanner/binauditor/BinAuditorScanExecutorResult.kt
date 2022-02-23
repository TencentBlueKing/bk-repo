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

package com.tencent.bkrepo.common.scanner.pojo.scanner.binauditor

import com.tencent.bkrepo.common.scanner.pojo.scanner.ScanExecutorResult
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import java.io.File
import java.time.LocalDateTime

@ApiModel("BinAuditor扫描器扫描结果")
data class BinAuditorScanExecutorResult(
    override val startDateTime: LocalDateTime,
    override val finishedDateTime: LocalDateTime,
    @ApiModelProperty("扫描报告压缩文件")
    val resultZipFile: File,
    @ApiModelProperty("扫描结果统计信息")
    val overview: BinAuditorScanExecutorResultOverview
) : ScanExecutorResult(startDateTime, finishedDateTime, BinAuditorScanner.TYPE)

@ApiModel("扫描结果预览")
data class BinAuditorScanExecutorResultOverview(
    @ApiModelProperty("敏感信息数")
    val sensitiveUriCount: Long = 0L,
    @ApiModelProperty("敏感信息数")
    val sensitiveIpCount: Long = 0L,
    @ApiModelProperty("敏感信息数")
    val sensitiveEmailCount: Long = 0L,
    @ApiModelProperty("敏感信息数")
    val sensitivePrivateKeyCount: Long = 0L,
    @ApiModelProperty("敏感信息数")
    val sensitiveUriPasswordCount: Long = 0L,

    @ApiModelProperty("高风险开源证书数量")
    val licenseHighCount: Long = 0L,
    @ApiModelProperty("中风险开源证书数量")
    val licenseMediumCount: Long = 0L,
    @ApiModelProperty("低风险开源证书数量")
    val licenseLowCount: Long = 0L,
    @ApiModelProperty("扫描器尚未支持扫描的开源证书数量")
    val licenseNotAvailableCount: Long = 0L,

    @ApiModelProperty("严重漏洞数")
    val cveCriticalCount: Long = 0L,
    @ApiModelProperty("高危漏洞数")
    val cveHighCount: Long = 0L,
    @ApiModelProperty("高危漏洞数")
    val cveMediumCount: Long = 0L,
    @ApiModelProperty("高危漏洞数")
    val cveLowCount: Long = 0L
)
