package com.tencent.bkrepo.job.batch.task.other

import com.google.common.util.concurrent.RateLimiter
import com.tencent.bkrepo.common.notify.api.NotifyService
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.notify.api.weworkbot.FileMessage
import com.tencent.bkrepo.common.notify.api.weworkbot.WeworkBotChannelCredential
import com.tencent.bkrepo.common.notify.api.weworkbot.WeworkBotMessage
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.base.MongoDbBatchJob
import com.tencent.bkrepo.job.batch.utils.TimeUtils
import com.tencent.bkrepo.job.config.properties.CheckUserStatusJobProperties
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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
    private val failedUsers = Collections.synchronizedList(mutableListOf<User>())
    private val jobName = getJobName()

    private val okHttpClient = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS).build()

    private val rateLimiter = RateLimiter.create(
        properties.apiRateLimit.coerceAtLeast(1.0),
        500L,  // 增加500ms预热时间
        TimeUnit.MILLISECONDS
    )

    override fun collectionNames(): List<String> = listOf(COLLECTION_NAME_USER)

    override fun getLockAtMostFor(): Duration = Duration.ofDays(1)

    override fun doStart0(jobContext: JobContext) {
        if (!checkProperties()) return
        inactiveUsers.clear()
        failedUsers.clear()
        super.doStart0(jobContext)
        sendWechatMessage(inactiveUsers)
        inactiveUsers.clear()
        failedUsers.clear()
    }

    override fun buildQuery(): Query {
        return Query.query(Criteria.where(User::locked.name).`is`(false))
    }

    override fun run(row: User, collectionName: String, context: JobContext) {
        try {
            when (checkUserStatus(row.userId)) {
                true -> inactiveUsers.add(row)
                false -> {}
                null -> failedUsers.add(row)
            }
        } catch (e: Exception) {
            failedUsers.add(row)
        }
    }

    override fun mapToEntity(row: Map<String, Any?>): User {
        return User(
            userId = row[User::userId.name].toString(),
            name = row[User::name.name]?.toString(),
            email = row[User::email.name]?.toString(),
            locked = row[User::locked.name]?.toString()?.toBoolean() ?: false,
            lastModifiedDate = TimeUtils.parseMongoDateTimeStr(row[User::lastModifiedDate.name].toString())
        )
    }

    override fun entityClass(): KClass<User> = User::class

    override fun createJobContext(): JobContext = JobContext()

    data class User(
        val userId: String,
        val name: String?,
        val email: String?,
        val locked: Boolean = false,
        val lastModifiedDate: LocalDateTime? = null
    )

    fun checkUserStatus(userId: String): Boolean? {
        return try {
            rateLimiter.acquire()
            val normalizedUrl = properties.checkUserUrl
                .trim()
                .removeSuffix("/")

            val request = Request.Builder()
                .url("$normalizedUrl?login_name=$userId")
                .addHeader(
                    "X-Bkapi-Authorization",
                    JsonUtils.objectMapper.writeValueAsString(mapOf("access_token" to properties.bkAccessToken))
                        .replace("\\s".toRegex(), "")
                )
                .get()
                .build()


            val responseContent = doRequest(okHttpClient, request, RETRY_COUNT)
            val json = JsonUtils.objectMapper.readTree(responseContent)

            // 解析状态
            when {
                json.get("code").asText() != "00" -> {
                    throw RuntimeException(json.get("message").asText())
                }

                json.get("data").isEmpty -> {
                    null
                }

                else -> {
                    json.get("data").get("StatusId").asText() !in setOf("1", "3")
                }
            }
        } catch (e: Exception) {
            logger.warn("Job[$jobName]: Failed to check status for user: $userId. Error: ${e.message}")
            throw e
        }
    }

    private fun sendWechatMessage(inactiveUsers: List<User>) {
        if (inactiveUsers.isEmpty() && failedUsers.isEmpty()) {
            logger.info("Job[$jobName]: No inactive or failed users found. Skipping notification.")
            return
        }

        val receivers = properties.receivers
        val reportContent = buildString {
            if (inactiveUsers.isNotEmpty()) {
                append("发现 ${inactiveUsers.size} 名离职用户：\n")
                inactiveUsers.joinTo(this, "\n") { user ->
                    "ID: ${user.userId}, 姓名: ${user.name}, " +
                        "邮箱: ${user.email ?: "无"}, 最后活跃: ${user.lastModifiedDate ?: "无记录"}"
                }
            }

            if (failedUsers.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("发现 ${failedUsers.size} 名用户检查失败：\n")
                failedUsers.joinTo(this, "\n") { user ->
                    "ID: ${user.userId}, 姓名: ${user.name}"
                }
            }
        }

        try {
            val credential = WeworkBotChannelCredential(key = properties.checkBotKey)

            val reportDir = File("/data/tmp/report/user/")
            if (!reportDir.exists() && !reportDir.mkdirs()) {
                throw RuntimeException("Failed to create report directory: ${reportDir.absolutePath}")
            }

            val dateFormat = DateTimeFormatter.ofPattern("yyyyMMdd")
            val dateStr = LocalDateTime.now().format(dateFormat)
            val reportFile = File(reportDir, "user-report-$dateStr.txt")
            reportFile.writeText(reportContent, Charsets.UTF_8)
            val mediaId = uploadFile(reportFile, credential)
            logger.info("Job[$jobName]: Successfully uploaded file. Media ID: $mediaId")

            // 发送文件
            notifyService.send(WeworkBotMessage(FileMessage(mediaId), receivers), credential)
            logger.info("Job[$jobName]: Sent WeChat file notification to ${receivers.joinToString()}")

            // 删除临时文件
            if (reportFile.delete()) {
                logger.info("Job[$jobName]: Deleted temporary file: ${reportFile.absolutePath}")
            } else {
                logger.warn("Job[$jobName]: Failed to delete temporary file: ${reportFile.absolutePath}")
            }
        } catch (e: Exception) {
            logger.error("Job[$jobName]: Failed to send WeChat notification. Reason: ${e.message}", e)
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
            logger.error("Job[$jobName]: Missing required configuration properties: ${missingFields.joinToString()}")
            return false
        }
        return true
    }

    private fun uploadFile(file: File, credential: WeworkBotChannelCredential): String {
        val url = "$DEFAULT_API_HOST/cgi-bin/webhook/upload_media?key=${credential.key}&type=file"
        logger.debug("Job[$jobName]: Uploading file to WeChat API: $url")

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "media",
                file.name,
                file.asRequestBody("application/octet-stream".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val responseContent = doRequest(okHttpClient, request, RETRY_COUNT)
        val json = JsonUtils.objectMapper.readTree(responseContent)

        if (json.get("errcode").asInt() != 0) {
            val errorCode = json.get("errcode").asInt()
            val errorMsg = json.get("errmsg").asText()
            throw RuntimeException("WeChat API error [$errorCode]: $errorMsg")
        }

        return json.get("media_id").asText()
    }

    private fun doRequest(okHttpClient: OkHttpClient, request: Request, retryCount: Int = 0): String {
        try {
            okHttpClient.newBuilder().build().newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw RuntimeException("HTTP request failed with status ${response.code}. URL: ${request.url}")
                }
                return response.body!!.string().also {
                    logger.debug("Job[$jobName]: Received response: $it")
                }
            }
        } catch (e: Exception) {
            if (retryCount > 0) {
                logger.warn("Job[$jobName]: HTTP request error. Error: ${e.message}")
            } else {
                logger.error("Job[$jobName]: HTTP request failed after retries. URL: ${request.url}", e)
            }
        }
        if (retryCount > 0) {
            Thread.sleep(1000)
            return doRequest(okHttpClient, request, retryCount - 1)
        } else {
            throw RuntimeException("HTTP request failed after $RETRY_COUNT retries. URL: ${request.url}")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CheckUserStatusJob::class.java)
        private const val DEFAULT_API_HOST = "https://qyapi.weixin.qq.com"
        private const val COLLECTION_NAME_USER = "user"
        private const val RETRY_COUNT = 3
    }
}