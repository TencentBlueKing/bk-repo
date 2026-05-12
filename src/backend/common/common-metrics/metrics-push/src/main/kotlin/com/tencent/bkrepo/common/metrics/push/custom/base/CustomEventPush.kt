package com.tencent.bkrepo.common.metrics.push.custom.base

import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.api.util.okhttp.HttpClientBuilderFactory
import com.tencent.bkrepo.common.metrics.push.custom.config.CustomEventConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

class CustomEventPush(
    private val config: CustomEventConfig,
) {

    // HttpClientBuilderFactory.create() 共享 defaultClient 的 ConnectionPool，但 Dispatcher 独立新建。
    // bean 随 JVM 生命周期存活，不在此释放；shutdown 由容器处理。
    private val httpClient = HttpClientBuilderFactory.create()
        .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    init {
        validateConfig()
    }

    // 启用上报时必须配置完整，否则直接 fail-fast，避免启动成功却运行时 100% 失败
    private fun validateConfig() {
        require(config.url.isNotBlank()) { "custom event url must not be blank when event reporting enabled" }
        require(config.dataId > 0) { "custom event dataId must be positive (current=${config.dataId})" }
        require(config.accessToken.isNotBlank()) { "custom event accessToken must not be blank" }
        require(config.batchSize > 0) { "batchSize must be positive" }
        require(config.maxQueueSize > 0) { "maxQueueSize must be positive" }
        require(!config.pushRate.isZero && !config.pushRate.isNegative) { "pushRate must be positive" }
    }

    /** 失败即丢弃，不重试；事件上报定位为尽力而为，不引入重入队/熔断等复杂度 */
    fun push(events: List<CustomEventItem>): Boolean {
        if (events.isEmpty()) return true
        return try {
            doPost(buildRequest(events).toJsonString())
            true
        } catch (e: Exception) {
            logger.warn("push custom event failed, url: ${config.url}, errmsg: ${e.message}")
            false
        }
    }

    private fun buildRequest(events: List<CustomEventItem>): CustomEventRequest {
        return CustomEventRequest(
            dataId = config.dataId,
            accessToken = config.accessToken,
            data = events.map { it.toEventData() },
        )
    }

    private fun CustomEventItem.toEventData() = CustomEventData(
        eventName = eventName,
        event = CustomEventContent(content = content, extra = extra),
        target = target,
        dimension = dimension,
        timestamp = timestamp,
    )

    private fun doPost(body: String) {
        val request = Request.Builder()
            .url(config.url)
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("http ${response.code}")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CustomEventPush::class.java)
        private const val CONNECT_TIMEOUT_MS = 5_000L
        private const val READ_TIMEOUT_MS = 10_000L
        private const val WRITE_TIMEOUT_MS = 10_000L
        private val JSON_MEDIA_TYPE = "application/json; charset=UTF-8".toMediaType()
    }
}
