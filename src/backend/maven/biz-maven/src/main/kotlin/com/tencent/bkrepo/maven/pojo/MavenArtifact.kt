package com.tencent.bkrepo.maven.pojo

import com.tencent.bkrepo.repository.pojo.node.NodeDetail

@Deprecated("MavenArtifactVersionData")
data class MavenArtifact(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val nodeInfo: NodeDetail
)
