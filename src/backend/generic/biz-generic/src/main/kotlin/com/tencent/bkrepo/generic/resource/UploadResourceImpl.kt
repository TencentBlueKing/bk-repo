package com.tencent.bkrepo.generic.resource

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.generic.api.UploadResource
import com.tencent.bkrepo.generic.artifact.GenericArtifactInfo
import com.tencent.bkrepo.generic.pojo.BlockInfo
import com.tencent.bkrepo.generic.pojo.UploadTransactionInfo
import com.tencent.bkrepo.generic.service.UploadService
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

    override fun upload(artifactInfo: GenericArtifactInfo, file: ArtifactFile){
        uploadService.upload(artifactInfo, file)
    }

    override fun startBlockUpload(userId: String, artifactInfo: GenericArtifactInfo): Response<UploadTransactionInfo> {
        return ResponseBuilder.success(uploadService.startBlockUpload(userId, artifactInfo))
    }

    override fun abortBlockUpload(userId: String, uploadId: String, artifactInfo: GenericArtifactInfo): Response<Void> {
        uploadService.abortBlockUpload(userId, uploadId, artifactInfo)
        return ResponseBuilder.success()
    }

    override fun completeBlockUpload(userId: String, uploadId: String, artifactInfo: GenericArtifactInfo): Response<Void> {
        uploadService.completeBlockUpload(userId, uploadId, artifactInfo)
        return ResponseBuilder.success()
    }

    override fun listBlock(userId: String, uploadId: String, artifactInfo: GenericArtifactInfo): Response<List<BlockInfo>> {
        return ResponseBuilder.success(uploadService.listBlock(userId, uploadId, artifactInfo))
    }
}
