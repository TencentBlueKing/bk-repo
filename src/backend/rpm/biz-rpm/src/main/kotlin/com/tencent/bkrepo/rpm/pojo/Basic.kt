package com.tencent.bkrepo.rpm.pojo

data class Basic(
    val path: String,
    val name: String,
    val version: String,
    val size: Long,
    val fullPath: String,
    val lastModifiedBy: String,
    val lastModifiedDate: String,
    val downloadCount: Long,
    val sha256: String?,
    val md5: String?,
    val stageTag: List<String>?,
    val description: String?
)
