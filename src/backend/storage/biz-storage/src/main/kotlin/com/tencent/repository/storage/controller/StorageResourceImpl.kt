package com.tencent.repository.storage.controller

import com.tencent.repository.common.api.pojo.Response
import com.tencent.repository.storage.api.StorageResource
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
class StorageResourceImpl : StorageResource {

    override fun store(multipartFile: MultipartFile): Response<Any> {
        logger.info("file name: ${multipartFile.originalFilename}")
        logger.info("file size: ${multipartFile.size}")
        return Response("success")
    }

    override fun load(blockId: String): Response<Any> {
        return Response("success")
    }

    override fun delete(blockId: String): Response<Any> {
        return Response("success")
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(StorageResourceImpl::class.java)
    }
}