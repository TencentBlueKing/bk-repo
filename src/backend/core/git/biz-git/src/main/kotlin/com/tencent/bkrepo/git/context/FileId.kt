package com.tencent.bkrepo.git.context

data class FileId(
    val projectId: String,
    val repoName: String,
    val fileName: String
)
