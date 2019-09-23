package com.tencent.bkrepo.registry.manifest

data class ManifestList(
    val schemaVersion: Int = 2,
    val mediaType: String,
    val manifests: List<ManifestDescriptor>
)
