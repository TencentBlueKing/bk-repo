package com.tencent.bkrepo.common.artifact.pojo.configuration

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.LocalConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.repository.RpmLocalConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.remote.RemoteConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.virtual.VirtualConfiguration

/**
 *
 * @author: carrypan
 * @date: 2019/11/26
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", defaultImpl = LocalConfiguration::class)
@JsonSubTypes(
    JsonSubTypes.Type(value = LocalConfiguration::class, name = LocalConfiguration.type),
    JsonSubTypes.Type(value = RemoteConfiguration::class, name = RemoteConfiguration.type),
    JsonSubTypes.Type(value = VirtualConfiguration::class, name = VirtualConfiguration.type),
    JsonSubTypes.Type(value = RpmLocalConfiguration::class, name = RpmLocalConfiguration.type)
)
abstract class RepositoryConfiguration
