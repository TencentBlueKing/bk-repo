package com.tencent.bkrepo.docker.model

data class DockerSchema2(
    var schemaVersion: Int,
    var mediaType: String,
    var config: DockerConfig,
    var layers: List<DockerLayer>
)
