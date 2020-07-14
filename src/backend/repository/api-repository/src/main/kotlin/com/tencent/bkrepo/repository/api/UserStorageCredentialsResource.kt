package com.tencent.bkrepo.repository.api

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.repository.pojo.credendial.StorageCredentialsCreateRequest
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping

@RequestMapping("/api/storage/credentials")
interface UserStorageCredentialsResource {

    @PostMapping
    fun create(
        @RequestAttribute userId: String,
        @RequestBody storageCredentialsCreateRequest: StorageCredentialsCreateRequest
    ): Response<Void>
}
