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

package com.tencent.bkrepo.auth.api

import com.tencent.bkrepo.auth.constant.AUTH_API_KEY_PREFIX
import com.tencent.bkrepo.auth.constant.AUTH_SERVICE_KEY_PREFIX
import com.tencent.bkrepo.auth.pojo.Key
import com.tencent.bkrepo.common.api.constant.AUTH_SERVICE_NAME
import com.tencent.bkrepo.common.api.pojo.Response
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@Api(tags = ["SERVICE_KEY"], description = "服务-密钥接口")
@FeignClient(AUTH_SERVICE_NAME, contextId = "ServiceKeyResource")
@RequestMapping(AUTH_SERVICE_KEY_PREFIX, AUTH_API_KEY_PREFIX)
interface ServiceKeyResource {

    @ApiOperation("新增密钥")
    @PostMapping("/create")
    fun createKey(
        @RequestParam name: String,
        @RequestParam key: String
    ): Response<Void>

    @ApiOperation("查询公钥列表")
    @GetMapping("/list")
    fun listKey(): Response<List<Key>>

    @ApiOperation("删除公钥")
    @DeleteMapping("/delete/{id}")
    fun deleteKey(
        @PathVariable id: String
    ): Response<Void>
}
