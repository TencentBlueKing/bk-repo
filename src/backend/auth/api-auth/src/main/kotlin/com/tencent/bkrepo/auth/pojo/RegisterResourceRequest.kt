package com.tencent.bkrepo.auth.pojo

import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("注册资源请求")
class RegisterResourceRequest(
    @ApiModelProperty("类型")
    val uid: String,
    override var resourceType: ResourceType,
    override var projectId: String? = null,
    override var repoName: String? = null,
    override var path: String? = null
) : ResourceBaseRequest(resourceType, projectId, repoName, path)
