package com.tencent.bkrepo.opdata.controller

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.metadata.pojo.sign.SignConfig
import com.tencent.bkrepo.common.metadata.pojo.sign.SignConfigCreateRequest
import com.tencent.bkrepo.common.metadata.pojo.sign.SignConfigListOption
import com.tencent.bkrepo.common.metadata.pojo.sign.SignConfigUpdateRequest
import com.tencent.bkrepo.common.metadata.service.sign.SignConfigService
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/sign/config")
@Principal(PrincipalType.ADMIN)
class SignConfigController(
    private val signConfigService: SignConfigService
) {

    @GetMapping("/list")
    fun list(
        option: SignConfigListOption
    ): Response<Page<SignConfig>> {
        return ResponseBuilder.success(signConfigService.findPage(option))
    }

    @PostMapping("/create")
    fun create(
        @RequestBody request: SignConfigCreateRequest
    ): Response<SignConfig> {
        return ResponseBuilder.success(signConfigService.create(request))
    }

    @PostMapping("/update")
    fun update(
        @RequestBody request: SignConfigUpdateRequest
    ): Response<SignConfig> {
        return ResponseBuilder.success(signConfigService.update(request))
    }

    @DeleteMapping("/delete/{projectId}")
    fun delete(
        @PathVariable projectId: String
    ): Response<Boolean> {
        return ResponseBuilder.success(signConfigService.delete(projectId))
    }
}
