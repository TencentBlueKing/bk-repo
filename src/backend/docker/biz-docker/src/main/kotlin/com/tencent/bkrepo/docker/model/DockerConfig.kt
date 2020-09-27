package com.tencent.bkrepo.docker.model

data class DockerConfig(
    var mediaType: String,
    var size: Int,
    var digest: String
)
