package com.tencent.bkrepo.repository.controller.service

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.api.FileReferenceClient
import com.tencent.bkrepo.repository.service.file.FileReferenceService
import org.springframework.web.bind.annotation.RestController

@RestController
class FileReferenceController(
    private val fileReferenceService: FileReferenceService
) : FileReferenceClient {
    override fun decrement(sha256: String, credentialsKey: String?): Response<Boolean> {
        return ResponseBuilder.success(fileReferenceService.decrement(sha256, credentialsKey))
    }

    override fun increment(sha256: String, credentialsKey: String?): Response<Boolean> {
        return ResponseBuilder.success(fileReferenceService.increment(sha256, credentialsKey))
    }

    override fun count(sha256: String, credentialsKey: String?): Response<Long> {
        return ResponseBuilder.success(fileReferenceService.count(sha256, credentialsKey))
    }
}
