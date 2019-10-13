package com.tencent.bkrepo.generic.api

import com.tencent.bkrepo.common.api.constant.AUTH_HEADER_USER_ID
import com.tencent.bkrepo.common.api.constant.AUTH_HEADER_USER_ID_DEFAULT_VALUE
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.generic.annotation.WildcardParam
import com.tencent.bkrepo.generic.pojo.BlockInfo
import com.tencent.bkrepo.generic.pojo.upload.BlockUploadRequest
import com.tencent.bkrepo.generic.pojo.upload.SimpleUploadRequest
import com.tencent.bkrepo.generic.pojo.upload.UploadCompleteRequest
import com.tencent.bkrepo.generic.pojo.upload.UploadPreCheckRequest
import com.tencent.bkrepo.generic.pojo.upload.UploadTransactionInfo
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
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
    @PutMapping("/simple/{projectId}/{repoName}/**")
    fun simpleUpload(
        @ApiParam(value = "用户id", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @RequestHeader(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("项目id", required = true)
        @PathVariable
        projectId: String,
        @ApiParam("仓库名称", required = true)
        @PathVariable
        repoName: String,
        @ApiParam(hidden = true)
        @WildcardParam
        fullPath: String,
        @ApiParam("文件", required = true)
        file: MultipartFile,
        request: SimpleUploadRequest
    ): Response<Void>

    @ApiOperation("分块上传预检")
    @PostMapping("/precheck/{projectId}/{repoName}/**")
    fun preCheck(
        @ApiParam(value = "用户id", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @RequestHeader(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("项目id", required = true)
        @PathVariable
        projectId: String,
        @ApiParam("仓库名称", required = true)
        @PathVariable
        repoName: String,
        @ApiParam(hidden = true)
        @WildcardParam
        fullPath: String,
        request: UploadPreCheckRequest
    ): Response<UploadTransactionInfo>

    @ApiOperation("分块上传")
    @PutMapping("/block/{uploadId}/{sequence}")
    fun blockUpload(
        @ApiParam(value = "用户id", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @RequestHeader(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("分块上传事物id", required = true)
        @PathVariable
        uploadId: String,
        @ApiParam("分块序号", required = true)
        @PathVariable
        sequence: Int,
        @ApiParam("文件", required = true)
        file: MultipartFile,
        request: BlockUploadRequest
    ): Response<Void>

    @ApiOperation("取消分块上传")
    @DeleteMapping("/abort/{uploadId}")
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
        request: UploadCompleteRequest
    ): Response<Void>

    @ApiOperation("查询上传分块")
    @GetMapping("/info/{uploadId}")
    fun queryBlockInfo(
        @ApiParam(value = "用户id", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @RequestHeader(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("上传事物id", required = true)
        @PathVariable
        uploadId: String
    ): Response<List<BlockInfo>>
}
