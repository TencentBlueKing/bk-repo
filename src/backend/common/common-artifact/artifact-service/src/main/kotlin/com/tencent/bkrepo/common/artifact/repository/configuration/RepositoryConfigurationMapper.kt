package com.tencent.bkrepo.common.artifact.repository.configuration

import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.repository.constant.enums.RepositoryCategory
import com.tencent.bkrepo.repository.pojo.repo.RepositoryConfiguration

/**
 *
 * @author: carrypan
 * @date: 2019/11/27
 */
object RepositoryConfigurationMapper {

    fun readString(category: RepositoryCategory, configuration: String): RepositoryConfiguration {
        return when (category) {
            RepositoryCategory.LOCAL -> JsonUtils.objectMapper.readValue(configuration, LocalConfiguration::class.java)
            RepositoryCategory.REMOTE -> JsonUtils.objectMapper.readValue(configuration, RemoteConfiguration::class.java)
            RepositoryCategory.VIRTUAL -> JsonUtils.objectMapper.readValue(configuration, VirtualConfiguration::class.java)
        }
    }
}