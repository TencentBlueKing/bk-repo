package com.tencent.bkrepo.auth.pojo.user

/** 联邦同步专用：包含完整用户信息（含 hashedPwd），避免逐个调用 userInfoById + userPwdById */
data class UserFederationInfo(
    val userId: String,
    val name: String,
    val hashedPwd: String?,
    val admin: Boolean,
    val group: Boolean,
    val asstUsers: List<String>,
    val email: String?,
    val phone: String?,
    val tenantId: String?,
)
