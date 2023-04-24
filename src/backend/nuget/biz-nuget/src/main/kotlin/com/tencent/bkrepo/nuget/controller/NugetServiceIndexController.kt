package com.tencent.bkrepo.nuget.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo.Companion.NUGET_ROOT_URI_V3
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo.Companion.SERVICE_INDEX
import com.tencent.bkrepo.nuget.service.NugetServiceIndexService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(NUGET_ROOT_URI_V3)
class NugetServiceIndexController(
    private val nugetServiceIndexService: NugetServiceIndexService
) {
    /**
     * 获取服务索引 service index
     */
    @GetMapping(SERVICE_INDEX, produces = [MediaTypes.APPLICATION_JSON])
    @Permission(ResourceType.REPO, PermissionAction.READ)
    fun feed(
        artifactInfo: NugetArtifactInfo
    ): ResponseEntity<Any> {
        return ResponseEntity.ok(nugetServiceIndexService.getFeed(artifactInfo))
    }
}
