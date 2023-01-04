package com.tencent.bkrepo.fs.server.model

data class NodeAttribute(
    val owner: String,
    val mode: Int
) {
    companion object {
        const val DEFAULT_MODE = 700
    }
}
