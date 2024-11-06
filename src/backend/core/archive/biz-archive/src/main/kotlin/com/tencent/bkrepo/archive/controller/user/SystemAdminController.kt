package com.tencent.bkrepo.archive.controller.user

import com.tencent.bkrepo.archive.request.UpdateCompressFileStatusRequest
import com.tencent.bkrepo.archive.service.CompressService
import com.tencent.bkrepo.archive.service.SystemAdminService
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Principal(PrincipalType.ADMIN)
@RestController
@RequestMapping("/api/archive/admin")
class SystemAdminController(
    private val systemAdminService: SystemAdminService,
    private val compressService: CompressService,
) {
    @PutMapping("/stop")
    fun stop() {
        systemAdminService.stop()
    }

    @PostMapping("/compress/update")
    fun updateCompressFileStatus(@RequestBody request: UpdateCompressFileStatusRequest) {
        compressService.updateStatus(request)
    }
}
