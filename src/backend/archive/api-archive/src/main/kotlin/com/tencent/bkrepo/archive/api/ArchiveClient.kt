package com.tencent.bkrepo.archive.api

import com.tencent.bkrepo.archive.pojo.ArchiveFile
import com.tencent.bkrepo.archive.request.ArchiveFileRequest
import com.tencent.bkrepo.archive.request.CreateArchiveFileRequest
import com.tencent.bkrepo.common.api.constant.ARCHIVE_SERVICE_NAME
import com.tencent.bkrepo.common.api.pojo.Response
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(ARCHIVE_SERVICE_NAME)
@RequestMapping("/service/archive")
interface ArchiveClient {
    @PostMapping("/archive")
    fun archive(@RequestBody request: CreateArchiveFileRequest): Response<Void>

    @DeleteMapping("/delete")
    fun delete(@RequestBody request: ArchiveFileRequest): Response<Void>

    @PostMapping("/restore")
    fun restore(@RequestBody request: ArchiveFileRequest): Response<Void>

    @PostMapping("/complete")
    fun complete(@RequestBody request: ArchiveFileRequest): Response<Void>

    @GetMapping
    fun get(
        @RequestParam sha256: String,
        @RequestParam(required = false) storageCredentialsKey: String?,
    ): Response<ArchiveFile?>
}
