package com.tencent.bkrepo.maven.pojo

data class Basic(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val size: Long,
    val fullPath: String,
    val lastModifiedBy: String,
    val lastModifiedDate: String,
    val downloadCount: Int,
    val sha256: String?,
    val md5: String?,
    val stageTag: List<String>?,
    val description: String?
)
