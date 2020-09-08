package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.repository.pojo.credendial.StorageCredentialsCreateRequest

/**
 * 存储凭证服务
 */
interface StorageCredentialService {
    fun create(userId: String, request: StorageCredentialsCreateRequest)
    fun findByKey(key: String): StorageCredentials?
    fun list(): List<StorageCredentials>
    fun delete(key: String)
}
