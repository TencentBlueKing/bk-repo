package com.tencent.bkrepo.fs.server.model

data class NodeAttribute(
    val uid: String,
    val gid: String,
    val mode: Int? = DEFAULT_MODE,
    val flags: Int? = null,
) {
    companion object {
        const val DEFAULT_MODE = 644
        const val NOBODY = "nobody"
    }
}
