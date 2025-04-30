package com.tencent.bkrepo.job.batch.task.other

import com.tencent.bkrepo.auth.model.TUser
import com.tencent.bkrepo.common.notify.api.NotifyService
import com.tencent.bkrepo.auth.util.HttpUtils
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.notify.api.weworkbot.TextMessage
import com.tencent.bkrepo.common.notify.api.weworkbot.WeworkBotChannelCredential
import com.tencent.bkrepo.common.notify.api.weworkbot.WeworkBotMessage
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.base.MongoDbBatchJob
import com.tencent.bkrepo.job.batch.utils.TimeUtils
import com.tencent.bkrepo.job.config.properties.CheckUserStatusJobProperties
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

/**
 * 用户在职状态检查
 */
@Component
@EnableConfigurationProperties(CheckUserStatusJobProperties::class)
class CheckUserStatusJob(
    private val properties: CheckUserStatusJobProperties,
    private val notifyService: NotifyService
) : MongoDbBatchJob<CheckUserStatusJob.User, JobContext>(properties) {

    private val inactiveUsers = Collections.synchronizedList(mutableListOf<User>())

    override fun collectionNames(): List<String> = listOf(COLLECTION_NAME_USER)

    override fun getLockAtMostFor(): Duration = Duration.ofDays(1)

    override fun doStart0(jobContext: JobContext) {
        if (!checkProperties()) return
        super.doStart0(jobContext)
        sendWechatMessage(inactiveUsers)
    }

    override fun buildQuery(): Query {
        return Query.query(Criteria.where(TUser::locked.name).`is`(false))
    }

    override fun run(row: User, collectionName: String, context: JobContext) {
        if (checkUserStatus(row.userId))
            inactiveUsers.add(row)
    }

    override fun mapToEntity(row: Map<String, Any?>): User {
        return User(
            userId = row[User::userId.name].toString(),
            name = row[User::name.name]?.toString(),
            email = row[User::email.name]?.toString(),
            lastModifiedDate = TimeUtils.parseMongoDateTimeStr(row[User::lastModifiedDate.name].toString())
        )
    }

    override fun entityClass(): KClass<User> = User::class

    override fun createJobContext(): JobContext = JobContext()

    data class User(
        val userId: String,
        val name: String?,
        val email: String?,
        val lastModifiedDate: LocalDateTime? = null
    )

    private fun checkUserStatus(userId: String): Boolean {
        return try {
            // 构建请求
            val normalizedUrl = properties.checkUserUrl
                .trim()
                .removeSuffix("/")

            val request = Request.Builder()
                .url("$normalizedUrl?login_name=$userId")
                .addHeader("X-Bkapi-Authorization",
                    JsonUtils.objectMapper.writeValueAsString(mapOf("access_token" to properties.bkAccessToken))
                        .replace("\\s".toRegex(), ""))
                .get()
                .build()

            // 执行请求并解析
            val response = HttpUtils.doRequest(okHttpClient, request, RETRY_COUNT)
            val json = JsonUtils.objectMapper.readTree(response.content)

            // 解析状态
            when {
                json.get("data").isEmpty -> {
                    throw RuntimeException(json.get("message").toString())
                }
                json.get("data").get("StatusId").asText() in setOf("1", "3") -> false
                else -> true
            }
        } catch (e: Exception) {
            logger.warn("Failed to check status for user: $userId", e.message)
            throw e
        }
    }

    private fun sendWechatMessage(inactiveUsers: List<User>) {
        if (inactiveUsers.isEmpty()) return

        val receivers = properties.receivers
        val body = buildString {
            append("发现 ${inactiveUsers.size} 名离职用户：\n")
            inactiveUsers.joinTo(this, "\n") { user ->
                "ID: ${user.userId}, 姓名: ${user.name}, " +
                        "邮箱: ${user.email ?: "无"}, 最后活跃: ${user.lastModifiedDate ?: "无记录"}"
            }
        }

        try {
            val credential = WeworkBotChannelCredential(key = properties.checkBotKey)
            notifyService.send(WeworkBotMessage(TextMessage(body), receivers), credential)
            logger.info("Sent wechat notification to ${receivers.joinToString()}")
        } catch (e: Exception) {
            logger.error("Failed to send wechat notification", e)
        }
    }

    private fun checkProperties(): Boolean {
        val missingFields = mutableListOf<String>().apply {
            if (properties.checkUserUrl.isEmpty()) add("checkUserUrl")
            if (properties.checkBotKey.isEmpty()) add("checkBotKey")
            if (properties.bkAccessToken.isEmpty()) add("bkAccessToken")
            if (properties.receivers.isEmpty()) add("receivers")
        }

        if (missingFields.isNotEmpty()) {
            logger.error("Missing required fields for CheckUserStatusJob: ${missingFields.joinToString()}")
            return false
        }
        return true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS).build()

    companion object {
        private val logger = LoggerFactory.getLogger(CheckUserStatusJob::class.java)
        private const val COLLECTION_NAME_USER = "user"
        private const val RETRY_COUNT = 3
    }
}