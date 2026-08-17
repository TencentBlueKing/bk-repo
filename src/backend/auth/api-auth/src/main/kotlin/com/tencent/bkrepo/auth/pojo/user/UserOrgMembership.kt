package com.tencent.bkrepo.auth.pojo.user

import com.tencent.bkrepo.auth.pojo.token.OrgScope
import io.swagger.v3.oas.annotations.media.Schema

/**
 * 用户所属组织。
 *
 * [scopes] 表示该用户当前所在组织单元，以及组织源提供的祖先组织。
 * 每条范围为自由 `(scopeType, scopeValue)`，类型语义由组织源约定。
 */
@Schema(title = "用户组织归属")
data class UserOrgMembership(
    /**
     * 用户 ID
     */
    @get:Schema(title = "用户 ID")
    val userId: String,
    /**
     * 组织范围列表：当前组织 + 祖先组织（若组织源提供）。
     */
    @get:Schema(title = "组织范围列表（当前组织及祖先）")
    val scopes: List<OrgScope> = emptyList(),
)
