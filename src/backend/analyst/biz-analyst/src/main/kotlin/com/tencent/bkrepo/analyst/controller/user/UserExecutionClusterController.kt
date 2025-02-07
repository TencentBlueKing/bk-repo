package com.tencent.bkrepo.analyst.controller.user

import com.tencent.bkrepo.analyst.pojo.execution.ExecutionCluster
import com.tencent.bkrepo.analyst.service.ExecutionClusterService
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.metadata.annotation.LogOperate
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "扫描执行集群配置接口")
@RestController
@RequestMapping("/api/execution/clusters")
@Principal(PrincipalType.ADMIN)
class UserExecutionClusterController(private val executionClusterService: ExecutionClusterService) {
    @Operation(summary = "创建集群")
    @PostMapping
    @LogOperate(type = "EXECUTION_CLUSTER_CREATE", desensitize = true)
    fun create(
        @RequestBody executionCluster: ExecutionCluster
    ): Response<ExecutionCluster> {
        return ResponseBuilder.success(executionClusterService.create(executionCluster))
    }

    @Operation(summary = "获取执行集群列表")
    @GetMapping
    @LogOperate(type = "EXECUTION_CLUSTER_LIST")
    fun list(): Response<List<ExecutionCluster>> {
        return ResponseBuilder.success(executionClusterService.list())
    }

    @Operation(summary = "删除执行集群")
    @DeleteMapping("/{name}")
    @LogOperate(type = "EXECUTION_CLUSTER_DELETE")
    fun delete(@PathVariable("name") name: String): Response<Void> {
        executionClusterService.remove(name)
        return ResponseBuilder.success()
    }

    @Operation(summary = "更新执行集群")
    @PutMapping("/{name}")
    @LogOperate(type = "EXECUTION_CLUSTER_UPDATE", desensitize = true)
    fun update(
        @PathVariable("name") name: String,
        @RequestBody executionCluster: ExecutionCluster
    ): Response<ExecutionCluster> {
        if (name != executionCluster.name) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, name)
        }
        return ResponseBuilder.success(executionClusterService.update(executionCluster))
    }
}
