package com.tencent.bkrepo.auth.service.bkiam

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.pojo.enums.SystemCode

interface BkiamService {
    fun validateResourcePermission(
        userId: String,
        systemCode: SystemCode,
        projectId: String,
        resourceType: ResourceType,
        action: PermissionAction,
        resourceId: String
    ): Boolean

    fun listResourceByPermission(
        userId: String,
        systemCode: SystemCode,
        projectId: String,
        resourceType: ResourceType,
        action: PermissionAction
    ): List<String>

    fun createResource(
        userId: String,
        systemCode: SystemCode,
        projectId: String,
        resourceType: ResourceType,
        resourceId: String,
        resourceName: String
    )
}
