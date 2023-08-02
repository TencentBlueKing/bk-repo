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

import com.fasterxml.jackson.annotation.JsonInclude
import com.tencent.bkrepo.common.analysis.pojo.scanner.Level
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("质量规则")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ScanQuality(
    @ApiModelProperty("严重漏洞数")
    val critical: Long?,
    @ApiModelProperty("高危漏洞数")
    val high: Long?,
    @ApiModelProperty("中危漏洞数")
    val medium: Long?,
    @ApiModelProperty("低危漏洞数")
    val low: Long?,
    @ApiModelProperty("扫描未完成是否禁用制品")
    val forbidScanUnFinished: Boolean?,
    @ApiModelProperty("质量规则未通过是否禁用制品")
    val forbidQualityUnPass: Boolean?,
    @ApiModelProperty("许可是否推荐使用")
    val recommend: Boolean?,
    @ApiModelProperty("许可是否合规")
    val compliance: Boolean?,
    @ApiModelProperty("许可是否未知")
    val unknown: Boolean?
) {

    companion object {
        fun create(map: Map<String, Any>) = ScanQuality(
            critical = map[Level.CRITICAL.levelName] as? Long,
            high = map[Level.HIGH.levelName] as? Long,
            medium = map[Level.MEDIUM.levelName] as? Long,
            low = map[Level.LOW.levelName] as? Long,
            forbidScanUnFinished = map[ScanQuality::forbidScanUnFinished.name] as? Boolean,
            forbidQualityUnPass = map[ScanQuality::forbidQualityUnPass.name] as? Boolean,
            recommend = map[ScanQuality::recommend.name] as? Boolean,
            compliance = map[ScanQuality::compliance.name] as? Boolean,
            unknown = map[ScanQuality::unknown.name] as? Boolean
        )
    }
}
