package com.tencent.bkrepo.repository.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties("repository")
data class RepositoryProperties(
    var deletedNodeReserveDays: Long = 14,
    var defaultStorageCredentialsKey: String? = null,
    var listThreshold: Long = 1000L
)
