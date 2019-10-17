package com.tencent.bkrepo.docker.manifest2

data class ManifestList(
    val schemaVersion: Int = 2,
    val mediaType: String,
    val manifests: List<ManifestDescriptor>
)
