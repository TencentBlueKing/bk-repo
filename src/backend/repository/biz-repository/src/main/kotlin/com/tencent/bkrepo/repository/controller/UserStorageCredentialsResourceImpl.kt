package com.tencent.bkrepo.repository.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.pojo.credendials.StorageCredentialsCreateRequest
import com.tencent.bkrepo.repository.service.StorageCredentialService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Principal(PrincipalType.ADMIN)
@RestController
@RequestMapping("/api/storage/credentials")
class UserStorageCredentialsResourceImpl(
    private val storageCredentialService: StorageCredentialService
) {

    @PostMapping
    fun create(
        @RequestAttribute userId: String,
        @RequestBody storageCredentialsCreateRequest: StorageCredentialsCreateRequest
    ): Response<Void> {
        storageCredentialService.create(userId, storageCredentialsCreateRequest)
        return ResponseBuilder.success()
    }
}
