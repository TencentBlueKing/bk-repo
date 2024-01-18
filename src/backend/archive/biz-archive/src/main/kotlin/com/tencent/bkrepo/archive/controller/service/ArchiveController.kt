package com.tencent.bkrepo.archive.controller.service

import com.tencent.bkrepo.archive.api.ArchiveClient
import com.tencent.bkrepo.archive.pojo.ArchiveFile
import com.tencent.bkrepo.archive.pojo.CompressFile
import com.tencent.bkrepo.archive.request.ArchiveFileRequest
import com.tencent.bkrepo.archive.request.CompleteCompressRequest
import com.tencent.bkrepo.archive.request.CompressFileRequest
import com.tencent.bkrepo.archive.request.CreateArchiveFileRequest
import com.tencent.bkrepo.archive.request.DeleteCompressRequest
import com.tencent.bkrepo.archive.request.UncompressFileRequest
import com.tencent.bkrepo.archive.service.ArchiveService
import com.tencent.bkrepo.archive.service.CompressService
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import org.springframework.web.bind.annotation.RestController

/**
 * 归档服务控制器
 * */
@RestController
class ArchiveController(
    private val archiveService: ArchiveService,
    private val compressService: CompressService,
) : ArchiveClient {
    override fun archive(request: CreateArchiveFileRequest): Response<Void> {
        archiveService.archive(request)
        return ResponseBuilder.success()
    }

    override fun delete(request: ArchiveFileRequest): Response<Void> {
        archiveService.delete(request)
        return ResponseBuilder.success()
    }

    override fun restore(request: ArchiveFileRequest): Response<Void> {
        archiveService.restore(request)
        return ResponseBuilder.success()
    }

    override fun get(sha256: String, storageCredentialsKey: String?): Response<ArchiveFile?> {
        return ResponseBuilder.success(archiveService.get(sha256, storageCredentialsKey))
    }

    override fun complete(request: ArchiveFileRequest): Response<Void> {
        archiveService.complete(request)
        return ResponseBuilder.success()
    }

    override fun compress(request: CompressFileRequest): Response<Void> {
        compressService.compress(request)
        return ResponseBuilder.success()
    }

    override fun uncompress(request: UncompressFileRequest): Response<Void> {
        compressService.uncompress(request)
        return ResponseBuilder.success()
    }

    override fun deleteCompress(request: DeleteCompressRequest): Response<Void> {
        compressService.delete(request)
        return ResponseBuilder.success()
    }

    override fun completeCompress(request: CompleteCompressRequest): Response<Void> {
        compressService.complete(request)
        return ResponseBuilder.success()
    }

    override fun getCompressInfo(sha256: String, storageCredentialsKey: String?): Response<CompressFile?> {
        return ResponseBuilder.success(compressService.getCompressInfo(sha256, storageCredentialsKey))
    }

    override fun deleteAll(request: ArchiveFileRequest): Response<Void> {
        archiveService.delete(request)
        val deleteCompressRequest = DeleteCompressRequest(
            sha256 = request.sha256,
            storageCredentialsKey = request.storageCredentialsKey,
            operator = request.operator,
        )
        compressService.delete(deleteCompressRequest)
        return ResponseBuilder.success()
    }
}
