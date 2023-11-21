package com.tencent.bkrepo.archive.controller.service

import com.tencent.bkrepo.archive.api.ArchiveClient
import com.tencent.bkrepo.archive.pojo.ArchiveFile
import com.tencent.bkrepo.archive.request.ArchiveFileRequest
import com.tencent.bkrepo.archive.request.CreateArchiveFileRequest
import com.tencent.bkrepo.archive.service.ArchiveService
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import org.springframework.web.bind.annotation.RestController

@RestController
class ArchiveController(
    private val archiveService: ArchiveService,
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
}
