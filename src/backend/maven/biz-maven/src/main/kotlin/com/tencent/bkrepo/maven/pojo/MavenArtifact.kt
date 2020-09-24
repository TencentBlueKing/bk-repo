package com.tencent.bkrepo.maven.pojo

import com.tencent.bkrepo.repository.pojo.node.NodeDetail

data class MavenArtifact(
        val groupId: String,
        val artifactId: String,
        val version: String,
        val nodeInfo: NodeDetail
)