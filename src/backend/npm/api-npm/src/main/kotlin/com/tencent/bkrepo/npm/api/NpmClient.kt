package com.tencent.bkrepo.npm.api

import com.tencent.bkrepo.common.api.constant.NPM_SERVICE_NAME
import com.tencent.bkrepo.common.api.pojo.Response
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@Api("Npm 内部服务接口")
@FeignClient(NPM_SERVICE_NAME, contextId = "NpmClient")
@RequestMapping("/service")
interface NpmClient {
    @ApiOperation("删除仓库下的包版本")
    @DeleteMapping("/version/delete/{projectId}/{repoName}")
    fun deleteVersion(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestParam packageKey: String,
        @RequestParam version: String,
        @RequestParam operator: String
    ): Response<Void>
}
