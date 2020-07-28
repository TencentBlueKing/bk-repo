package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.repository.dao.repository.StorageCredentialsRepository
import com.tencent.bkrepo.repository.model.TStorageCredentials
import com.tencent.bkrepo.repository.pojo.credendial.StorageCredentialsCreateRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class StorageCredentialService(
    private val storageCredentialsRepository: StorageCredentialsRepository,
    private val storageProperties: StorageProperties
) {
    @Transactional(rollbackFor = [Throwable::class])
    fun create(userId: String, request: StorageCredentialsCreateRequest) {
        takeIf { request.key.isNotBlank() } ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "key")
        // 目前的实现方式有个限制：新增的存储方式和默认的存储方式必须相同
        if (storageProperties.defaultStorageCredentials()::class != request.credentials::class) {
            throw throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "type")
        }
        storageCredentialsRepository.findByIdOrNull(request.key)?.run {
            throw ErrorCodeException(CommonMessageCode.RESOURCE_EXISTED, request.key)
        }
        val storageCredential = TStorageCredentials(
            id = request.key,
            createdBy = userId,
            createdDate = LocalDateTime.now(),
            lastModifiedBy = userId,
            lastModifiedDate = LocalDateTime.now(),
            credentials = request.credentials.toJsonString()
        )
        storageCredentialsRepository.save(storageCredential)
    }

    fun findByKey(key: String): StorageCredentials? {
        val tStorageCredentials = storageCredentialsRepository.findByIdOrNull(key)
        return tStorageCredentials?.credentials?.let { JsonUtils.objectMapper.readValue(it, StorageCredentials::class.java) }
    }

    fun list(): List<StorageCredentials> {
        return storageCredentialsRepository.findAll().map {
            JsonUtils.objectMapper.readValue(it.credentials, StorageCredentials::class.java)
        }
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun delete(key: String) {
        return storageCredentialsRepository.deleteById(key)
    }
}
