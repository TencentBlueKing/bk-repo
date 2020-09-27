package com.tencent.bkrepo.auth.pojo.enums

enum class SystemCode {
    BK_CI,
    BK_REPO;

    fun id() = this.name.toLowerCase()
}
