package com.tencent.bkrepo.docker.model

data class DockerLayer(
    var mediaType: String,
    var size: Int,
    var digest: String
)
