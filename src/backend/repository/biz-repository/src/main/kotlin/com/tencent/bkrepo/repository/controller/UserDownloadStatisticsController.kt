package com.tencent.bkrepo.repository.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactInfo.Companion.DEFAULT_MAPPING_URI
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.pojo.download.DownloadStatisticsMetricResponse
import com.tencent.bkrepo.repository.pojo.download.DownloadStatisticsResponse
import com.tencent.bkrepo.repository.service.DownloadStatisticsService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@Api("构建下载量统计用户接口")
@RestController
@RequestMapping("/api/download/statistics")
class UserDownloadStatisticsController(
    private val downloadStatisticsService: DownloadStatisticsService
) {

    @ApiOperation("查询构建下载量")
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    @GetMapping("/query/$DEFAULT_MAPPING_URI")
    fun query(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo,
        @ApiParam("构建名称", required = true) artifact: String,
        @ApiParam("构建版本", required = false) version: String? = null,
        @ApiParam("开始日期", required = true)
        @RequestParam startDate: LocalDate,
        @ApiParam("结束日期", required = true)
        @RequestParam endDate: LocalDate
    ): Response<DownloadStatisticsResponse> {
        with(artifactInfo) {
            val downloadStatisticsInfo =
                downloadStatisticsService.query(projectId, repoName, artifact, version, startDate, endDate)
            return ResponseBuilder.success(downloadStatisticsInfo)
        }
    }

    @ApiOperation("查询构建在 日、周、月 的下载量")
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    @GetMapping("/query/special/$DEFAULT_MAPPING_URI")
    fun queryForSpecial(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo,
        @ApiParam("构建名称", required = true) artifact: String,
        @ApiParam("构建版本", required = false) version: String? = null
    ): Response<DownloadStatisticsMetricResponse> {
        with(artifactInfo) {
            val downloadStatisticsInfo =
                downloadStatisticsService.queryForSpecial(projectId, repoName, artifact, version)
            return ResponseBuilder.success(downloadStatisticsInfo)
        }
    }
}
