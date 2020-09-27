package com.tencent.bkrepo.docker.model

data class DockerSchema2Config(
    var architecture: String,
    var history: List<DockerHistory>,
    var os: String
)
