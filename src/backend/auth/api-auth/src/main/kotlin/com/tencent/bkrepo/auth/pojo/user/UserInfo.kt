package com.tencent.bkrepo.auth.pojo.user

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(title = "分页用户信息")
data class UserInfo(
    @get:Schema(title = "用户ID")
    val userId: String,
    @get:Schema(title = "用户名")
    val name: String,
    @get:Schema(title = "邮箱")
    val email: String?,
    @get:Schema(title = "联系电话")
    val phone: String?,
    @get:Schema(title = "用户名")
    val createdDate: LocalDateTime?,
    @get:Schema(title = "用户名")
    val locked: Boolean,
    @get:Schema(title = "是否管理员")
    val admin: Boolean,
    @get:Schema(title = "是否为虚拟用户")
    val group: Boolean,
    @get:Schema(title = "关联用户")
    val asstUsers: List<String> = emptyList(),
    @get:Schema(title = "租户信息")
    val tenantId: String? = null,
)
