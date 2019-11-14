package com.tencent.bkrepo.repository.api

import com.tencent.bkrepo.common.api.annotation.WildcardParam
import com.tencent.bkrepo.common.api.constant.AUTH_HEADER_USER_ID
import com.tencent.bkrepo.common.api.constant.AUTH_HEADER_USER_ID_DEFAULT_VALUE
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.repository.constant.SERVICE_NAME
import com.tencent.bkrepo.repository.pojo.metadata.UserMetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.UserMetadataSaveRequest
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping

/**
 * 节点元数据服务接口
 *
 * @author: carrypan
 * @date: 2019-10-18
 */
@Api("节点元数据服务接口")
@FeignClient(SERVICE_NAME, contextId = "UserMetadataResource")
@RequestMapping("/user/metadata")
interface UserMetadataResource {
    @ApiOperation("查询元数据列表")
    @GetMapping("/{projectId}/{repoName}/**")
    fun query(
        @ApiParam(value = "用户id", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @RequestHeader(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam(value = "所属项目", required = true)
        @PathVariable
        projectId: String,
        @ApiParam(value = "仓库名称", required = true)
        @PathVariable
        repoName: String,
        @ApiParam(hidden = true)
        @WildcardParam
        fullPath: String
    ): Response<Map<String, String>>

    @ApiOperation("创建/更新元数据列表")
    @PostMapping("/{projectId}/{repoName}/**")
    fun upsert(
        @ApiParam(value = "用户id", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @RequestHeader(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam(value = "所属项目", required = true)
        @PathVariable
        projectId: String,
        @ApiParam(value = "仓库名称", required = true)
        @PathVariable
        repoName: String,
        @ApiParam(hidden = true)
        @WildcardParam
        fullPath: String,
        @RequestBody
        metadataSaveRequest: UserMetadataSaveRequest
    ): Response<Void>

    @ApiOperation("删除元数据")
    @DeleteMapping("/{projectId}/{repoName}/**")
    fun delete(
        @ApiParam(value = "用户id", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @RequestHeader(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam(value = "所属项目", required = true)
        @PathVariable
        projectId: String,
        @ApiParam(value = "仓库名称", required = true)
        @PathVariable
        repoName: String,
        @ApiParam(hidden = true)
        @WildcardParam
        fullPath: String,
        @RequestBody
        metadataDeleteRequest: UserMetadataDeleteRequest
    ): Response<Void>
}
