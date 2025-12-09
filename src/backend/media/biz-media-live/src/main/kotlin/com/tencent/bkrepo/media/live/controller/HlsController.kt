package com.tencent.bkrepo.media.live.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.media.live.service.HlsService
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/hls")
class HlsController(
    private val hlsService: HlsService,
) {

    /**
     * 获取HLS播放列表（m3u8文件）- 直播模式
     * 仅支持 TS (MPEG-TS) 格式
     * 直播流特点：
     * - 播放列表类型为EVENT（持续更新）
     * - 不包含#EXT-X-ENDLIST标签
     * - 动态获取实际存在的分片文件
     * - 支持滑动窗口，只保留最近的分片
     * @param projectId 项目ID
     * @param repoName 仓库名
     * @param resolution 分辨率
     * @return m3u8播放列表内容
     */
    @RequestMapping(
        value = ["/{projectId}/{repoName}/{resolution}/playlist.m3u8"],
        method = [RequestMethod.GET, RequestMethod.OPTIONS]
    )
    @Permission(ResourceType.REPO, PermissionAction.READ)
    fun getHlsPlaylist(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @PathVariable resolution: String,
    ): ResponseEntity<String> {
        val hlsService = hlsService
        val playlist = hlsService.getPlaylist(
            projectId = projectId,
            repoName = repoName,
            resolution = resolution,
        )
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, "application/vnd.apple.mpegurl")
            .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
            .body(playlist)
    }

    /**
     * 获取HLS分片文件（仅支持 TS 格式）
     */
    @RequestMapping(
        value = ["/{projectId}/{repoName}/**"],
        method = [RequestMethod.GET, RequestMethod.OPTIONS]
    )
    @Permission(ResourceType.REPO, PermissionAction.READ)
    fun getHlsSegment(
        @ArtifactPathVariable artifactInfo: ArtifactInfo
    ) {
        hlsService.getSegment(artifactInfo)
    }
}