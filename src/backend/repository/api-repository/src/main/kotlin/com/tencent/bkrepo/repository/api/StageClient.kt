package com.tencent.bkrepo.repository.api

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.repository.constant.SERVICE_NAME
import com.tencent.bkrepo.repository.pojo.stage.ArtifactStageEnum
import com.tencent.bkrepo.repository.pojo.stage.StageUpgradeRequest
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.context.annotation.Primary
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@Api("制品晋级服务接口")
@Primary
@FeignClient(SERVICE_NAME, contextId = "StageClient")
@RequestMapping("/service/stage")
interface StageClient {

    @ApiOperation("查询制品状态")
    @GetMapping("/query/{projectId}/{repoName}")
    fun query(
        @ApiParam(value = "所属项目", required = true)
        @PathVariable projectId: String,
        @ApiParam(value = "仓库名称", required = true)
        @PathVariable repoName: String,
        @ApiParam(value = "节点完整路径", required = true)
        @RequestParam fullPath: String
    ): Response<ArtifactStageEnum>

    @ApiOperation("制品晋级")
    @PostMapping("/upgrade")
    fun upgrade(@RequestBody request: StageUpgradeRequest): Response<Void>

    @ApiOperation("制品降级")
    @PostMapping("/downgrade")
    fun downgrade(@RequestBody request: StageUpgradeRequest): Response<Void>
}
