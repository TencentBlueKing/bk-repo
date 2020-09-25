package com.tencent.bkrepo.auth.api

import com.tencent.bk.sdk.iam.dto.callback.request.CallbackRequestDTO
import com.tencent.bk.sdk.iam.dto.callback.response.CallbackBaseResponseDTO
import com.tencent.bkrepo.auth.constant.AUTHORIZATION
import com.tencent.bkrepo.auth.constant.SERVICE_NAME
import com.tencent.bkrepo.auth.pojo.bkiam.BkResult
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping

@Api(tags = ["BKIAM_USER"], description = "蓝鲸权限中心回调接口")
@FeignClient(SERVICE_NAME, contextId = "BkiamUserResource")
@RequestMapping("/external/bkiam/callback")
interface BkiamCallbackResource {

    @ApiOperation("项目列表")
    @PostMapping("/project")
    fun queryProject(
        @RequestHeader(AUTHORIZATION) token: String,
        @RequestBody request: CallbackRequestDTO
    ): CallbackBaseResponseDTO?

    @ApiOperation("仓库列表")
    @PostMapping("/repo")
    fun queryRepo(
        @RequestHeader(AUTHORIZATION) token: String,
        @RequestBody request: CallbackRequestDTO
    ): CallbackBaseResponseDTO?

    @ApiOperation("健康检查")
    @GetMapping("/health")
    fun health(): BkResult<Boolean>
}
