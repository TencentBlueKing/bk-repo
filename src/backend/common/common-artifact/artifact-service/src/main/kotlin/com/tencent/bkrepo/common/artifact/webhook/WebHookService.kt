package com.tencent.bkrepo.common.artifact.webhook

import com.tencent.bkrepo.common.api.constant.StringPool.MEDIA_TYPE_JSON
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.artifact.event.ArtifactEventType
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.LocalConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.webhook.WebHookInfo
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactTransferContext
import com.tencent.bkrepo.common.artifact.util.http.HttpClientBuilderFactory
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class WebHookService {

    private val httpClient = HttpClientBuilderFactory.create().build()

    private val jsonMediaType = MediaType.parse(MEDIA_TYPE_JSON)

    fun hook(context: ArtifactTransferContext, type: ArtifactEventType) {
        if (context.repositoryConfiguration is LocalConfiguration) {
            val configuration = context.repositoryConfiguration as LocalConfiguration
            val artifact = context.artifactInfo
            configuration.webHookConfiguration?.webHookInfoList?.let {
                val data = ArtifactWebHookData(artifact.projectId, artifact.repoName, artifact.artifactUri, type)
                val requestBody = RequestBody.create(jsonMediaType, JsonUtils.objectMapper.writeValueAsString(data))
                it.forEach { info -> remoteCall(info, requestBody) }
            }
        }
    }

    private fun remoteCall(webHookInfo: WebHookInfo, requestBody: RequestBody) {
        try {
            val builder = Request.Builder().url(webHookInfo.url).post(requestBody)
            webHookInfo.headers?.forEach { key, value -> builder.addHeader(key, value) }
            val request = builder.build()
            val response = httpClient.newCall(request).execute()
            assert(response.isSuccessful)
            logger.info("Execute web hook[$webHookInfo] success.")
        } catch (exception: Exception) {
            logger.error("Execute web hook[$webHookInfo] error.", exception)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(WebHookService::class.java)
    }

}