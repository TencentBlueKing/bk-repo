package com.tencent.bkrepo.docker.context

data class RequestContext(
    var projectId: String,
    var repoName: String,
    var artifactName: String
)
