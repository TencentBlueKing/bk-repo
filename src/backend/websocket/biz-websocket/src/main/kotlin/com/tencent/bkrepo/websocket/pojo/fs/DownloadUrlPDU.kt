package com.tencent.bkrepo.websocket.pojo.fs

data class DownloadUrlPDU(
    val url: String,
    val timestamp: Long,
    val workspaceName: String,
)
