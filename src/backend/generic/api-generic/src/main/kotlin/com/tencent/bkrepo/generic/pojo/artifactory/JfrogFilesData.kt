package com.tencent.bkrepo.generic.pojo.artifactory

data class JfrogFilesData(
    val uri: String,
    val created: String,
    val files: List<JfrogFile>
)