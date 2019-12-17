package com.tencent.bkrepo.maven.artifact.repository

import com.fasterxml.jackson.databind.ObjectMapper
import com.tencent.bkrepo.common.artifact.exception.ArtifactDownloadException
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.exception.ArtifactValidateException
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.core.AbstractArtifactRepository
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.util.HttpResponseUtils
import com.tencent.bkrepo.maven.pojo.ResultMsg
import com.tencent.bkrepo.maven.pojo.ResultStatusCode
import com.tencent.bkrepo.repository.util.NodeUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import javax.servlet.http.HttpServletResponse

@Component
class MavenLocalRepository : LocalRepository(){
    override fun download(context: ArtifactDownloadContext) {
        val artifactUri = context.artifactInfo.getFullUri()
        val userId = context.userId
        try {
            this.onDownloadValidate(context)
            this.onBeforeDownload(context)
            val file = this.onDownload(context)
                    ?: throw ArtifactNotFoundException("Artifact[$artifactUri] does not exist")
            val name = NodeUtils.getName(context.artifactInfo.artifactUri)
            HttpResponseUtils.response(name, file)
            logger.info("User[$userId] download artifact[$artifactUri] success")
            this.onDownloadSuccess(context, file)
        } catch (validateException: ArtifactValidateException) {
            this.onValidateFailed(context, validateException)
        } catch (artifactNotFoundException: ArtifactNotFoundException) {
            val httpServletResponse = context.response
            httpServletResponse.characterEncoding = "utf-8"
            httpServletResponse.contentType = "application/json; charset=utf-8"
            httpServletResponse.status = HttpServletResponse.SC_NOT_FOUND
            val mapper = ObjectMapper()
            val resultMsg = ResultMsg(ResultStatusCode.NOT_FOUND.errorCode, ResultStatusCode.NOT_FOUND.errorMessage, null)
            httpServletResponse.writer.write(mapper.writeValueAsString(resultMsg))
            return
//            val downloadException = ArtifactDownloadException(exception.message ?: "Download error")
//            this.onDownloadFailed(context, downloadException)
        }

    }

    companion object{
        private val logger = LoggerFactory.getLogger(MavenLocalRepository::class.java)
    }

}