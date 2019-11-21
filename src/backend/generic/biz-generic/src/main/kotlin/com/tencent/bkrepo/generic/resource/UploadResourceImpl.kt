package com.tencent.bkrepo.generic.resource

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactCoordinate
import com.tencent.bkrepo.common.artifact.api.ArtifactFileItem
import com.tencent.bkrepo.generic.api.UploadResource
import com.tencent.bkrepo.generic.pojo.BlockInfo
import com.tencent.bkrepo.generic.pojo.upload.UploadCompleteRequest
import com.tencent.bkrepo.generic.pojo.upload.UploadTransactionInfo
import com.tencent.bkrepo.generic.service.UploadService
import javax.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

/**
 * 通用文件上传服务接口实现类
 *
 * @author: carrypan
 * @date: 2019-09-27
 */
@RestController
class UploadResourceImpl @Autowired constructor(
    private val uploadService: UploadService
) : UploadResource {
    override fun simpleUpload(userId: String, artifactCoordinate: ArtifactCoordinate, fileItem: ArtifactFileItem, request: HttpServletRequest): Response<Void> {
        return artifactCoordinate.run {
            uploadService.simpleUpload(userId, projectId, repoName, artifactPath.fullPath, fileItem, request)
            Response.success()
        }
    }

    override fun preCheck(userId: String, artifactCoordinate: ArtifactCoordinate, request: HttpServletRequest): Response<UploadTransactionInfo> {
        return artifactCoordinate.run {
            Response.success(uploadService.preCheck(userId, projectId, repoName, artifactPath.fullPath, request))
        }
    }

    override fun blockUpload(userId: String, uploadId: String, sequence: Int, fileItem: ArtifactFileItem, request: HttpServletRequest): Response<Void> {
        uploadService.blockUpload(userId, uploadId, sequence, fileItem, request)
        return Response.success()
    }


    override fun abortUpload(userId: String, uploadId: String): Response<Void> {
        uploadService.abortUpload(userId, uploadId)
        return Response.success()
    }

    override fun completeUpload(userId: String, uploadId: String, request: UploadCompleteRequest): Response<Void> {
        uploadService.completeUpload(userId, uploadId, request.blockSha256ListStr)
        return Response.success()
    }

    override fun queryBlockInfo(userId: String, uploadId: String): Response<List<BlockInfo>> {
        return Response.success(uploadService.queryBlockInfo(userId, uploadId))
    }
}
