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

package com.tencent.bkrepo.auth.api

import com.tencent.bkrepo.auth.constant.PathConstants
import com.tencent.bkrepo.auth.pojo.oauth.AuthorizationGrantType
import com.tencent.bkrepo.common.api.constant.AUTH_SERVICE_NAME
import com.tencent.bkrepo.common.api.pojo.Response
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.context.annotation.Primary
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@Tag(name = "SERVICE_ACCOUNT", description = "服务-账号接口")
@Primary
@FeignClient(AUTH_SERVICE_NAME, contextId = "ServiceAccountResource")
@RequestMapping(PathConstants.AUTH_SERVICE_ACCOUNT_PREFIX)
interface ServiceAccountClient {

    @Operation(summary = "校验ak/sk")
    @GetMapping("/credential/{accesskey}/{secretkey}")
    @Deprecated("删除get方式校验")
    fun checkCredential(
        @Parameter(name = "accesskey")
        @PathVariable accesskey: String,
        @Parameter(name = "secretkey")
        @PathVariable secretkey: String
    ): Response<String?>

    @Operation(summary = "校验ak/sk")
    @PostMapping("/credential")
    fun checkAccountCredential(
        @Parameter(name = "accesskey")
        @RequestParam accesskey: String,
        @Parameter(name = "secretkey")
        @RequestParam secretkey: String,
        @Parameter(name = "authorizationGrantType")
        @RequestParam authorizationGrantType: AuthorizationGrantType? = null
    ): Response<String?>

    @Operation(summary = "查找sk")
    @GetMapping("/credential/appId/{appId}/accessKey/{accessKey}")
    fun findSecretKey(
        @Parameter @PathVariable appId: String,
        @Parameter @PathVariable accessKey: String
    ): Response<String?>
}
