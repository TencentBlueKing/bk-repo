package com.tencent.bkrepo.fs.server.model

data class NodeAttribute(
    // 用户id
    val uid: String,
    // 组id
    val gid: String,
    // 文件权限，八进制
    val mode: Int? = DEFAULT_MODE,
    // windows文件flag，十六进制
    val flags: Int? = null,
    // 设备文件设备号
    val rdev: Int? = null,
    // 文件类型
    val type: Int? = null
) {
    companion object {
        const val DEFAULT_MODE = 644
        const val NOBODY = "nobody"
    }
}
