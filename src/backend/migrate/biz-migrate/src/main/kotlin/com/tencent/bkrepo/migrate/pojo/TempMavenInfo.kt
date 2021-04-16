package com.tencent.bkrepo.migrate.pojo

import java.io.File

data class TempMavenInfo(
    val extension: String,
    val groupId: String,
    val artifactId: String,
    val version: String,
    val jarFile: File,
    val repository: String
)
