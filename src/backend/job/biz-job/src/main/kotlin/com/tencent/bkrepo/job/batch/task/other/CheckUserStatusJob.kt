package com.tencent.bkrepo.job.batch.task.other

import com.tencent.bkrepo.auth.model.TUser
import com.tencent.bkrepo.common.notify.api.NotifyService
import com.tencent.bkrepo.auth.pojo.ApiResponse
import com.tencent.bkrepo.auth.util.HttpUtils
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.notify.api.weworkbot.TextMessage
import com.tencent.bkrepo.common.notify.api.weworkbot.WeworkBotChannelCredential
import com.tencent.bkrepo.common.notify.api.weworkbot.WeworkBotMessage
import com.tencent.bkrepo.job.BATCH_SIZE
import com.tencent.bkrepo.job.batch.base.DefaultContextJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.config.properties.CheckUserStatusJobProperties
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * 用户在职状态检查
 */
@Component
@EnableConfigurationProperties(CheckUserStatusJobProperties::class)
class CheckUserStatusJob(
    val properties: CheckUserStatusJobProperties,
    private val mongoTemplate: MongoTemplate,
    private val notifyService: NotifyService
) : DefaultContextJob(properties) {

    override fun doStart0(jobContext: JobContext) {
        logger.info("Starting employee status check task...")

        if (!checkUserStatusJobProperties()) return

        val inactiveUsers = Collections.synchronizedList(mutableListOf<TUser>())
        val totalProcessed = processUsersInBatches(inactiveUsers)

        logger.info("Check completed, total processed $totalProcessed users, " +
                "found ${inactiveUsers.size} inactive employees")

        if (inactiveUsers.isNotEmpty()) {
            sendWechatMessage(inactiveUsers)
            jobContext.success.addAndGet(inactiveUsers.size.toLong())
        }
        jobContext.total.addAndGet(totalProcessed.toLong())
    }

    override fun getLockAtMostFor(): Duration = Duration.ofDays(1)

    private fun processUsersInBatches(inactiveUsers: MutableList<TUser>): Int {
        val criteria = Criteria.where(TUser::locked.name).`is`(false)

        mongoTemplate.stream(
            Query(criteria),
            TUser::class.java,
            COLLECTION_NAME_USER
        ).use { cursor ->
            runBlocking {
                val jobs = mutableListOf<Deferred<Unit>>()
                var totalProcessed = 0
                cursor.forEach { user: TUser ->
                    jobs.add(processSingleUserAsync(user, inactiveUsers))
                    if (jobs.size >= BATCH_SIZE) {
                        jobs.awaitAll()
                        totalProcessed += jobs.size
                        jobs.clear()
                    }
                }
                // 处理最后一批不足BATCH_SIZE的任务
                if (jobs.isNotEmpty()) {
                    jobs.awaitAll()
                    totalProcessed += jobs.size
                }
                logger.info("All unlocked user accounts processed, total $totalProcessed users")
                totalProcessed // 返回处理的总用户数
            }.let { return it }
        }
    }

    private suspend fun processSingleUserAsync(user: TUser, inactiveUsers: MutableList<TUser>) = coroutineScope {
        async(Dispatchers.IO) {
            try {
                if (!checkUserStatus(user.userId)) {
                    synchronized(inactiveUsers) {
                        inactiveUsers.add(user)
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to check status for user: ${user.userId}", e)
            }
        }
    }

    private fun checkUserStatus(userId: String): Boolean {
        val request = buildCheckUserRequest(userId)
        try{
            val response = HttpUtils.doRequest(okHttpClient, request, RETRY_COUNT)
            return parseActiveStatus(response)
        } catch (e: Exception) {
            logger.warn("Failed to check status for user: $userId", e)
            return false
        }
    }

    private fun buildCheckUserRequest(userId: String): Request {
        val authHeader = mapOf(
            "access_token" to properties.bkAccessToken
        ).toJsonString().replace("\\s".toRegex(), "")

        return Request.Builder()
            .url("${properties.checkUserUrl}?login_name=$userId")
            .addHeader("X-Bkapi-Authorization", authHeader)
            .get()
            .build()
    }

    private fun parseActiveStatus(response: ApiResponse): Boolean {
        val jsonNode = JsonUtils.objectMapper.readTree(response.content)
        val data = jsonNode.get("data")
        if (data.isEmpty) {
            logger.error("Empty response body for user status check, response meassage: "
                    + jsonNode.get("message").toString())
            return true
        }
        val statusId = data.get("StatusId").asText()
        // 有效状态ID为1（在职）和3（试用）
        return statusId in setOf("1", "3")
    }

    private fun sendWechatMessage(inactiveUsers: List<TUser>) {
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

    private fun checkUserStatusJobProperties(): Boolean {
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