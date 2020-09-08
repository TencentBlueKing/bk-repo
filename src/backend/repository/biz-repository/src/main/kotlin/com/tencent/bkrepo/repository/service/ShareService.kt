package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.repository.pojo.share.ShareRecordCreateRequest
import com.tencent.bkrepo.repository.pojo.share.ShareRecordInfo

/**
 * 文件分享服务
 */
interface ShareService {
    fun create(userId: String, artifactInfo: ArtifactInfo, request: ShareRecordCreateRequest): ShareRecordInfo
    fun download(userId: String, token: String, artifactInfo: ArtifactInfo)
    fun list(projectId: String, repoName: String, fullPath: String): List<ShareRecordInfo>
}
