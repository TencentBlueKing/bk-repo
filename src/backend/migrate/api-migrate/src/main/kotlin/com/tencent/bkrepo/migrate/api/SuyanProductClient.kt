package com.tencent.bkrepo.migrate.api

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.migrate.MIGRATE_SERVICE_NAME
import com.tencent.bkrepo.migrate.pojo.BkProduct
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.context.annotation.Primary
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@Api("migrate productList 查询接口")
@Primary
@FeignClient(MIGRATE_SERVICE_NAME, contextId = "SuyanProductClient")
@RequestMapping("/service/suyan")
interface SuyanProductClient {
    @ApiOperation("根据制品信息查询 productList接口")
    @GetMapping("/products/maven/{repoName}")
    fun listMavenProducts(
        @PathVariable repoName: String,
        @RequestParam groupId: String,
        @RequestParam artifactId: String,
        @RequestParam version: String,
        @RequestParam type: String
    ): Response<List<BkProduct>?>
}
