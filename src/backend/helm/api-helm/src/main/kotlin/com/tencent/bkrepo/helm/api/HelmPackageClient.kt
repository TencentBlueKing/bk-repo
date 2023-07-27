package com.tencent.bkrepo.helm.api

import com.tencent.bkrepo.common.api.constant.HELM_SERVICE_NAME
import com.tencent.bkrepo.common.api.pojo.Response
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@Api("Helm 包服务接口")
@FeignClient(HELM_SERVICE_NAME, contextId = "HelmPackageClient")
@RequestMapping("/service")
interface HelmPackageClient {
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
