package com.tencent.bkrepo.repository.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.pojo.proxy.ProxyChannelCreateRequest
import com.tencent.bkrepo.repository.pojo.proxy.ProxyChannelInfo
import com.tencent.bkrepo.repository.service.ProxyChannelService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Api("代理源用户接口")
@RestController
@RequestMapping("/api/proxy-channel")
class UserProxyChannelController(
    private val proxyChannelService: ProxyChannelService
) {

    @ApiOperation("列表查询公有源")
    @GetMapping("/list/public/{repoType}")
    fun listPublicChannel(
        @ApiParam("仓库类型", required = true)
        @PathVariable repoType: String
    ): Response<List<ProxyChannelInfo>> {
        return ResponseBuilder.success(proxyChannelService.listPublicChannel(repoType))
    }

    @ApiOperation("创建代理源")
    @Principal(PrincipalType.ADMIN)
    @PostMapping
    fun create(
        @RequestAttribute userId: String,
        @RequestBody request: ProxyChannelCreateRequest
    ): Response<Void> {
        proxyChannelService.create(userId, request)
        return ResponseBuilder.success()
    }
}
