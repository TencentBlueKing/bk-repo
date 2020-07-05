package com.tencent.bkrepo.docker.context

/**
 * docker registry request context
 * @author: owenlxu
 * @date: 2019-12-01
 */
data class RequestContext(
    var userId: String,
    var projectId: String,
    var repoName: String,
    var artifactName: String
)
