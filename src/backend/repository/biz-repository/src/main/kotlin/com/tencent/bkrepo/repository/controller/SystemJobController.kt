package com.tencent.bkrepo.repository.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.job.FileReferenceCleanupJob
import com.tencent.bkrepo.repository.job.FileSynchronizeJob
import com.tencent.bkrepo.repository.job.NodeDeletedCorrectionJob
import com.tencent.bkrepo.repository.job.RootNodeCleanupJob
import com.tencent.bkrepo.repository.job.StorageInstanceMigrationJob
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Principal(type = PrincipalType.ADMIN)
@RestController
@RequestMapping("/api/job")
class SystemJobController(
    private val fileSynchronizeJob: FileSynchronizeJob,
    private val storageInstanceMigrationJob: StorageInstanceMigrationJob,
    private val fileReferenceCleanupJob: FileReferenceCleanupJob,
    private val rootNodeCleanupJob: RootNodeCleanupJob,
    private val nodeDeletedCorrectionJob: NodeDeletedCorrectionJob,
    private val mongoTemplate: MongoTemplate
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
    fun cleanupFileReference(): Response<Void> {
        fileReferenceCleanupJob.cleanUp()
        return ResponseBuilder.success()
    }

    @PostMapping("/correct/node")
    fun correct(): Response<Void> {
        nodeDeletedCorrectionJob.correct()
        return ResponseBuilder.success()
    }

    @PostMapping("/cleanup/rootNode")
    fun cleanupRootNode(): Response<Void> {
        rootNodeCleanupJob.cleanup()
        return ResponseBuilder.success()
    }

}
