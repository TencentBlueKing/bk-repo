package com.tencent.bkrepo.job.controller.user

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.job.batch.task.cache.preload.ArtifactAccessLogEmbeddingJob
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/embedding")
@Principal(type = PrincipalType.ADMIN)
class UserEmbeddingController(
    private val artifactAccessLogEmbeddingJob: ArtifactAccessLogEmbeddingJob?
) {
    @PostMapping("/project/{projectId}")
    fun embed(@PathVariable projectId: String) {
        if (artifactAccessLogEmbeddingJob == null) {
            throw ErrorCodeException(CommonMessageCode.SYSTEM_ERROR, "unsupported operation")
        }
        artifactAccessLogEmbeddingJob.embedAccessLog(projectId)
    }
}
