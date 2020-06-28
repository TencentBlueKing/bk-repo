package com.tencent.bkrepo.auth.api

import com.tencent.bkrepo.auth.constant.SERVICE_NAME
import io.swagger.annotations.Api
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.RequestMapping

@Api(tags = ["API_PERMISSION"], description = "用户api-权限接口")
@FeignClient(SERVICE_NAME, contextId = "ApiPermissionResource")
@RequestMapping("/api/permission")
interface ApiPermissionResource : PermissionResource
