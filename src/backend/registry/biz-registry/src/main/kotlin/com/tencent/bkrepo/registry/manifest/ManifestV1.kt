package com.tencent.bkrepo.registry.manifest

data class ManifestV1(
    var schemaVersion: Int = 1,
    var mediaType: String,
    var tag: String,
    var architecture: String,
    var layers: List<FSLayer>,
    var history: List<History>
)
