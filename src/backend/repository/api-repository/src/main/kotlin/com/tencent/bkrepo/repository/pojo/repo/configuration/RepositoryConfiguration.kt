package com.tencent.bkrepo.repository.pojo.repo.configuration

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 *
 * @author: carrypan
 * @date: 2019/11/26
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", defaultImpl = LocalConfiguration::class)
@JsonSubTypes(
    JsonSubTypes.Type(value = LocalConfiguration::class, name = LocalConfiguration.type),
    JsonSubTypes.Type(value = RemoteConfiguration::class, name = RemoteConfiguration.type),
    JsonSubTypes.Type(value = VirtualConfiguration::class, name = VirtualConfiguration.type)
)
abstract class RepositoryConfiguration