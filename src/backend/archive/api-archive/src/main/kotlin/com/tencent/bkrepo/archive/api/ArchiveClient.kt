package com.tencent.bkrepo.archive.api

import com.tencent.bkrepo.archive.pojo.ArchiveFile
import com.tencent.bkrepo.archive.pojo.CompressFile
import com.tencent.bkrepo.archive.request.ArchiveFileRequest
import com.tencent.bkrepo.archive.request.CompleteCompressRequest
import com.tencent.bkrepo.archive.request.CompressFileRequest
import com.tencent.bkrepo.archive.request.CreateArchiveFileRequest
import com.tencent.bkrepo.archive.request.DeleteCompressRequest
import com.tencent.bkrepo.archive.request.UncompressFileRequest
import com.tencent.bkrepo.common.api.constant.ARCHIVE_SERVICE_NAME
import com.tencent.bkrepo.common.api.pojo.Response
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

/**
 * 归档服务接口
 * */
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

    @PutMapping("/compress")
    fun compress(@RequestBody request: CompressFileRequest): Response<Void>

    @PutMapping("/uncompress")
    fun uncompress(@RequestBody request: UncompressFileRequest): Response<Void>

    @DeleteMapping("/compress")
    fun deleteCompress(@RequestBody request: DeleteCompressRequest): Response<Void>

    @PutMapping("/compress/complete")
    fun completeCompress(@RequestBody request: CompleteCompressRequest): Response<Void>

    @GetMapping("/compress")
    fun getCompressInfo(
        @RequestParam sha256: String,
        @RequestParam(required = false) storageCredentialsKey: String?,
    ): Response<CompressFile?>

    @DeleteMapping("/deleteAll")
    fun deleteAll(@RequestBody request: ArchiveFileRequest): Response<Void>
}
