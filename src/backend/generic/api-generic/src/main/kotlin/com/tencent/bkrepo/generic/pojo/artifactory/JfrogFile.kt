package com.tencent.bkrepo.generic.pojo.artifactory

data class JfrogFile(
    val uri: String,
    val size: Long,
    val lastModified: String,
    val folder: Boolean,
    val sha1: String = ""
)