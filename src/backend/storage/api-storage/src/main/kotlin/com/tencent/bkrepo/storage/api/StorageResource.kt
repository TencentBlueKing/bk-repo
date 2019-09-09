package com.tencent.bkrepo.storage.api

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.storage.constant.SERVICE_NAME
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@Api("资源存储接口")
@FeignClient(SERVICE_NAME)
interface StorageResource {

    @ApiOperation("存储资源")
    @PutMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun store(
            @ApiParam(value = "文件信息", required = true)
            @RequestPart("file")
            multipartFile: MultipartFile
    ): Response<Any>

    @ApiOperation("加载资源")
    @GetMapping("/{blockId}")
    fun load(@PathVariable blockId: String): Response<Any>


    @ApiOperation("删除资源")
    @DeleteMapping("/{blockId}")
    fun delete(@PathVariable blockId: String): Response<Any>

}