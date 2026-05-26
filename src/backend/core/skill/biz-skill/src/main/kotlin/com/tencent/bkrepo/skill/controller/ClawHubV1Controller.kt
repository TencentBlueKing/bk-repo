/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.skill.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.artifact.api.ArtifactMultiFileMap
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.skill.constant.DEFAULT_PAGE_LIMIT
import com.tencent.bkrepo.skill.constant.SORT_UPDATED
import com.tencent.bkrepo.skill.pojo.artifact.ClawHubPublishInfo
import com.tencent.bkrepo.skill.pojo.artifact.ClawHubArchiveDownloadInfo
import com.tencent.bkrepo.skill.pojo.artifact.ClawHubFileDownloadInfo
import com.tencent.bkrepo.skill.pojo.artifact.ClawHubResolveInfo
import com.tencent.bkrepo.skill.pojo.artifact.ClawHubSearchInfo
import com.tencent.bkrepo.skill.pojo.artifact.ClawHubSkillInfo
import com.tencent.bkrepo.skill.pojo.artifact.ClawHubSkillListInfo
import com.tencent.bkrepo.skill.pojo.artifact.ClawHubSkillModerationInfo
import com.tencent.bkrepo.skill.pojo.artifact.ClawHubSkillVersionInfo
import com.tencent.bkrepo.skill.pojo.artifact.ClawHubSkillVersionListInfo
import com.tencent.bkrepo.skill.pojo.response.ClawHubPublishResponse
import com.tencent.bkrepo.skill.pojo.response.ClawHubSkillListResponse
import com.tencent.bkrepo.skill.pojo.response.ClawHubSkillDetailResponse
import com.tencent.bkrepo.skill.pojo.response.ClawHubSkillVersionDetailResponse
import com.tencent.bkrepo.skill.pojo.response.ClawHubSkillVersionListResponse
import com.tencent.bkrepo.skill.pojo.response.ClawHubModerationResponse
import com.tencent.bkrepo.skill.pojo.response.ClawHubResolveResponse
import com.tencent.bkrepo.skill.pojo.response.ClawHubSearchResponse
import com.tencent.bkrepo.skill.service.ClawHubService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Skill ClawHub v1 API")
@RestController
@RequestMapping("/{projectId}/{repoName}/api/v1", produces = [MediaTypes.APPLICATION_JSON])
class ClawHubV1Controller(
    private val clawHubService: ClawHubService,
) {

    @Operation(summary = "获取skill列表")
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    @GetMapping("/skills")
    fun listSkills(
        artifactInfo: ClawHubSkillListInfo,
        @RequestParam(defaultValue = DEFAULT_PAGE_LIMIT.toString()) limit: Int,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = SORT_UPDATED) sort: String,
    ): ClawHubSkillListResponse {
        return clawHubService.listSkills(artifactInfo)
    }

    @Operation(summary = "获取skill详情")
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    @GetMapping("/skills/{slug}")
    fun getSkillDetail(
        artifactInfo: ClawHubSkillInfo,
    ): ClawHubSkillDetailResponse {
        return clawHubService.getSkillDetail(artifactInfo)
    }

    @Operation(summary = "获取skill审核状态")
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    @GetMapping("/skills/{slug}/moderation")
    fun getSkillModeration(
        artifactInfo: ClawHubSkillModerationInfo,
    ): ClawHubModerationResponse {
        return clawHubService.getSkillModeration(artifactInfo)
    }

    @Operation(summary = "获取指定skill所有版本")
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    @GetMapping("/skills/{slug}/versions")
    fun listSkillVersions(
        artifactInfo: ClawHubSkillVersionListInfo,
    ): ClawHubSkillVersionListResponse {
        return clawHubService.listSkillVersions(artifactInfo)
    }

    @Operation(summary = "获取skill指定版本详情")
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    @GetMapping("/skills/{slug}/versions/{version}")
    fun getSkillVersionDetail(
        artifactInfo: ClawHubSkillVersionInfo,
    ): ClawHubSkillVersionDetailResponse {
        return clawHubService.getSkillVersionDetail(artifactInfo)
    }

    @Operation(summary = "搜索skill")
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    @GetMapping("/search")
    fun search(
        artifactInfo: ClawHubSearchInfo,
        @RequestParam q: String,
        @RequestParam(defaultValue = "10") limit: Int,
    ): ClawHubSearchResponse {
        return clawHubService.search(artifactInfo)
    }

    @Operation(summary = "根据fingerprint解析skill版本")
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    @GetMapping("/resolve")
    fun resolve(
        artifactInfo: ClawHubResolveInfo,
        @RequestParam slug: String,
        @RequestParam hash: String,
    ): ClawHubResolveResponse {
        return clawHubService.resolve(artifactInfo)
    }

    @Operation(summary = "下载skill zip")
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    @GetMapping("/download", produces = [MediaType.ALL_VALUE])
    fun download(
        artifactInfo: ClawHubArchiveDownloadInfo,
        @RequestParam slug: String,
        @RequestParam(required = false) version: String?,
        @RequestParam(required = false) tag: String?,
    ) {
        clawHubService.download(artifactInfo)
    }

    @Operation(summary = "获取skill压缩包内单个文件")
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    @GetMapping("/skills/{slug}/file", produces = [MediaType.ALL_VALUE])
    fun getFileContent(
        artifactInfo: ClawHubFileDownloadInfo,
        @RequestParam(required = false) version: String?,
        @RequestParam(required = false) tag: String?,
        @RequestParam path: String,
    ) {
        clawHubService.getFileContent(artifactInfo)
    }

    @Operation(summary = "发布skill")
    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    @PostMapping("/skills", consumes = ["multipart/form-data"])
    fun publish(
        artifactMultiFileMap: ArtifactMultiFileMap,
        artifactInfo: ClawHubPublishInfo,
    ): ClawHubPublishResponse {
        return clawHubService.publish(artifactInfo, artifactMultiFileMap)
    }
}
