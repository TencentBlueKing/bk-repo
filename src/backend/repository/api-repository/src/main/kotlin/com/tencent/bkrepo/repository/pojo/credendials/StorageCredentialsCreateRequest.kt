package com.tencent.bkrepo.repository.pojo.credendials

import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.repository.pojo.UserRequest

data class StorageCredentialsCreateRequest(
    val key: String,
    val credentials: StorageCredentials
) : UserRequest
