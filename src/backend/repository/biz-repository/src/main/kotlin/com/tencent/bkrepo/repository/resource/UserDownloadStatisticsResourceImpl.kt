package com.tencent.bkrepo.repository.resource

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.permission.Permission
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.api.UserDownloadStatisticsResource
import com.tencent.bkrepo.repository.pojo.download.DownloadStatisticsMetricResponse
import com.tencent.bkrepo.repository.pojo.download.DownloadStatisticsResponse
import com.tencent.bkrepo.repository.service.DownloadStatisticsService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
class UserDownloadStatisticsResourceImpl @Autowired constructor(
    private val downloadStatisticsService: DownloadStatisticsService
) : UserDownloadStatisticsResource {

    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    override fun query(userId: String, artifactInfo: ArtifactInfo, artifact: String, version: String?, startDate: LocalDate, endDate: LocalDate): Response<DownloadStatisticsResponse> {
        with(artifactInfo) {
            val downloadStatisticsInfo = downloadStatisticsService.query(projectId, repoName, artifact, version, startDate, endDate)
            return ResponseBuilder.success(downloadStatisticsInfo)
        }
    }

    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    override fun queryForSpecial(userId: String, artifactInfo: ArtifactInfo, artifact: String, version: String?): Response<DownloadStatisticsMetricResponse> {
        with(artifactInfo) {
            val downloadStatisticsInfo = downloadStatisticsService.queryForSpecial(projectId, repoName, artifact, version)
            return ResponseBuilder.success(downloadStatisticsInfo)
        }
    }
}
