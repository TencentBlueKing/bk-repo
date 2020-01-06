package com.tencent.bkrepo.generic.api

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactInfo.Companion.DEFAULT_MAPPING_URI
import com.tencent.bkrepo.generic.pojo.FileInfo
import com.tencent.bkrepo.generic.pojo.FileSearchRequest
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam

/**
 * 文件操作接口
 *
 * @author: carrypan
 * @date: 2019-09-29
 */
@Api("文件操作接口")
interface OperateResource {

    @ApiOperation("列出目录下的文件")
    @GetMapping("/list/$DEFAULT_MAPPING_URI")
    fun listFile(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo,
        @ApiParam("是否包含目录", required = false, defaultValue = "false")
        @RequestParam includeFolder: Boolean = true,
        @ApiParam("是否深度查询文件", required = false, defaultValue = "false")
        @RequestParam deep: Boolean = false
    ): Response<List<FileInfo>>

    @ApiOperation("搜索文件")
    @PostMapping("/search")
    fun searchFile(
        @RequestAttribute userId: String,
        @RequestBody searchRequest: FileSearchRequest
    ): Response<Page<FileInfo>>
}
