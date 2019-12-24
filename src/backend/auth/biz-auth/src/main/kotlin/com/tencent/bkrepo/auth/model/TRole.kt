package com.tencent.bkrepo.auth.model

import com.tencent.bkrepo.auth.pojo.enums.RoleType
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document

/**
 * 角色
 */
@Document("role")
@CompoundIndexes(
    CompoundIndex(name = "roleId_idx", def = "{'roleId': 1}", background = true),
    CompoundIndex(name = "type_idx", def = "{'type': 1}", background = true),
    CompoundIndex(name = "projectId_idx", def = "{'projectId': 1}", background = true),
    CompoundIndex(name = "repoName_idx", def = "{'repoName': 1}", background = true)
)
data class TRole(
    val id: String? = null,
    val roleId: String,
    val type: RoleType,
    val name: String,
    val projectId: String,
    val repoName:String?=null,
    val admin:Boolean? = false
)
