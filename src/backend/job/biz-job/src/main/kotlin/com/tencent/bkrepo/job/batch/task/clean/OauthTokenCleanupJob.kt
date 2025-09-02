package com.tencent.bkrepo.job.batch.task.clean

import com.tencent.bkrepo.common.metadata.constant.ID
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.utils.TimeUtils
import com.tencent.bkrepo.job.config.properties.OauthTokenCleanupJobProperties
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.reflect.KClass


@Component
class OauthTokenCleanupJob(
    private val properties: OauthTokenCleanupJobProperties
) : DefaultContextMongoDbJob<OauthTokenCleanupJob.OauthToken>(properties) {
    override fun collectionNames(): List<String> {
        return listOf(COLLECTION_NAME)
    }

    override fun buildQuery(): Query {
        return Query(where(OauthToken::expireSeconds).gt(0))
    }

    override fun mapToEntity(row: Map<String, Any?>): OauthToken {
        return OauthToken(row)
    }

    override fun entityClass(): KClass<OauthToken> {
        return OauthToken::class
    }

    override fun run(row: OauthToken, collectionName: String, context: JobContext) {
        val accessTokenExpireTime = row.issuedAt.plusSeconds(row.expireSeconds!!)
        val reservedTime = accessTokenExpireTime.plusSeconds(properties.reservedDuration.seconds)
        if (Instant.now().isAfter(reservedTime)) {
            val query = Query(Criteria.where(ID).isEqualTo(row.id))
            mongoTemplate.remove(query, COLLECTION_NAME)
            context.success.incrementAndGet()
            logger.info("clean up oauth token: ${row.id}, ${row.issuedAt.epochSecond}, ${row.expireSeconds}")
        }
        context.total.incrementAndGet()
    }

    override fun getLockAtMostFor(): Duration {
        return Duration.ofDays(7)
    }

    data class OauthToken(
        var id: String,
        var issuedAt: Instant,
        val expireSeconds: Long?,
    ) {
        constructor(map: Map<String, Any?>) : this(
            map[OauthToken::id.name].toString(),
            TimeUtils.parseMongoDateTimeStr(map[OauthToken::issuedAt.name].toString())
                ?.atZone(ZoneId.systemDefault())
                ?.toInstant() ?: Instant.now(),
            map[OauthToken::expireSeconds.name]?.toString()?.toLong(),
        )
    }

    companion object {
        private const val COLLECTION_NAME = "oauth_token"
        private val logger = LoggerFactory.getLogger(OauthTokenCleanupJob::class.java)
    }

}
