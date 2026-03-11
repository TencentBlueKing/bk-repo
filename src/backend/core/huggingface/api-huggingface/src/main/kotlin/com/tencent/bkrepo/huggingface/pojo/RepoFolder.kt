package com.tencent.bkrepo.huggingface.pojo

data class RepoFolder(
    val path: String,
    val treeId: String,
    val lastCommit: LastCommitInfo?,
)
