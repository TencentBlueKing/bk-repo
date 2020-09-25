package com.tencent.bkrepo.maven.pojo

data class MavenGAVC(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val classifier: String?
)
