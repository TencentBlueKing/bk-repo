package com.tencent.bkrepo.auth.pojo

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import io.swagger.annotations.ApiModel

@ApiModel("校验权限请求")
data class CheckPermissionRequest(
    val uid: String,
    override var resourceType: ResourceType,
    val action: PermissionAction,
    override var projectId: String? = null,
    override var repoName: String? = null,
    override var path: String? = null,
    val role: String? = null,
    val appId: String? = null
) : ResourceBaseRequest(resourceType, projectId, repoName, path)

