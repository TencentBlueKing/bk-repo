package com.tencent.bkrepo.common.artifact.resolve.file

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import org.slf4j.LoggerFactory
import org.springframework.web.servlet.HandlerInterceptor
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import kotlin.system.measureTimeMillis

class ArtifactFileCleanInterceptor: HandlerInterceptor {
    override fun afterCompletion(request: HttpServletRequest, response: HttpServletResponse, handler: Any, ex: Exception?) {
        val artifactFileList = request.getAttribute(ArtifactFileFactory.ARTIFACT_FILES) as? List<ArtifactFile>
        artifactFileList?.forEach {
            val filename = it.getFile().name
            measureTimeMillis { it.delete() }.apply {
                logger.info("Delete temp artifact file [$filename] success, elapse $this ms")
            }

        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArtifactFileCleanInterceptor::class.java)
    }
}