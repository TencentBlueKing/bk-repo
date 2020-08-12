package com.tencent.bkrepo.repository.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 *
 * @author: carrypan
 * @date: 2020/1/2
 */
@Configuration
@ConfigurationProperties("repository")
data class RepositoryProperties(
    var deletedNodeReserveDays: Long = 14,
    var defaultStorageCredentialsKey: String? = null
)
