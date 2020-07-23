package com.tencent.bkrepo.auth.api

import com.tencent.bkrepo.auth.constant.AUTH_CLUSTER_PREFIX
import com.tencent.bkrepo.auth.constant.SERVICE_NAME
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.auth.pojo.AddClusterRequest
import com.tencent.bkrepo.auth.pojo.Cluster
import com.tencent.bkrepo.auth.pojo.UpdateClusterRequest
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.cloud.openfeign.FeignClient
import io.swagger.annotations.ApiParam
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping

@Api(tags = ["SERVICE_CLUSTER"], description = "服务-集群管理接口")
@FeignClient(SERVICE_NAME, contextId = "ServiceClustersource")
@RequestMapping(AUTH_CLUSTER_PREFIX)
interface ServiceClusterResource {

    @ApiOperation("添加集群")
    @PostMapping("/add")
    fun add(
        @RequestBody request: AddClusterRequest
    ): Response<Boolean>

    @ApiOperation("列出所有集群")
    @GetMapping("/list")
    fun list(): Response<List<Cluster>>

    @ApiOperation("校验集群状态")
    @GetMapping("/ping/{clusterId}")
    fun ping(
        @ApiParam(value = "集群id")
        @PathVariable clusterId: String
    ): Response<Boolean>

    @ApiOperation("删除集群")
    @DeleteMapping("/{clusterId}")
    fun delete(
        @ApiParam(value = "集群id")
        @PathVariable clusterId: String
    ): Response<Boolean>

    @ApiOperation("更新集群")
    @PutMapping("/{clusterId}")
    fun update(
        @ApiParam(value = "集群id")
        @PathVariable clusterId: String,
        @RequestBody request: UpdateClusterRequest
    ): Response<Boolean>

    @ApiOperation("认证集群")
    @GetMapping("/credential")
    fun credential(): Response<Boolean>
}
