package com.tencent.bkrepo.generic.api

import com.tencent.bkrepo.common.api.constant.AUTH_HEADER_USER_ID
import com.tencent.bkrepo.common.api.constant.AUTH_HEADER_USER_ID_DEFAULT_VALUE
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.generic.annotation.WildcardParam
import com.tencent.bkrepo.generic.pojo.FileDetail
import com.tencent.bkrepo.generic.pojo.FileInfo
import com.tencent.bkrepo.generic.pojo.operate.FileCopyRequest
import com.tencent.bkrepo.generic.pojo.operate.FileMoveRequest
import com.tencent.bkrepo.generic.pojo.operate.FileSearchRequest
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

/**
 * 文件操作接口
 *
 * @author: carrypan
 * @date: 2019-09-29
 */
@Api("文件操作接口")
@RequestMapping
interface OperateResource {

    @ApiOperation("列出目录下的文件")
    @GetMapping("/list/{projectId}/{repoName}/**")
    fun listFile(
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
            @ApiParam("是否包含目录", required = false, defaultValue = "false")
            @RequestParam
            includeFolder: Boolean = false,
            @ApiParam("是否深度查询文件", required = false, defaultValue = "false")
            @RequestParam
            deep: Boolean = false
    ): Response<List<FileInfo>>

    @ApiOperation("搜索文件")
    @PostMapping("/search/{projectId}/{repoName}")
    fun searchFile(
            @ApiParam(value = "用户id", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
            @RequestHeader(AUTH_HEADER_USER_ID)
            userId: String,
            @ApiParam("项目id", required = true)
            @PathVariable
            projectId: String,
            @ApiParam("仓库名称", required = true)
            @PathVariable
            repoName: String,
            @RequestBody
            searchRequest: FileSearchRequest
    ): List<FileInfo>

    @ApiOperation("查询文件详情")
    @GetMapping("/detail/{projectId}/{repoName}/**")
    fun getFileDetail(
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
            fullPath: String
    ): Response<FileDetail>

    @ApiOperation("查询文件(夹)大小")
    @GetMapping("/size/{projectId}/{repoName}/**")
    fun getSize(
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
            fullPath: String
    ): Response<Long>

    @ApiOperation("创建文件夹")
    @PostMapping("/create/{projectId}/{repoName}/**")
    fun mkdir(
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
            fullPath: String
    ): Response<Void>

    @ApiOperation("删除文件(夹)")
    @DeleteMapping("/delete/{projectId}/{repoName}/**")
    fun delete(
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
            fullPath: String
    ): Response<Void>

    @ApiOperation("移动文件(不支持文件夹)")
    @PostMapping("/move/{projectId}/{repoName}/**")
    fun move(
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
            @RequestBody
            moveRequest: FileMoveRequest
    ): Response<Void>

    @ApiOperation("复制文件(夹)，支持跨项目复制")
    @PostMapping("/copy/{projectId}/{repoName}/**")
    fun copy(
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
            @RequestBody
            copyRequest: FileCopyRequest
    ): Response<Void>
}
