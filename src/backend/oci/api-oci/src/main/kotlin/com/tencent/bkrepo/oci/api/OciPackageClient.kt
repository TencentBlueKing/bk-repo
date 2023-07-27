package com.tencent.bkrepo.oci.api

import com.tencent.bkrepo.common.api.constant.OCI_SERVICE_NAME
import io.swagger.annotations.Api
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@Api("Oci 包服务接口")
@FeignClient(OCI_SERVICE_NAME, contextId = "OciPackageClient")
@RequestMapping("/service")
interface OciPackageClient {
    @DeleteMapping("/{projectId}/{repoName}")
    fun deleteVersion(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestParam packageKey: String,
        @RequestParam version: String,
        @RequestParam operator: String
    ): Boolean
}
