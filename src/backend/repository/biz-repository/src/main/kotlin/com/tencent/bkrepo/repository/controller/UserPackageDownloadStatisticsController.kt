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
import com.tencent.bkrepo.repository.service.PackageDownloadStatisticsService
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
@RequestMapping("/api/package/download/statistics")
class UserPackageDownloadStatisticsController(
    private val packageDownloadStatisticsService: PackageDownloadStatisticsService
) {

    @ApiOperation("查询构建下载量")
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    @GetMapping("/query/$DEFAULT_MAPPING_URI")
    fun query(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo,
        @ApiParam("包唯一Key", required = true)
        @RequestParam packageKey: String,
        @ApiParam("包版本", required = false)
        @RequestParam version: String? = null,
        @ApiParam("开始日期", required = false)
        @RequestParam startDay: LocalDate? = null,
        @ApiParam("结束日期", required = false)
        @RequestParam endDay: LocalDate? = null
    ): Response<DownloadStatisticsResponse> {
        with(artifactInfo){
            return ResponseBuilder.success(
                packageDownloadStatisticsService.query(projectId, repoName, packageKey, version, startDay, endDay)
            )
        }
    }

    @ApiOperation("查询构建在 日、周、月 的下载量")
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    @GetMapping("/query/special/$DEFAULT_MAPPING_URI")
    fun queryForSpecial(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: ArtifactInfo,
        @ApiParam("包唯一Key", required = true)
        @RequestParam packageKey: String
    ): Response<DownloadStatisticsMetricResponse> {
        with(artifactInfo){
            return ResponseBuilder.success(
                packageDownloadStatisticsService.queryForSpecial(projectId, repoName, packageKey)
            )
        }
    }
}