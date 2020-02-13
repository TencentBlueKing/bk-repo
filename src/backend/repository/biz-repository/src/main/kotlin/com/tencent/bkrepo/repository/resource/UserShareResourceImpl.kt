package com.tencent.bkrepo.repository.resource

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactInfo
import com.tencent.bkrepo.common.artifact.permission.Permission
import com.tencent.bkrepo.common.artifact.permission.PermissionService
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.api.UserShareResource
import com.tencent.bkrepo.repository.pojo.share.BatchShareRecordCreateRequest
import com.tencent.bkrepo.repository.pojo.share.ShareRecordCreateRequest
import com.tencent.bkrepo.repository.pojo.share.ShareRecordInfo
import com.tencent.bkrepo.repository.service.ShareService
import com.tencent.bkrepo.repository.util.NodeUtils
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

    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    override fun share(userId: String, artifactInfo: ArtifactInfo, shareRecordCreateRequest: ShareRecordCreateRequest): Response<ShareRecordInfo> {
        with(artifactInfo) {
            return ResponseBuilder.success(shareService.create(userId, this, shareRecordCreateRequest))
        }
    }

    override fun batchShare(userId: String, batchShareRecordCreateRequest: BatchShareRecordCreateRequest): Response<List<ShareRecordInfo>> {
        with(batchShareRecordCreateRequest) {
            permissionService.checkPermission(userId, ResourceType.REPO, PermissionAction.WRITE, projectId, repoName)
            val shareRecordCreateRequest = ShareRecordCreateRequest(authorizedUserList, authorizedIpList, expireSeconds)
            val recordInfoList = fullPathList.map {
                val artifactInfo = DefaultArtifactInfo(projectId, repoName, NodeUtils.formatFullPath(it))
                shareService.create(userId, artifactInfo, shareRecordCreateRequest)
            }
            return ResponseBuilder.success(recordInfoList)
        }
    }

    override fun download(userId: String, token: String, artifactInfo: ArtifactInfo) {
        shareService.download(userId, token, artifactInfo)
    }
}
