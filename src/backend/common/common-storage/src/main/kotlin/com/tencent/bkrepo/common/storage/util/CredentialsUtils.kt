package com.tencent.bkrepo.common.storage.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.tencent.bkrepo.common.storage.core.ClientCredentials
import com.tencent.bkrepo.common.storage.core.StorageTypeEnum
import com.tencent.bkrepo.common.storage.innercos.InnerCosCredentials
import com.tencent.bkrepo.common.storage.local.LocalStorageCredentials

object CredentialsUtils {

    private val objectMapper = ObjectMapper()

    fun readString(type: String?, credentialsStr: String?): ClientCredentials? {
        if (type.isNullOrEmpty() || credentialsStr.isNullOrEmpty()) {
            return null
        }
        return when (StorageTypeEnum.getEnum(type)) {
            StorageTypeEnum.LOCAL -> objectMapper.readValue(credentialsStr, LocalStorageCredentials::class.java)
            StorageTypeEnum.INNER_COS -> objectMapper.readValue(credentialsStr, InnerCosCredentials::class.java)
            else -> null
        }
    }

    fun writeString(clientCredentials: ClientCredentials): String {
        return objectMapper.writeValueAsString(clientCredentials)
    }
}
