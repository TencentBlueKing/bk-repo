package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.repository.pojo.share.ShareRecordCreateRequest
import com.tencent.bkrepo.repository.pojo.share.ShareRecordInfo

/**
 * 文件分享服务接口
 */
interface ShareService {

    /**
     * 创建分享连接
     */
    fun create(userId: String, artifactInfo: ArtifactInfo, request: ShareRecordCreateRequest): ShareRecordInfo

    /**
     * 下载分享文件
     */
    fun download(userId: String, token: String, artifactInfo: ArtifactInfo)

    /**
     * 查询节点被创建的分享链接列表
     */
    fun list(projectId: String, repoName: String, fullPath: String): List<ShareRecordInfo>
}
