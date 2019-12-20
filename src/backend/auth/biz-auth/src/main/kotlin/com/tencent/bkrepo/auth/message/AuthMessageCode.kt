package com.tencent.bkrepo.auth.message

import com.tencent.bkrepo.common.api.message.MessageCode

enum class AuthMessageCode(private val businessCode: Int, private val key: String) : MessageCode {

    AUTH_DUP_UID(1, "auth.dup.uid"),
    AUTH_USER_NOT_EXIST(2, "auth.user.notexist"),
    AUTH_DELETE_USER_FAILED(3, "auth.delete.user.failed"),
    AUTH_USER_TOKEN_ERROR(4, "auth.user.token.error"),
    AUTH_ROLE_NOT_EXIST(5, "auth.role.notexist"),
    AUTH_DUP_RID(6, "auth.dup.rid"),
    AUTH_DUP_PERMNAME(7, "auth.dup.permname"),
    AUTH_PERMISSION_NOT_EXIST(8, "auth.permission.notexist"),
    AUTH_PERMISSION_FAILED(9, "auth.permission.failed"),
    AUTH_USER_PERMISSION_EXIST(10, "auth.user.permission-exist"),
    AUTH_ROLE_PERMISSION_EXIST(11, "auth.role.permission-exist"),
    AUTH_DUP_APPID(12, "auth.dup.appid"),
    AUTH_APPID_NOT_EXIST(13, "auth.appid.notexist");

    override fun getBusinessCode() = businessCode
    override fun getKey() = key
    override fun getModuleCode() = 2
}
