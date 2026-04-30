package com.tencent.bkrepo.auth.controller.service

import com.tencent.bkrepo.auth.api.ServiceRepoModeClient
import com.tencent.bkrepo.auth.pojo.enums.AccessControlMode
import com.tencent.bkrepo.auth.pojo.permission.RepoAuthConfigInfo
import com.tencent.bkrepo.auth.service.RepoModeService
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import org.springframework.web.bind.annotation.RestController

@RestController
class ServiceRepoModeController(
    private val repoModeService: RepoModeService
) : ServiceRepoModeClient {

    override fun listByProject(projectId: String): Response<List<RepoAuthConfigInfo>> {
        return ResponseBuilder.success(repoModeService.listByProject(projectId))
    }

    override fun upsertRepoAuthConfig(
        projectId: String,
        repoName: String,
        accessControlMode: AccessControlMode,
        officeDenyGroupSet: Set<String>?,
        bkiamv3Check: Boolean
    ): Response<Boolean> {
        repoModeService.createOrUpdateConfig(
            projectId = projectId,
            repoName = repoName,
            accessControlMode = accessControlMode,
            officeDenyGroupSet = officeDenyGroupSet ?: emptySet(),
            bkiamv3Check = bkiamv3Check
        )
        return ResponseBuilder.success(true)
    }
}
