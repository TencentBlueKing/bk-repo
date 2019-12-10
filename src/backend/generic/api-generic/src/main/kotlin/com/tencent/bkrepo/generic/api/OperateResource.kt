package com.tencent.bkrepo.generic.api

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactInfo.Companion.DEFAULT_MAPPING_URI
import com.tencent.bkrepo.generic.pojo.FileDetail
import com.tencent.bkrepo.generic.pojo.FileInfo
import com.tencent.bkrepo.generic.pojo.FileSizeInfo
import com.tencent.bkrepo.generic.pojo.operate.FileCopyRequest
import com.tencent.bkrepo.generic.pojo.operate.FileMoveRequest
import com.tencent.bkrepo.generic.pojo.operate.FileRenameRequest
import com.tencent.bkrepo.generic.pojo.operate.FileSearchRequest
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

/**
 * 文件操作接口
 *
 * @author: carrypan
 * @date: 2019-09-29
 */
@Api("文件操作接口")
@RequestMapping("/")
interface OperateResource {

    @ApiOperation("列出目录下的文件")
    @GetMapping("/list/$DEFAULT_MAPPING_URI")
    fun listFile(
        @RequestAttribute
        userId: String,
        @ArtifactPathVariable
        artifactInfo: ArtifactInfo,
        @ApiParam("是否包含目录", required = false, defaultValue = "false")
        @RequestParam
        includeFolder: Boolean = true,
        @ApiParam("是否深度查询文件", required = false, defaultValue = "false")
        @RequestParam
        deep: Boolean = false
    ): Response<List<FileInfo>>

    @ApiOperation("搜索文件")
    @PostMapping("/search")
    fun searchFile(
        @RequestAttribute
        userId: String,
        @RequestBody
        searchRequest: FileSearchRequest
    ): Response<Page<FileInfo>>

    @ApiOperation("查询文件详情")
    @GetMapping("/detail/$DEFAULT_MAPPING_URI")
    fun getFileDetail(
        @RequestAttribute
        userId: String,
        @ArtifactPathVariable
        artifactInfo: ArtifactInfo
    ): Response<FileDetail>

    @ApiOperation("查询文件(夹)大小")
    @GetMapping("/size/$DEFAULT_MAPPING_URI")
    fun getFileSize(
        @RequestAttribute
        userId: String,
        @ArtifactPathVariable
        artifactInfo: ArtifactInfo
    ): Response<FileSizeInfo>

    @ApiOperation("创建文件夹")
    @PostMapping("/create/$DEFAULT_MAPPING_URI")
    fun mkdir(
        @RequestAttribute
        userId: String,
        @ArtifactPathVariable
        artifactInfo: ArtifactInfo
    ): Response<Void>

    @ApiOperation("删除文件(夹)")
    @DeleteMapping("/delete/$DEFAULT_MAPPING_URI")
    fun delete(
        @RequestAttribute
        userId: String,
        @ArtifactPathVariable
        artifactInfo: ArtifactInfo
    ): Response<Void>

    @ApiOperation("重命名文件(夹)")
    @PutMapping("/rename")
    fun rename(
        @RequestAttribute
        userId: String,
        @RequestBody
        renameRequest: FileRenameRequest
    ): Response<Void>

    @ApiOperation("移动文件(夹)")
    @PutMapping("/move")
    fun move(
        @RequestAttribute
        userId: String,
        @RequestBody
        moveRequest: FileMoveRequest
    ): Response<Void>

    @ApiOperation("复制文件(夹)，支持跨项目复制")
    @PutMapping("/copy")
    fun copy(
        @RequestAttribute
        userId: String,
        @RequestBody
        copyRequest: FileCopyRequest
    ): Response<Void>
}
