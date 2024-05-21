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

package com.tencent.bkrepo.auth.controller.user

import com.tencent.bkrepo.auth.pojo.proxy.ProxyCreateRequest
import com.tencent.bkrepo.auth.pojo.proxy.ProxyInfo
import com.tencent.bkrepo.auth.pojo.proxy.ProxyListOption
import com.tencent.bkrepo.auth.pojo.proxy.ProxyUpdateRequest
import com.tencent.bkrepo.auth.service.ProxyService
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Api("Proxy管理接口")
@RestController
@RequestMapping("/api/proxy")
class ProxyController(
    private val proxyService: ProxyService
) {

    @ApiOperation("创建Proxy")
    @PostMapping("/create")
    fun create(@RequestBody request: ProxyCreateRequest): Response<ProxyInfo> {
        return ResponseBuilder.success(proxyService.create(request))
    }

    @ApiOperation("查询Proxy信息")
    @GetMapping("/info/{projectId}/{name}")
    fun info(
        @PathVariable projectId: String,
        @PathVariable name: String
    ): Response<ProxyInfo> {
        return ResponseBuilder.success(proxyService.getInfo(projectId, name))
    }

    @ApiOperation("分页查询Proxy信息")
    @GetMapping("/page/info/{projectId}")
    fun page(
        @PathVariable projectId: String,
        proxyListOption: ProxyListOption
    ): Response<Page<ProxyInfo>> {
        return ResponseBuilder.success(proxyService.page(projectId, proxyListOption))
    }

    @ApiOperation("更新Proxy")
    @PostMapping("/update")
    fun update(@RequestBody request: ProxyUpdateRequest): Response<ProxyInfo> {
        return ResponseBuilder.success(proxyService.update(request))
    }

    @ApiOperation("删除Proxy")
    @DeleteMapping("/delete/{projectId}/{name}")
    fun delete(
        @PathVariable projectId: String,
        @PathVariable name: String
    ): Response<Void> {
        proxyService.delete(projectId, name)
        return ResponseBuilder.success()
    }
}
