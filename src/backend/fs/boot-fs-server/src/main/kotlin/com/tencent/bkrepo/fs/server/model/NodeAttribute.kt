package com.tencent.bkrepo.fs.server.model

data class NodeAttribute(
    val uid: String,
    val gid: String,
    val mode: Int
) {
    companion object {
        const val DEFAULT_MODE = 644
        const val NOBODY = "nobody"
    }
}
