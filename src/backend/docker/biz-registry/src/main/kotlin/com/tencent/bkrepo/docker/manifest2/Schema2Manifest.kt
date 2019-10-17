package com.tencent.bkrepo.docker.manifest2

data class Schema2Manifest(
    val schemaVersion: Int = 2,
    val mediaType: String,
    val config: Descriptor,
    val layers: List<Descriptor>
)
