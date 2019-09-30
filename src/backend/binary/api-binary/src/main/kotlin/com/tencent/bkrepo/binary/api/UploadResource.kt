package com.tencent.bkrepo.binary.api

import com.tencent.bkrepo.binary.pojo.BlockInfo
import com.tencent.bkrepo.binary.pojo.BlockUploadRequest
import com.tencent.bkrepo.binary.pojo.SimpleUploadRequest
import com.tencent.bkrepo.binary.pojo.UploadCompleteRequest
import com.tencent.bkrepo.binary.pojo.UploadPreCheckRequest
import com.tencent.bkrepo.binary.pojo.UploadTransactionInfo
import com.tencent.bkrepo.common.api.constant.AUTH_HEADER_USER_ID
import com.tencent.bkrepo.common.api.constant.AUTH_HEADER_USER_ID_DEFAULT_VALUE
import com.tencent.bkrepo.common.api.pojo.Response
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.multipart.MultipartFile

/**
 * 上传接口
 *
 * @author: carrypan
 * @date: 2019-09-27
 */
@Api("上传接口")
@RequestMapping("/upload")
interface UploadResource {

    @ApiOperation("简单上传")
    @PostMapping("/simple")
    fun simpleUpload(
        @ApiParam(value = "用户id", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @RequestHeader(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("文件", required = true)
        file: MultipartFile,
        request: SimpleUploadRequest
    ): Response<Void>

    @ApiOperation("分块上传预检")
    @GetMapping("/precheck")
    fun preCheck(
        @ApiParam(value = "用户id", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @RequestHeader(AUTH_HEADER_USER_ID)
        userId: String,
        @RequestBody
        request: UploadPreCheckRequest
    ): Response<UploadTransactionInfo>

    @ApiOperation("分块上传")
    @PostMapping("/block")
    fun blockUpload(
        @ApiParam(value = "用户id", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @RequestHeader(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("文件", required = true)
        file: MultipartFile,
        request: BlockUploadRequest
    ): Response<Void>

    @ApiOperation("取消分块上传")
    @PostMapping("/abort/{uploadId}")
    fun abortUpload(
        @ApiParam(value = "用户id", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @RequestHeader(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("上传事物id", required = true)
        @PathVariable
        uploadId: String
    ): Response<Void>

    @ApiOperation("完成分块上传")
    @PostMapping("/complete/{uploadId}")
    fun completeUpload(
        @ApiParam(value = "用户id", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @RequestHeader(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("上传事物id", required = true)
        @PathVariable
        uploadId: String,
        @ApiParam("上传完成请求", required = true)
        @RequestBody
        uploadCompleteRequest: UploadCompleteRequest
    ): Response<Void>

    @ApiOperation("查询上传分块")
    @GetMapping("/info/{uploadId}")
    fun completeUpload(
        @ApiParam(value = "用户id", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @RequestHeader(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("上传事物id", required = true)
        @PathVariable
        uploadId: String
    ): Response<List<BlockInfo>>
}
