package com.tencent.bkrepo.registry.manifest

data class ManifestV2(
    val schemaVersion: Int = 2,
    val mediaType: String,
    val config: Descriptor,
    val layers: List<Descriptor>
)
