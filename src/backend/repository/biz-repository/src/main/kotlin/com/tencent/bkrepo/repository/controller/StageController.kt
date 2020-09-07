package com.tencent.bkrepo.repository.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactInfo
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.api.StageClient
import com.tencent.bkrepo.repository.pojo.stage.ArtifactStageEnum
import com.tencent.bkrepo.repository.pojo.stage.StageUpgradeRequest
import com.tencent.bkrepo.repository.service.StageService
import org.springframework.web.bind.annotation.RestController

/**
 * 制品晋级服务接口实现类
 */
@RestController
class StageController(
    private val stageService: StageService
) : StageClient {

    override fun query(projectId: String, repoName: String, fullPath: String): Response<ArtifactStageEnum> {
        val artifactInfo = DefaultArtifactInfo(projectId, repoName, fullPath)
        return ResponseBuilder.success(stageService.query(artifactInfo))
    }

    override fun upgrade(request: StageUpgradeRequest): Response<Void> {
        with(request) {
            val artifactInfo = DefaultArtifactInfo(projectId, repoName, fullPath)
            stageService.upgrade(artifactInfo)
            return ResponseBuilder.success()
        }
    }

    override fun downgrade(request: StageUpgradeRequest): Response<Void> {
        with(request) {
            val artifactInfo = DefaultArtifactInfo(projectId, repoName, fullPath)
            stageService.downgrade(artifactInfo)
            return ResponseBuilder.success()
        }
    }
}
