package com.tencent.bkrepo.repository.api

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.repository.constant.SERVICE_NAME
import io.swagger.annotations.ApiOperation
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.context.annotation.Primary
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@Primary
@FeignClient(SERVICE_NAME, contextId = "StageClient")
@RequestMapping("/service/stage")
interface StageClient {

    @ApiOperation("查询晋级状态")
    @GetMapping("/{projectId}/{repoName}")
    fun query(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestParam packageKey: String,
        @RequestParam version: String
    ): Response<List<String>>
}
