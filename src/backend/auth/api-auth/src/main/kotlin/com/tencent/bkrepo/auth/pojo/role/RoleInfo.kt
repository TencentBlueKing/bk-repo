package com.tencent.bkrepo.auth.pojo.role

import com.tencent.bkrepo.auth.pojo.DeptInfo

data class RoleInfo(
    val id: String? = null,
    val roleId: String,
    val name: String,
    val type: String,
    val projectId: String? = null,
    val repoName: String? = null,
    val admin: Boolean = false,
    val users: List<String> = emptyList(),
    val description: String? = null,
    /** RoleSource 枚举名，跨模块用 String 传递 */
    val source: String? = null,
    val deptInfoList: List<DeptInfo>? = null,
)
