package com.tencent.bkrepo.repository.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.permission.Principal
import com.tencent.bkrepo.common.artifact.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.job.FileReferenceCleanupJob
import com.tencent.bkrepo.repository.job.FileSynchronizeJob
import com.tencent.bkrepo.repository.job.StorageInstanceMigrationJob
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Principal(type = PrincipalType.ADMIN)
@RestController
@RequestMapping("/api/job")
class SystemJobController(
    private val fileSynchronizeJob: FileSynchronizeJob,
    private val storageInstanceMigrationJob: StorageInstanceMigrationJob,
    private val fileReferenceCleanupJob: FileReferenceCleanupJob
) {


    @GetMapping("/synchronizeFile")
    fun synchronizeFile(): Response<Void> {
        fileSynchronizeJob.run()
        return ResponseBuilder.success()
    }

    @GetMapping("/migrate/{projectId}/{repoName}/{newKey}")
    fun migrate(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @PathVariable newKey: String
    ): Response<Void> {
        storageInstanceMigrationJob.migrate(projectId, repoName, newKey)
        return ResponseBuilder.success()
    }

    @GetMapping("/cleanup/reference")
    fun cleanupFileReference(
    ): Response<Void> {
        fileReferenceCleanupJob.cleanUp()
        return ResponseBuilder.success()
    }
}
