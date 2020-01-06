package com.tencent.bkrepo.auth.model

import com.tencent.bkrepo.auth.pojo.PermissionSet
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import java.time.LocalDateTime
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document

/**
 * 角色
 */
@Document("permission")
@CompoundIndexes(
    CompoundIndex(name = "repos_idx", def = "{'repos': 1}", background = true),
    CompoundIndex(name = "resourceType_idx", def = "{'resourceType': 1}", background = true),
    CompoundIndex(name = "projectId_idx", def = "{'projectId': 1}", background = true),
    CompoundIndex(name = "includePattern_idx", def = "{'includePattern': 1}", background = true),
    CompoundIndex(name = "excludePattern_idx", def = "{'excludePattern': 1}", background = true),
    CompoundIndex(name = "users_id_idx", def = "{'users.id': 1}", background = true),
    CompoundIndex(name = "users_action_idx", def = "{'users.action': 1}", background = true),
    CompoundIndex(name = "roles_id_idx", def = "{'roles.id': 1}", background = true),
    CompoundIndex(name = "roles_action_idx", def = "{'roles.action': 1}", background = true)
)
data class TPermission(
    val id: String? = null,
    var resourceType: ResourceType,
    var projectId: String? = null,
    var permName: String,
    var repos: List<String>? = emptyList(),
    var includePattern: List<String>? = emptyList(),
    var excludePattern: List<String>? = emptyList(),
    var createBy: String,
    val createAt: LocalDateTime,
    var updatedBy: String,
    val updateAt: LocalDateTime,
    var users: List<PermissionSet>? = emptyList(),
    var roles: List<PermissionSet>? = emptyList()
)
