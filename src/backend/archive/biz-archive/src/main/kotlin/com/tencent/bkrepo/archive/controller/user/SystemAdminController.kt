package com.tencent.bkrepo.archive.controller.user

import com.tencent.bkrepo.archive.service.SystemAdminService
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Principal(PrincipalType.ADMIN)
@RestController
@RequestMapping("/api/archive/admin")
class SystemAdminController(
    private val systemAdminService: SystemAdminService,
) {
    @PutMapping("/stop")
    fun stop(@RequestParam jobName: String) {
        systemAdminService.stop(jobName)
    }
}
