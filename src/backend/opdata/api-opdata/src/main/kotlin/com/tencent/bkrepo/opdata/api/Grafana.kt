package com.tencent.bkrepo.opdata.api

import com.tencent.bkrepo.opdata.constant.SERVICE_NAME
import com.tencent.bkrepo.opdata.pojo.QueryRequest
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping

@Api(tags = ["SERVICE_OPDATA"], description = "服务-运营数据接口")
@FeignClient(SERVICE_NAME, contextId = "ServiceGrafanaOpdata")
@RequestMapping("/api/grafana")
interface Grafana {

    @ApiOperation("ping")
    @GetMapping("")
    fun ping(): ResponseEntity<Any>

    @ApiOperation("search")
    @PostMapping("/search")
    fun search(): ResponseEntity<Any>

    @ApiOperation("query")
    @PostMapping("/query")
    fun query(
        @RequestBody request: QueryRequest
    ): ResponseEntity<Any>

    @ApiOperation("annotations")
    @PostMapping("/annotations")
    fun annotations(): ResponseEntity<Any>

//    @ApiOperation("tag-keys")
//    @PostMapping("/tag-keys")
//    fun tagKeys(
//    ): Response<String?>
//
//    @ApiOperation("tag-values")
//    @PostMapping("/tag-values")
//    fun tagValues(
//    ): Response<String?>
}
