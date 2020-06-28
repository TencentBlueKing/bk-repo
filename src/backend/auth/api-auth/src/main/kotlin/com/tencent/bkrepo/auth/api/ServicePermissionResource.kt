package com.tencent.bkrepo.auth.api

import com.tencent.bkrepo.auth.constant.SERVICE_NAME
import io.swagger.annotations.Api
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.RequestMapping

@Api(tags = ["SERVICE_PERMISSION"], description = "服务-权限接口")
@FeignClient(SERVICE_NAME, contextId = "ServicePermissionResource")
@RequestMapping("/permission")
interface ServicePermissionResource : PermissionResource
