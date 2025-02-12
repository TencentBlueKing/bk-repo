/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.repository.controller.user

import com.tencent.bkrepo.auth.constant.BASIC_AUTH_HEADER_PREFIX
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.metadata.service.repo.ProxyChannelService
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.pojo.proxy.ProxyChannelInfo
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestTemplate
import java.util.Base64


@Api("代理源用户接口")
@RestController
@RequestMapping("/api/proxy-channel")
class UserProxyChannelController(
    private val proxyChannelService: ProxyChannelService
) {
    private val restTemplate = RestTemplate()

    @ApiOperation("查询代理源信息")
    @GetMapping("/{projectId}/{repoName}")
    fun getByUniqueId(
        @ApiParam(value = "所属项目", required = true)
        @PathVariable projectId: String,
        @ApiParam(value = "仓库名称", required = true)
        @PathVariable repoName: String,
        @ApiParam(value = "type", required = true)
        @RequestParam repoType: String,
        @ApiParam(value = "name", required = true)
        @RequestParam name: String
    ): Response<ProxyChannelInfo?> {
        val repoType = try {
            RepositoryType.ofValueOrDefault(repoType)
        } catch (ignored: IllegalArgumentException) {
            return ResponseBuilder.success(null)
        }
        return ResponseBuilder.success(
            proxyChannelService.queryProxyChannel(
                projectId = projectId,
                repoName = repoName,
                repoType = repoType,
                name = name
            )
        )
    }

    @PostMapping("/check")
    fun checkProxyValid(
        @RequestBody checkParam: CheckParam
    ): Response<Boolean> {
        with(checkParam) {
            val headers = HttpHeaders()
            userName?.let {
                val useInfo = userName + StringPool.COLON + password
                val authInfo = BASIC_AUTH_HEADER_PREFIX + Base64.getEncoder().encodeToString(useInfo.toByteArray())
                headers.set(HttpHeaders.AUTHORIZATION, authInfo)
            }
            val httpEntity = HttpEntity<Any>(headers)
            val response = restTemplate.exchange(
                url + "/index.yaml", HttpMethod.GET, httpEntity, String::class.java)
            if (response.statusCode != HttpStatus.OK) {
                return ResponseBuilder.success(false)
            } else {
                return ResponseBuilder.success(true)
            }
        }
    }
}

data class CheckParam(
    val url: String,
    val userName: String?,
    val password: String?
)
