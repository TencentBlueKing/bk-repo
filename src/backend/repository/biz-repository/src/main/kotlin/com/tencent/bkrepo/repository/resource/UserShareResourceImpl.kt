package com.tencent.bkrepo.repository.resource

import com.tencent.bkrepo.auth.pojo.CheckPermissionRequest
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.auth.PermissionService
import com.tencent.bkrepo.repository.api.UserShareResource
import com.tencent.bkrepo.repository.pojo.share.ShareRecordCreateRequest
import com.tencent.bkrepo.repository.pojo.share.ShareRecordInfo
import com.tencent.bkrepo.repository.service.ShareService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

/**
 * 用户节点接口实现类
 *
 * @author: carrypan
 * @date: 2019/11/19
 */
@RestController
class UserShareResourceImpl @Autowired constructor(
    private val permissionService: PermissionService,
    private val shareService: ShareService
) : UserShareResource {

    override fun share(userId: String, artifactInfo: ArtifactInfo, shareRecordCreateRequest: ShareRecordCreateRequest): Response<ShareRecordInfo> {
        with(artifactInfo) {
            permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.READ, projectId, repoName))
            return Response.success(shareService.create(userId, this, shareRecordCreateRequest))
        }
    }

    override fun download(userId: String, token: String, artifactInfo: ArtifactInfo) {
        shareService.download(userId, token, artifactInfo)
    }
}
