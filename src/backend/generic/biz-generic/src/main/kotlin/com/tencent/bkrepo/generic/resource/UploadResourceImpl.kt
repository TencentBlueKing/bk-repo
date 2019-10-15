package com.tencent.bkrepo.generic.resource

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.generic.api.UploadResource
import com.tencent.bkrepo.generic.pojo.BlockInfo
import com.tencent.bkrepo.generic.pojo.upload.BlockUploadRequest
import com.tencent.bkrepo.generic.pojo.upload.SimpleUploadRequest
import com.tencent.bkrepo.generic.pojo.upload.UploadCompleteRequest
import com.tencent.bkrepo.generic.pojo.upload.UploadPreCheckRequest
import com.tencent.bkrepo.generic.pojo.upload.UploadTransactionInfo
import com.tencent.bkrepo.generic.service.UploadService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

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

    override fun simpleUpload(userId: String, projectId: String, repoName: String, fullPath: String, file: MultipartFile, request: SimpleUploadRequest): Response<Void> {
        val path = request.fullPath ?: fullPath
        uploadService.simpleUpload(userId, projectId, repoName, path, request.sha256, request.expires, request.overwrite, file)
        return Response.success()
    }

    override fun preCheck(userId: String, projectId: String, repoName: String, fullPath: String, request: UploadPreCheckRequest): Response<UploadTransactionInfo> {
        val path = request.fullPath ?: fullPath
        return Response.success(uploadService.preCheck(userId, projectId, repoName, path, request.sha256, request.expires, request.overwrite))
    }

    override fun blockUpload(userId: String, uploadId: String, sequence: Int, file: MultipartFile, request: BlockUploadRequest): Response<Void> {
        uploadService.blockUpload(userId, uploadId, sequence, file, request.size, request.sha256)
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
