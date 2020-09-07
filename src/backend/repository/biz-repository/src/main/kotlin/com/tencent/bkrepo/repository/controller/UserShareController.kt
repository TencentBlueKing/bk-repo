package com.tencent.bkrepo.repository.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactInfo
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactInfo.Companion.DEFAULT_MAPPING_URI
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.pojo.share.BatchShareRecordCreateRequest
import com.tencent.bkrepo.repository.pojo.share.ShareRecordCreateRequest
import com.tencent.bkrepo.repository.pojo.share.ShareRecordInfo
import com.tencent.bkrepo.repository.service.ShareService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 用户分享接口
 */
@Api("节点分享用户接口")
@RestController
@RequestMapping("/api/share")
class UserShareController(
    private val permissionManager: PermissionManager,
    private val shareService: ShareService
) {

    @ApiOperation("创建分享链接")
    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    @PostMapping(DEFAULT_MAPPING_URI)
    fun share(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo,
        @RequestBody shareRecordCreateRequest: ShareRecordCreateRequest
    ): Response<ShareRecordInfo> {
        return ResponseBuilder.success(shareService.create(userId, artifactInfo, shareRecordCreateRequest))
    }

    @ApiOperation("批量创建分享链接")
    @PostMapping("/batch")
    fun batchShare(
        @RequestAttribute userId: String,
        @RequestBody batchShareRecordCreateRequest: BatchShareRecordCreateRequest
    ): Response<List<ShareRecordInfo>> {
        with(batchShareRecordCreateRequest) {
            permissionManager.checkPermission(userId, ResourceType.REPO, PermissionAction.WRITE, projectId, repoName)
            val shareRecordCreateRequest = ShareRecordCreateRequest(authorizedUserList, authorizedIpList, expireSeconds)
            val recordInfoList = fullPathList.map {
                val artifactInfo = DefaultArtifactInfo(projectId, repoName, PathUtils.normalizeFullPath(it))
                shareService.create(userId, artifactInfo, shareRecordCreateRequest)
            }
            return ResponseBuilder.success(recordInfoList)
        }
    }

    @ApiOperation("下载分享文件")
    @GetMapping(DEFAULT_MAPPING_URI)
    fun download(
        @RequestAttribute userId: String,
        @RequestParam token: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo
    ) {
        shareService.download(userId, token, artifactInfo)
    }
}
