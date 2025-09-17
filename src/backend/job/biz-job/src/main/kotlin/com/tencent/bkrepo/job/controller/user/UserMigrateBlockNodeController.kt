package com.tencent.bkrepo.job.controller.user

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.job.pojo.MigrateBlockNodeRequest
import com.tencent.bkrepo.job.service.MigrateBlockNodeCollectionService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/job/migrate/block")
@Principal(type = PrincipalType.ADMIN)
class UserMigrateBlockNodeController(
    private val migrateBlockNodeCollectionService: MigrateBlockNodeCollectionService
) {
    @PostMapping
    fun migrate(@RequestBody request: MigrateBlockNodeRequest): Response<Any?> {
        migrateBlockNodeCollectionService.migrate(
            oldCollectionNamePrefix = request.oldCollectionNamePrefix,
            newCollectionNamePrefix = request.newCollectionNamePrefix,
            newShardingColumns = request.newShardingColumns,
            newShardingCount = request.newShardingCount,
        )
        return ResponseBuilder.success()
    }
}
