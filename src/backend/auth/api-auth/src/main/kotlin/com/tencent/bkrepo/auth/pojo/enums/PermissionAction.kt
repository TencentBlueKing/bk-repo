package com.tencent.bkrepo.auth.pojo.enums

enum class PermissionAction {
    MANAGE,
    WRITE,
    READ,
    UPDATE,
    DELETE;

    fun id() = this.name.toLowerCase()
}
