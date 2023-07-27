package com.tencent.bkrepo.maven.api

import com.tencent.bkrepo.common.api.constant.MAVEN_SERVICE_NAME
import io.swagger.annotations.ApiOperation
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@ApiOperation("Maven client")
@FeignClient(MAVEN_SERVICE_NAME, contextId = "mavenClient")
@RequestMapping("/service")
interface MavenClient {
    @DeleteMapping("/{projectId}/{repoName}")
    fun deleteVersion(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestParam packageKey: String,
        @RequestParam version: String,
        @RequestParam operator: String
    ): Boolean
}
