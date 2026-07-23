package com.tencent.bkrepo.fs.server.request.drive

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.common.artifact.constant.PROJECT_ID
import com.tencent.bkrepo.common.artifact.constant.REPO_NAME
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.fs.server.useRequestParam
import org.springframework.util.AntPathMatcher
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.util.pattern.PathPattern
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class DriveNodePathPageRequest(request: ServerRequest) : DriveNodeRequest(
    projectId = request.pathVariable(PROJECT_ID),
    repoName = request.pathVariable(REPO_NAME),
) {
    val fullPath: String = resolveFullPath(request)
    var pageSize: Int = DEFAULT_PAGE_SIZE
    var pageNumber: Int = DEFAULT_PAGE_NUMBER

    init {
        request.useRequestParam("pageSize") { pageSize = it.toInt() }
        request.useRequestParam("pageNumber") { pageNumber = it.toInt() }
    }

    companion object {
        private val antPathMatcher = AntPathMatcher()

        private fun resolveFullPath(request: ServerRequest): String {
            val encodeUrl = AntPathMatcher.DEFAULT_PATH_SEPARATOR + antPathMatcher.extractPathWithinPattern(
                (request.attribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE).get() as PathPattern).patternString,
                request.path(),
            )
            val decodeUrl = URLDecoder.decode(encodeUrl, StandardCharsets.UTF_8.name())
            return PathUtils.normalizeFullPath(decodeUrl)
        }
    }
}
