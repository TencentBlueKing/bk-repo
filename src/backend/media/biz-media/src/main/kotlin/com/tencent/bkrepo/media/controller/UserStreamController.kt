package com.tencent.bkrepo.media.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.media.artifact.MediaArtifactInfo
import com.tencent.bkrepo.media.artifact.MediaArtifactInfo.Companion.DEFAULT_STREAM_MAPPING_URI
import com.tencent.bkrepo.media.common.pojo.stream.MediaStreamRouteInfo
import com.tencent.bkrepo.media.service.StreamService
import com.tencent.bkrepo.media.service.TokenService
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
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
    fun rtc(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @PathVariable resolution: String,
    ): Response<String> {
        // 回调remotedev校验权限
        val userId = SecurityUtils.getUserId()
        val workspaceName = repoName.removePrefix("REMOTEDEV_")
        if (!streamService.checkUserWorkspaceLivePerm(projectId, workspaceName, userId)) {
            return ResponseBuilder.fail(HttpStatus.FORBIDDEN.value, "no workspace live read perm")
        }
        val url = streamService.fetchRtc(projectId, repoName, resolution)
        return ResponseBuilder.success(url)
    }

    @Deprecated("被 handleSrsHttpHook 取代")
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

    /**
     * SRS http_hook 回调
     * 接收 on_publish/on_unpublish 事件并同步活跃流记录
     */
    @PostMapping("/rtc/http_hook")
    fun handleSrsHttpHook(
        @RequestBody
        body: Map<String, Any>,
        @RequestParam(required = true) machine: String,
        request: HttpServletRequest,
    ): Response<Boolean> {
        logger.debug("handleSrsHttpHook| $body")
        val action = body["action"] as? String ?: return ResponseBuilder.fail(
            HttpStatus.BAD_REQUEST.value,
            "action required"
        )
        val streamId = body["stream"] as? String ?: return ResponseBuilder.fail(
            HttpStatus.BAD_REQUEST.value,
            "stream required"
        )
        val clientIp = body["ip"] as? String
        return when (action) {
            "on_publish" -> {
                val serverId = body["server_id"] as? String
                val app = body["app"] as? String
                val vhost = body["vhost"] as? String
                streamService.saveActiveStream(
                    streamId = streamId,
                    machine = machine,
                    serverId = serverId,
                    app = app,
                    vhost = vhost,
                    clientIp = clientIp,
                )
                logger.info("srs publish success, streamId=$streamId, machine=$machine, serverId=$serverId")
                ResponseBuilder.success(true)
            }

            "on_unpublish" -> {
                streamService.deleteActiveStream(streamId)
                logger.info("srs unpublish success, streamId=$streamId")
                ResponseBuilder.success(true)
            }

            "on_play" -> {
                val param = body["param"] as String?
                val token = param?.substringAfter("token=")?.substringBefore("&")
                if (token.isNullOrBlank()) {
                    logger.warn("no token $param")
                    return ResponseBuilder.fail(HttpStatus.FORBIDDEN.value, "no token")
                }
                if (!streamService.verifyToken(token, streamId)) {
                    logger.warn("token error, $streamId|$param")
                    return ResponseBuilder.fail(HttpStatus.FORBIDDEN.value, "invalid token")
                }
                logger.info("success stream=$streamId, ip=$clientIp")
                ResponseBuilder.success(true)
            }

            else -> {
                logger.warn("unsupported srs action=$action, streamId=$streamId")
                ResponseBuilder.fail(HttpStatus.BAD_REQUEST.value, "unsupported action: $action")
            }
        }
    }

    /**
     * 查询流所在机器，供 nginx 分配拉流机器
     */
    @GetMapping("/rtc/route")
    fun getStreamRoute(
        @RequestParam streamId: String,
    ): Response<MediaStreamRouteInfo> {
        val route = streamService.getActiveStreamRoute(streamId) ?: return ResponseBuilder.fail(
            HttpStatus.NOT_FOUND.value,
            "stream not found"
        )
        return ResponseBuilder.success(route)
    }

    /**
     * 给 nginx auth_request 使用，直接通过响应头返回机器地址
     */
    @GetMapping("/rtc/route/header")
    fun getStreamRouteHeader(
        @RequestParam streamId: String,
    ): ResponseEntity<Void> {
        val route = streamService.getActiveStreamRoute(streamId) ?: return ResponseEntity.notFound().build()
        val builder = ResponseEntity.ok()
            .header(HEADER_STREAM_MACHINE, route.machine)
            .header(HEADER_STREAM_ID, route.streamId)
        route.serverId?.takeIf { it.isNotBlank() }?.let {
            builder.header(HEADER_STREAM_SERVER_ID, it)
        }
        return builder.build()
    }

    /**
     * 给 nginx lua 使用，直接返回纯 JSON 路由信息
     */
    @GetMapping("/rtc/route/raw")
    fun getStreamRouteRaw(
        @RequestParam streamId: String,
    ): ResponseEntity<MediaStreamRouteInfo> {
        val route = streamService.getActiveStreamRoute(streamId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(route)
    }

    companion object {

        private val logger = LoggerFactory.getLogger(UserStreamController::class.java)
        private const val HEADER_STREAM_MACHINE = "X-Stream-Machine"
        private const val HEADER_STREAM_ID = "X-Stream-Id"
        private const val HEADER_STREAM_SERVER_ID = "X-Stream-Server-Id"
    }
}
