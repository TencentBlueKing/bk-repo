package com.tencent.bkrepo.docker.context

data class RequestContext(
    var userId: String,
    var projectId: String,
    var repoName: String,
    var artifactName: String
)
