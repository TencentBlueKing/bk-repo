package com.tencent.bkrepo.common.artifact.webhook

import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.event.ArtifactEventType
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.LocalConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.webhook.WebHookSetting
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.util.okhttp.HttpClientBuilderFactory
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.IOException

@Service
class WebHookService {

    private val httpClient = HttpClientBuilderFactory.create().build()

    private val jsonMediaType = MediaType.parse(MediaTypes.APPLICATION_JSON)

    fun hook(context: ArtifactContext, type: ArtifactEventType) {
        if (context.getConfiguration() is LocalConfiguration) {
            val configuration = context.getLocalConfiguration()
            val artifact = context.artifactInfo
            configuration.webHook.webHookList.takeIf { it.isNotEmpty() }?.run {
                val data = ArtifactWebHookData(artifact.projectId, artifact.repoName, artifact.artifact, artifact.version, type)
                val requestBody = RequestBody.create(jsonMediaType, data.toJsonString())
                this.forEach { info -> remoteCall(info, requestBody) }
            }
        }
    }

    private fun remoteCall(webHookSetting: WebHookSetting, requestBody: RequestBody) {
        try {
            val builder = Request.Builder().url(webHookSetting.url).post(requestBody)
            webHookSetting.headers?.forEach { key, value -> builder.addHeader(key, value) }
            val request = builder.build()
            val response = httpClient.newCall(request).execute()
            assert(response.isSuccessful)
            logger.info("Execute web hook[$webHookSetting] success.")
        } catch (exception: IOException) {
            logger.error("Execute web hook[$webHookSetting] error.", exception)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(WebHookService::class.java)
    }
}
