package com.tencent.bkrepo.common.storage.credentials

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * 存储身份信息
 *
 * @author: carrypan
 * @date: 2019-09-26
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = FileSystemCredentials::class, name = FileSystemCredentials.type),
    JsonSubTypes.Type(value = InnerCosCredentials::class, name = InnerCosCredentials.type)
)
abstract class StorageCredentials
