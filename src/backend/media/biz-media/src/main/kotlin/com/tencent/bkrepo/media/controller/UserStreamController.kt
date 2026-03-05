package com.tencent.bkrepo.media.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.pojo.token.TokenType
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
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*


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
        val action = body["action"] as String?
        val app = body["app"] as String?
        val stream = body["stream"] as String?
        val param = body["param"] as String?
        val ip = body["ip"] as String?

        logger.info("SRS on_play: $body")


//        // 解析 param 中的 token
//        val params: MutableMap<String?, String?> = parseQueryString(param)
//        val token = params.get("token")
//
//
//        // 没有 token，拒绝
//        if (token == null || token.isEmpty()) {
//            log.warn("拒绝拉流：缺少 token, stream={}, ip={}", stream, ip)
//            return ResponseEntity.status(403).body(java.util.Map.of("code", 403, "msg", "token required"))
//        }
//
//
//        // 验证 token
//        if (!verifyToken(token, stream)) {
//            log.warn("拒绝拉流：token 无效, stream={}, ip={}", stream, ip)
//            return ResponseEntity.status(403).body(java.util.Map.of("code", 403, "msg", "invalid token"))
//        }
//
//        log.info("允许拉流：stream={}, ip={}", stream, ip)
//
//        // 返回 200 + code=0，SRS 允许拉流
//        return ResponseEntity.ok<MutableMap<String?, Int?>?>(java.util.Map.of<String?, Int?>("code", 0))

        return ResponseBuilder.success(true)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UserStreamController::class.java)
    }
}
