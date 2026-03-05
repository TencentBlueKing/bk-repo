package com.tencent.bkrepo.media.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.media.artifact.MediaArtifactInfo
import com.tencent.bkrepo.media.artifact.MediaArtifactInfo.Companion.DEFAULT_STREAM_MAPPING_URI
import com.tencent.bkrepo.media.service.StreamService
import com.tencent.bkrepo.media.service.TokenService
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController


/**
 * 流控制器
 * */
@RestController
@RequestMapping("/api/user/stream")
class UserStreamController(
    private val streamService: StreamService,
    private val permissionManager: PermissionManager,
    private val tokenService: TokenService,
) {

    /**
     * 生成推流地址
     * */
    @PostMapping("/create/{projectId}/{repoName}")
    fun createStream(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestParam(required = false, defaultValue = "true") display: Boolean = true,
    ): Response<String> {
        permissionManager.checkProjectPermission(
            action = PermissionAction.MANAGE,
            projectId = projectId,
        )
        return ResponseBuilder.success(streamService.createStream(projectId, repoName, display))
    }

    /**
     * 下载流媒体文件
     * */
    @GetMapping(DEFAULT_STREAM_MAPPING_URI)
    @Permission(ResourceType.REPO, PermissionAction.MANAGE)
    fun download(@ArtifactPathVariable artifactInfo: MediaArtifactInfo) {
        streamService.download(artifactInfo)
    }

    /**
     * 生成拉流地址
     */
    @GetMapping("/{projectId}/{repoName}/{resolution}/rtc")
    @Permission(ResourceType.REPO, PermissionAction.READ)
    fun rtc(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @PathVariable resolution: String,
    ): Response<String> {
        val url = streamService.fetchRtc(projectId, repoName, resolution)
        return ResponseBuilder.success(url)
    }

    @PostMapping("/rtc/verify_token")
    fun verifyToken(
        @RequestBody
        body: Map<String, Any>
    ): Response<Boolean> {
//        val action = body["action"] as String?
//        val app = body["app"] as String?
        val stream = body["stream"] as? String? ?: return ResponseBuilder.fail(
            HttpStatus.NOT_FOUND.value,
            HttpStatus.NOT_FOUND.reasonPhrase
        )
        val param = body["param"] as String?
        val ip = body["ip"] as String?
        val token = param?.substringAfter("token=")?.substringBefore("&")

        // 没有 token，拒绝
        if (token.isNullOrBlank()) {
            logger.warn("no token $param")
            return ResponseBuilder.fail(HttpStatus.FORBIDDEN.value, "no token")
        }
        // 验证 token
        if (!streamService.verifyToken(token, stream)) {
            logger.warn("token error, $stream|$param")
            return ResponseBuilder.fail(HttpStatus.FORBIDDEN.value, "invalid token")
        }

        logger.info("success stream=$stream, ip=$ip")
        return ResponseBuilder.success(true)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UserStreamController::class.java)
    }
}
