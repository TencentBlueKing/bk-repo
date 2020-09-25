package com.tencent.bkrepo.maven.pojo

data class MavenGAVC(
    val name: String,
    val groupId: String,
    val artifactId: String,
    val version: String,
    val classifier: String?
)
