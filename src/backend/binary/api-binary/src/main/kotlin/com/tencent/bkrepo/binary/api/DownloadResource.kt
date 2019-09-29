package com.tencent.bkrepo.binary.api

import com.tencent.bkrepo.binary.constant.SERVICE_NAME
import com.tencent.bkrepo.binary.pojo.FileInfo
import com.tencent.bkrepo.common.api.pojo.Response
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping

/**
 * 下载接口
 *
 * @author: carrypan
 * @date: 2019-09-28
 */
@Api("下载接口")
@FeignClient(SERVICE_NAME, contextId = "DownloadResource")
@RequestMapping("/download")
interface DownloadResource {

    @ApiOperation("简单下载")
    @GetMapping("/{repositoryId}")
    fun simpleDownload(
        @ApiParam("仓库id", required = true)
        @PathVariable
        repositoryId: String,
        @ApiParam("完整路径", required = true)
        fullPath: String
    )

    @ApiOperation("查询文件信息")
    @GetMapping("/info/{repositoryId}")
    fun info(
        @ApiParam("仓库id", required = true)
        @PathVariable
        repositoryId: String,
        @ApiParam("完整路径", required = true)
        fullPath: String
    ): Response<FileInfo>

    @ApiOperation("分块下载")
    @GetMapping("/block/{repositoryId}")
    fun blockDownload(
        @ApiParam("仓库id", required = true)
        @PathVariable
        repositoryId: String,
        @ApiParam("完整路径", required = true)
        fullPath: String,
        @ApiParam("分块序号", required = true)
        sequence: Int
    ): Response<FileInfo>
}
