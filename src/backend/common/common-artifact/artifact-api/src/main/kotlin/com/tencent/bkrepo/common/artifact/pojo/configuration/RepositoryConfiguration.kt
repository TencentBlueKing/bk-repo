package com.tencent.bkrepo.common.artifact.pojo.configuration

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.tencent.bkrepo.common.artifact.pojo.configuration.composite.CompositeConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.LocalConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.repository.RpmLocalConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.remote.RemoteConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.virtual.VirtualConfiguration
import io.swagger.annotations.ApiModelProperty

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", defaultImpl = LocalConfiguration::class)
@JsonSubTypes(
    JsonSubTypes.Type(value = LocalConfiguration::class, name = LocalConfiguration.type),
    JsonSubTypes.Type(value = RemoteConfiguration::class, name = RemoteConfiguration.type),
    JsonSubTypes.Type(value = VirtualConfiguration::class, name = VirtualConfiguration.type),
    JsonSubTypes.Type(value = CompositeConfiguration::class, name = CompositeConfiguration.type),
    JsonSubTypes.Type(value = RpmLocalConfiguration::class, name = RpmLocalConfiguration.type)
)
abstract class RepositoryConfiguration {
    /**
     * 设置项
     * 不同类型仓库可以通过该字段进行差异化配置
     */
    @ApiModelProperty("设置项", required = false)
    val settings: Map<String, Any> = emptyMap()

    /**
     * 根据属性名[key]获取自定义context属性
     */
    @JsonIgnore
    inline fun <reified T> getSetting(key: String): T? {
        return settings[key] as T?
    }

    /**
     * 根据属性名[key]获取字符串类型设置项
     */
    @JsonIgnore
    fun getSetting(key: String): String? {
        return settings[key]?.toString()
    }

    /**
     * 根据属性名[key]获取字符串类型设置项
     */
    @JsonIgnore
    fun getStringSetting(key: String): String? {
        return settings[key]?.toString()
    }

    /**
     * 根据属性名[key]获取Boolean类型设置项
     */
    @JsonIgnore
    fun getBooleanSetting(key: String): Boolean? {
        return settings[key]?.toString()?.toBoolean()
    }

    /**
     * 获取整数类型设置项
     */
    @JsonIgnore
    fun getIntegerSetting(key: String): Int? {
        return settings[key]?.toString()?.toInt()
    }
}
