package com.tencent.bkrepo.registry.manifest

data class Descriptor(
    val mediaType: String,
    val size: Int,
    val digest: String,
    var urls: List<String>,
    val annotations: Map<String, String>,
    val platform: Platform
)
