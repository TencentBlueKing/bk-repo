package com.tencent.bkrepo.media.web

import com.tencent.bkrepo.common.artifact.constant.PROJECT_ID
import com.tencent.bkrepo.common.artifact.constant.REPO_NAME
import com.tencent.bkrepo.media.service.MediaRepoService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping

/**
 * COS 归档上传前确保仓库已创建，避免 ArtifactFile 解析时因仓库不存在失败。
 */
@Component
class MediaCosRepoInterceptor(
    private val mediaRepoService: MediaRepoService,
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (!request.method.equals("PUT", ignoreCase = true)) {
            return true
        }
        val uriAttribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as? Map<*, *>
            ?: return true
        val projectId = uriAttribute[PROJECT_ID]?.toString() ?: return true
        val repoName = uriAttribute[REPO_NAME]?.toString() ?: return true
        val credentialsKey = mediaRepoService.resolveCosStorageCredentialsKey(projectId) ?: return true
        mediaRepoService.ensureRepo(projectId, repoName, credentialsKey)
        logger.debug("Ensure cos archive repo [$projectId/$repoName] with credentials [$credentialsKey]")
        return true
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MediaCosRepoInterceptor::class.java)
    }
}
