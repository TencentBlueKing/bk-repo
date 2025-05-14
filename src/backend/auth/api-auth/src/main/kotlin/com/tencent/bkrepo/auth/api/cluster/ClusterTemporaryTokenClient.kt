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

package com.tencent.bkrepo.auth.api.cluster

import com.tencent.bkrepo.auth.pojo.token.TemporaryTokenCreateRequest
import com.tencent.bkrepo.auth.pojo.token.TemporaryTokenInfo
import com.tencent.bkrepo.common.api.constant.AUTH_SERVICE_NAME
import com.tencent.bkrepo.common.api.pojo.Response
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.context.annotation.Primary
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping

@Tag(name = "临时token服务接口")
@Primary
@FeignClient(AUTH_SERVICE_NAME, contextId = "ClusterTemporaryTokenResource")
@RequestMapping("/cluster/temporary/token")
interface ClusterTemporaryTokenClient {

    @Operation(summary = "创建临时token")
    @PostMapping("/create")
    fun createToken(
        @RequestBody request: TemporaryTokenCreateRequest
    ): Response<List<TemporaryTokenInfo>>

    @Operation(summary = "查询临时token信息")
    @GetMapping("/info/{token}")
    fun getTokenInfo(
        @PathVariable token: String
    ): Response<TemporaryTokenInfo?>

    @Operation(summary = "删除临时token信息")
    @DeleteMapping("/delete/{token}")
    fun deleteToken(
        @PathVariable token: String
    ): Response<Void>

    @Operation(summary = "减少token 允许访问次数")
    @PostMapping("/permits/decrement/{token}")
    fun decrementPermits(
        @PathVariable token: String
    ): Response<Void>
}
