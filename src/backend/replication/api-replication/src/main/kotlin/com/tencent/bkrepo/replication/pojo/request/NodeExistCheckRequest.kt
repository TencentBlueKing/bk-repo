package com.tencent.bkrepo.replication.pojo.request


data class NodeExistCheckRequest(
    val projectId: String,
    val repoName: String,
    val fullPathList: List<String>
)
