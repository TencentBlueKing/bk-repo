package com.tencent.bkrepo.common.artifact.resolve.file

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import org.slf4j.LoggerFactory
import org.springframework.web.servlet.HandlerInterceptor
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import kotlin.system.measureTimeMillis

class ArtifactFileCleanInterceptor : HandlerInterceptor {
    @Suppress("UNCHECKED_CAST")
    override fun afterCompletion(request: HttpServletRequest, response: HttpServletResponse, handler: Any, ex: Exception?) {
        try {
            val artifactFileList = request.getAttribute(ArtifactFileFactory.ARTIFACT_FILES) as? List<ArtifactFile>
            artifactFileList?.filter { !it.isInMemory() }?.forEach {
                val absolutePath = it.getFile()!!.absolutePath
                measureTimeMillis { it.delete() }.apply {
                    logger.info("Delete temp artifact file [$absolutePath] success, elapse $this ms")
                }
            }
        } catch (ex: Exception) {
            logger.error("Failed to clean temp artifact file.", ex)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArtifactFileCleanInterceptor::class.java)
    }
}
