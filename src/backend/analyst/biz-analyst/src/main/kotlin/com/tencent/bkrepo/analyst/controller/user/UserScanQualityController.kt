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

package com.tencent.bkrepo.analyst.controller.user

import com.tencent.bkrepo.analyst.pojo.request.ScanQualityUpdateRequest
import com.tencent.bkrepo.analyst.pojo.response.ScanQuality
import com.tencent.bkrepo.analyst.service.ScanQualityService
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "扫描方案质量规则接口")
@RestController
@RequestMapping("/api/scan/quality")
class UserScanQualityController(
    private val scanQualityService: ScanQualityService
) {

    @Operation(summary = "获取扫描方案质量规则")
    @GetMapping("/{planId}")
    fun getScanQuality(
        @Parameter(name = "方案id")
        @PathVariable("planId")
        planId: String
    ): Response<ScanQuality> {
        return ResponseBuilder.success(scanQualityService.getScanQuality(planId))
    }

    @Operation(summary = "更新扫描方案质量规则")
    @PostMapping("/{planId}")
    fun updateScanQuality(
        @Parameter(name = "方案id")
        @PathVariable("planId")
        planId: String,
        @RequestBody request: ScanQualityUpdateRequest
    ): Response<Boolean> {
        return ResponseBuilder.success(scanQualityService.updateScanQuality(planId, request))
    }
}
