package com.tencent.bkrepo.opdata.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.routing.CompensationHealthChecker
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.opdata.api.mongo.migration.MigrationBindingRequest
import com.tencent.bkrepo.opdata.api.mongo.migration.MigrationProjectRequest
import com.tencent.bkrepo.opdata.api.mongo.migration.MigrationStatusListResponse
import com.tencent.bkrepo.opdata.service.mongo.migration.MongoMigrationService
import io.swagger.v3.oas.annotations.Operation
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Principal(type = PrincipalType.ADMIN)
@RestController
@RequestMapping("/api")
@ConditionalOnBean(MongoRoutingRegistry::class)
class MongoMigrationController(
    private val migrationService: MongoMigrationService,
) {

    @Operation(summary = "声明迁移单元")
    @PostMapping("/migration/binding")
    fun binding(@RequestBody request: MigrationBindingRequest): Response<Void> {
        migrationService.binding(request)
        return ResponseBuilder.success()
    }

    @Operation(summary = "启动迁移同步")
    @PostMapping("/migration/start")
    fun start(@RequestBody request: MigrationProjectRequest): Response<Void> {
        migrationService.start(request)
        return ResponseBuilder.success()
    }

    @Operation(summary = "DBA dump/restore 完成后确认")
    @PostMapping("/migration/dump-complete")
    fun dumpComplete(@RequestBody request: MigrationProjectRequest): Response<Void> {
        migrationService.dumpComplete(request)
        return ResponseBuilder.success()
    }

    @Operation(summary = "VERIFY 通过后标记 READY")
    @PostMapping("/migration/ready")
    fun ready(@RequestBody request: MigrationProjectRequest): Response<Void> {
        migrationService.ready(request)
        return ResponseBuilder.success()
    }

    @Operation(summary = "开启双写")
    @PostMapping("/migration/dual-write")
    fun dualWrite(@RequestBody request: MigrationProjectRequest): Response<Void> {
        migrationService.enableDualWrite(request)
        return ResponseBuilder.success()
    }

    @Operation(summary = "关闭双写并切流")
    @PostMapping("/migration/route")
    fun route(@RequestBody request: MigrationProjectRequest): Response<Void> {
        migrationService.route(request)
        return ResponseBuilder.success()
    }

    @Operation(summary = "清理 Default 副本")
    @PostMapping("/migration/cleanup")
    fun cleanup(@RequestBody request: MigrationProjectRequest): Response<Void> {
        migrationService.cleanup(request)
        return ResponseBuilder.success()
    }

    @Operation(summary = "回滚迁移")
    @PostMapping("/migration/rollback")
    fun rollback(@RequestBody request: MigrationProjectRequest): Response<Void> {
        migrationService.rollback(request)
        return ResponseBuilder.success()
    }

    @Operation(summary = "查询迁移状态")
    @GetMapping("/migration/status")
    fun status(
        @RequestParam ruleName: String,
        @RequestParam(required = false) projectId: String?,
    ): Response<MigrationStatusListResponse> =
        ResponseBuilder.success(migrationService.status(ruleName, projectId))

    @Operation(summary = "G-34 路由就绪检查")
    @GetMapping("/routing/readiness")
    fun readiness(): Response<Any> =
        ResponseBuilder.success(migrationService.readiness() as Any)

    @Operation(summary = "补偿队列统计")
    @GetMapping("/compensation/stats")
    fun compensationStats(
        @RequestParam ruleName: String,
    ): Response<CompensationHealthChecker.CompensationStats> =
        ResponseBuilder.success(migrationService.compensationStats(ruleName))
}
