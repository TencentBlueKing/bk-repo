/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.opdata.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.metadata.annotation.LogOperate
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder.success
import com.tencent.bkrepo.opdata.pojo.gateway.GatewayHeartbeatInfo
import com.tencent.bkrepo.opdata.service.GatewayHeartbeatService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Gateway心跳管理接口
 */
@Api(tags = ["Gateway心跳管理"])
@RestController
@RequestMapping("/api/gateway/heartbeat")
@Principal(PrincipalType.ADMIN)
class GatewayHeartbeatController @Autowired constructor(
    private val gatewayHeartbeatService: GatewayHeartbeatService
) {

    /**
     * 获取所有Gateway的tag列表
     */
    @ApiOperation("获取所有Gateway的tag列表")
    @GetMapping("/tags")
    @LogOperate(type = "GATEWAY_HEARTBEAT_TAGS")
    fun listAllTags(): Response<List<String>> {
        return success(gatewayHeartbeatService.listAllTags())
    }

    /**
     * 根据tag获取Gateway列表（只返回在线的Gateway）
     */
    @ApiOperation("根据tag获取Gateway列表")
    @GetMapping("/tag/{tag}")
    @LogOperate(type = "GATEWAY_HEARTBEAT_BY_TAG")
    fun listGatewaysByTag(
        @ApiParam("Gateway标签", required = true)
        @PathVariable tag: String
    ): Response<List<GatewayHeartbeatInfo>> {
        return success(gatewayHeartbeatService.listGatewaysByTag(tag))
    }
}
