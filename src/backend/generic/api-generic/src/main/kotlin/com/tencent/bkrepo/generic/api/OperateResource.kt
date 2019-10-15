package com.tencent.bkrepo.generic.api

import com.tencent.bkrepo.common.api.constant.AUTH_HEADER_USER_ID
import com.tencent.bkrepo.common.api.constant.AUTH_HEADER_USER_ID_DEFAULT_VALUE
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping

/**
 * 文件操作接口
 *
 * @author: carrypan
 * @date: 2019-09-29
 */
@Api("文件操作接口")
@RequestMapping("/operate")
interface OperateResource {

    @ApiOperation("移动文件(不支持目录)")
    @PutMapping("/{projectId}/{repoName}/{fullPath}")
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
        @ApiParam("完整路径", required = true)
        @PathVariable
        fullPath: String,
        @ApiParam("新路径", required = true)
        newPath: String
    )

    @ApiOperation("删除文件(支持文件和目录)")
    @DeleteMapping("/{projectId}/{repoName}/{fullPath}")
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
        @ApiParam("完整路径", required = true)
        @PathVariable
        fullPath: String
    )
}
