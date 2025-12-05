package com.tencent.bkrepo.huggingface.pojo

data class RepoFile(
    val path: String,
    val size: Long,
    val blobId: String,
    val lfs: BlobLfsInfo?,
    val lastCommit: LastCommitInfo?,
    val security: BlobSecurityInfo?,
)
