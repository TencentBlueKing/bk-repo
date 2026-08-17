package com.tencent.bkrepo.auth.pojo.token

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 通用组织授权范围：自由 `(scopeType, scopeValue)`，由对接方约定 type 语义。
 */
@Schema(title = "组织授权范围")
data class OrgScope(
    @get:Schema(title = "范围类型（自由字符串，由组织源约定）")
    val scopeType: String,
    @get:Schema(title = "范围值（组织单元 ID 等）")
    val scopeValue: String,
    @get:Schema(title = "展示名称")
    val scopeName: String? = null,
)
