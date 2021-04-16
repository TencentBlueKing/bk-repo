package com.tencent.bkrepo.migrate.controller.user

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.migrate.artifact.MigrateArtifactInfo
import com.tencent.bkrepo.migrate.artifact.MigrateArtifactInfo.Companion.MIGRATE
import com.tencent.bkrepo.migrate.artifact.MigrateArtifactInfo.Companion.MIGRATE_SYNC_CLEAN
import com.tencent.bkrepo.migrate.artifact.MigrateArtifactInfo.Companion.SUYAN_SYNC
import com.tencent.bkrepo.migrate.pojo.suyan.SuyanArtifactInfo
import com.tencent.bkrepo.migrate.pojo.suyan.SuyanSyncRequest
import com.tencent.bkrepo.migrate.service.MigrateService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.GetMapping

@RestController
class MigrateController(
    val migrateService: MigrateService
) {
//    @PostMapping(MIGRATE_SYNC)
//    fun sync(
//        @ArtifactPathVariable migrateArtifactInfo: MigrateArtifactInfo,
//        @RequestBody syncRequest: SyncRequest
//    ): Response<String> {
//        return ResponseBuilder.success(migrateService.sync(migrateArtifactInfo, syncRequest))
//    }

    @PostMapping(SUYAN_SYNC)
    fun suyanSync(
        @ArtifactPathVariable migrateArtifactInfo: MigrateArtifactInfo,
        @RequestBody bkSyncRequest: SuyanSyncRequest
    ): Response<Boolean> {
        val syncRequest = migrateService.sync(migrateArtifactInfo, bkSyncRequest)
        return ResponseBuilder.build(
            code = 0,
            message = syncRequest.message,
            data = syncRequest.data
        )
    }

    @DeleteMapping(MIGRATE_SYNC_CLEAN)
    fun clean(
        @ArtifactPathVariable migrateArtifactInfo: MigrateArtifactInfo,
        @RequestParam metaField: String,
        @RequestParam value: String,
        // 由于文件锁的存在，该参数不能保证一定能成功删除 pending下的文件，需多次尝试
        @RequestParam includePending: Boolean = false
    ): Response<Boolean> {
        return ResponseBuilder.success(migrateService.clean(migrateArtifactInfo, metaField, value))
    }

    @GetMapping(MIGRATE)
    fun search(
        @ArtifactPathVariable migrateArtifactInfo: MigrateArtifactInfo,
        @RequestParam repoName: String,
        @RequestParam groupId: String,
        @RequestParam artifactId: String,
        @RequestParam version: String,
        @RequestParam type: String
    ): Response<SuyanArtifactInfo?> {
        return ResponseBuilder.success(migrateService.query(migrateArtifactInfo))
    }
}
