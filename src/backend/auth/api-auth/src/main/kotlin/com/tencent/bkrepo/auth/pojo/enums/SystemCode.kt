package com.tencent.bkrepo.auth.pojo.enums

enum class SystemCode {
    BK_CI,
    BKREPO;

    fun id() = this.name.toLowerCase()
}
