package com.tencent.bkrepo.repository.pojo.credendial

import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.repository.pojo.UserRequest

data class StorageCredentialsCreateRequest(
    val key: String,
    val credentials: StorageCredentials
) : UserRequest
