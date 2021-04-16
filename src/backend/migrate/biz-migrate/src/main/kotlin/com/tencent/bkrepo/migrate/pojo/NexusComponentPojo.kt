package com.tencent.bkrepo.migrate.pojo

data class NexusPageComponent(
    val items: List<NexusComponentPojo>?,
    val continuationToken: String?
)

data class NexusPageAssets(
    val items: List<NexusAssetPojo>?,
    val continuationToken: String?
)

data class NexusComponentPojo(
    val id: String,
    val repository: String,
    val format: String,
    val group: String,
    val name: String,
    val version: String,
    val assets: List<NexusAssetPojo>
)

data class NexusAssetPojo(
    val downloadUrl: String,
    val path: String,
    val id: String?,
    val repository: String,
    val format: String?,
    val checksum: NexusChecksumPojo,
    val contentType: String?,
    val lastModified: String?,
    val maven2: NexusMaven2Pojo?
) {
    fun nick(): String {
        return downloadUrl.substringAfter("/")
    }
}

data class NexusChecksumPojo(
    val sha1: String?,
    val sha512: String?,
    val sha256: String?,
    val md5: String
)

data class NexusMaven2Pojo(
    val extension: String,
    val groupId: String,
    val artifactId: String,
    val version: String
)
