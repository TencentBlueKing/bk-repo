package com.tencent.bkrepo.auth.pojo

import com.tencent.bkrepo.auth.pojo.enums.ResourceType

abstract class ResourceBaseRequest(
    open var resourceType: ResourceType,
    open var projectId: String? = null,
    open var repoName: String? = null,
    open var path: String? = null
)
