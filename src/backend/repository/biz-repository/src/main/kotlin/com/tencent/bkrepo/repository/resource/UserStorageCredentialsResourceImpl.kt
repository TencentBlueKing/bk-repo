package com.tencent.bkrepo.repository.resource

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.permission.Principal
import com.tencent.bkrepo.common.artifact.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.api.UserStorageCredentialsResource
import com.tencent.bkrepo.repository.pojo.credendial.StorageCredentialsCreateRequest
import com.tencent.bkrepo.repository.service.StorageCredentialService
import org.springframework.web.bind.annotation.RestController

@RestController
class UserStorageCredentialsResourceImpl(
    private val storageCredentialService: StorageCredentialService
) : UserStorageCredentialsResource {

    @Principal(PrincipalType.ADMIN)
    override fun create(userId: String, storageCredentialsCreateRequest: StorageCredentialsCreateRequest): Response<Void> {
        storageCredentialService.create(userId, storageCredentialsCreateRequest)
        return ResponseBuilder.success()
    }
}
