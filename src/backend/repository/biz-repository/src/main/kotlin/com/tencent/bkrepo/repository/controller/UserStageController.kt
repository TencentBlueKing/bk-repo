package com.tencent.bkrepo.repository.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.pojo.stage.StageUpgradeRequest
import com.tencent.bkrepo.repository.service.StageService
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
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
    @GetMapping("/{projectId}/{repoName}/{packageKey}/{version}")
    fun query(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @PathVariable packageKey: String,
        @PathVariable version: String
    ): Response<List<String>> {
        return ResponseBuilder.success(stageService.query(projectId, repoName, packageKey, version))
    }

    @ApiOperation("制品晋级")
    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    @PostMapping("/upgrade/{projectId}/{repoName}/{packageKey}/{version}")
    fun upgrade(
        @RequestAttribute userId: String,
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @PathVariable packageKey: String,
        @PathVariable version: String,
        @RequestParam tag: String? = null
    ): Response<Void> {
        val request = StageUpgradeRequest(
            projectId = projectId,
            repoName = repoName,
            packageKey = packageKey,
            version = version,
            newTag = tag,
            operator = userId
        )
        stageService.upgrade(request)
        return ResponseBuilder.success()
    }
}
