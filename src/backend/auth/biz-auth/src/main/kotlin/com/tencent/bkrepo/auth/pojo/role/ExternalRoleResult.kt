package com.tencent.bkrepo.auth.pojo.role

import com.tencent.bkrepo.auth.pojo.DeptInfo

data class ExternalRoleResult(
    val name: String,
    val roleId: String,
    val userList: List<String>,
    val deptInfoList: List<DeptInfo>
)
