package com.tencent.bkrepo.rpm.pojo

import com.tencent.bkrepo.common.artifact.api.ArtifactFile

data class RepomdChildNode(
    val indexType: String,
    val xmlFileSize: Int,
    val xmlGZFileSha1: String,
    val xmlGZArtifact: ArtifactFile,
    val xmlFileSha1: String
)
