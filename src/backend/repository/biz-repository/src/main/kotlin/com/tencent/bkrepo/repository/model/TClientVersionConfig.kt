package com.tencent.bkrepo.repository.model

import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document("client_version_config")
@CompoundIndexes(
    CompoundIndex(
        name = "client_version_config_key_unique",
        def = "{'productId': 1, 'platform': 1, 'arch': 1, 'targetUserId': 1}",
        unique = true,
        background = true,
    ),
    CompoundIndex(
        name = "client_version_config_product_idx",
        def = "{'productId': 1}",
        background = true,
    ),
)
data class TClientVersionConfig(
    var id: String? = null,
    var createdBy: String,
    var createdDate: LocalDateTime,
    var lastModifiedBy: String,
    var lastModifiedDate: LocalDateTime,
    var productId: String,
    var platform: String,
    var arch: String,
    var targetUserId: String? = null,
    var minVersion: String? = null,
    var latestVersion: String,
    var downloadUrl: String,
    var releaseNotes: String? = null,
    var enabled: Boolean = true,
)
