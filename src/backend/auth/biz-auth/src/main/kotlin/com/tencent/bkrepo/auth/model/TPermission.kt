package com.tencent.bkrepo.auth.model

import com.tencent.bkrepo.auth.pojo.PermissionSet
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/**
 * 角色
 */
@Document("permission")
data class TPermission(
        val id:String? = null,
        var resourceType: ResourceType,
        var projectId:String,
        var permName:String,
        var repos:List<String>? = emptyList(),
        var includePattern:List<String>? = emptyList(),
        var excludePattern:List<String>? = emptyList(),
        var createBy:String,
        val createAt:LocalDateTime,
        var updatedBy:String,
        val updateAt:LocalDateTime,
        var users: List<PermissionSet> ? = emptyList(),
        var roles :List<PermissionSet> ?= emptyList()
)
