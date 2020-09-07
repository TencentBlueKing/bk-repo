package com.tencent.bkrepo.repository.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactInfo.Companion.DEFAULT_MAPPING_URI
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.pojo.stage.ArtifactStageEnum
import com.tencent.bkrepo.repository.service.StageService
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 制品晋级用户接口
 */
@RestController
@RequestMapping("/api/stage")
class UserStageController(
    private val stageService: StageService
) {

    @ApiOperation("查询制品状态")
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    @GetMapping("/query$DEFAULT_MAPPING_URI")
    fun query(@ArtifactPathVariable artifactInfo: ArtifactInfo): Response<ArtifactStageEnum> {
        return ResponseBuilder.success(stageService.query(artifactInfo))
    }

    @ApiOperation("制品晋级")
    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    @PostMapping("/upgrade$DEFAULT_MAPPING_URI")
    fun upgrade(@ArtifactPathVariable artifactInfo: ArtifactInfo): Response<Void> {
        stageService.upgrade(artifactInfo)
        return ResponseBuilder.success()
    }
}
