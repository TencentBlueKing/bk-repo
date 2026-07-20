package com.tencent.bkrepo.common.metadata.service.shortlink.impl

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.dao.shortlink.ShortLinkDao
import com.tencent.bkrepo.common.metadata.model.TShortLink
import com.tencent.bkrepo.common.metadata.pojo.shortlink.ShortLink
import com.tencent.bkrepo.common.metadata.pojo.shortlink.ShortLinkCreateRequest
import com.tencent.bkrepo.common.metadata.pojo.shortlink.ShortLinkListOption
import com.tencent.bkrepo.common.metadata.properties.ShortLinkProperties
import com.tencent.bkrepo.common.metadata.service.shortlink.ShortLinkService
import com.tencent.bkrepo.common.metadata.util.ShortLinkHelper
import com.tencent.bkrepo.common.metadata.util.ShortLinkHelper.toShortLink
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Service

/**
 * 短链接服务实现
 */
@Service
@Conditional(SyncCondition::class)
class ShortLinkServiceImpl(
    private val shortLinkDao: ShortLinkDao,
    private val shortLinkProperties: ShortLinkProperties,
) : ShortLinkService {

    override fun create(request: ShortLinkCreateRequest): ShortLink {
        ShortLinkHelper.validateTarget(request.target, shortLinkProperties.allowedHosts)
        val expiredDate = ShortLinkHelper.resolveExpiredDate(request, shortLinkProperties)
        repeat(ShortLinkHelper.MAX_CODE_RETRY) {
            val code = ShortLinkHelper.generateCode()
            val entity = ShortLinkHelper.buildEntity(request, code, expiredDate)
            try {
                val saved = shortLinkDao.insert(entity)
                logger.info("User[${request.createdBy}] create short link [$code] -> [${request.target}]")
                return saved.toShortLink(shortLinkProperties.publicHost)
            } catch (_: DuplicateKeyException) {
                logger.warn("Short link code [$code] collided, retrying")
            }
        }
        throw ErrorCodeException(CommonMessageCode.SYSTEM_ERROR, "shortlink code generate failed")
    }

    override fun resolve(code: String, scheme: String, host: String): String {
        val record = shortLinkDao.findByCode(code)
            ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, code)
        ShortLinkHelper.ensureNotExpired(record)
        return ShortLinkHelper.resolveAbsoluteUrl(record.target, scheme, host)
    }

    override fun get(code: String): ShortLink? {
        return shortLinkDao.findByCode(code)?.toShortLink(shortLinkProperties.publicHost)
    }

    override fun delete(code: String) {
        if (!shortLinkDao.deleteByCode(code)) {
            throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, code)
        }
        logger.info("Delete short link [$code]")
    }

    override fun listByCreator(option: ShortLinkListOption): Page<ShortLink> {
        with(option) {
            val pageRequest = Pages.ofRequest(pageNumber, pageSize)
            val query = Query(TShortLink::createdBy.isEqualTo(createdBy))
                .with(Sort.by(Sort.Direction.DESC, TShortLink::createdDate.name))
            val totalRecords = shortLinkDao.count(query)
            val records = shortLinkDao.find(query.with(pageRequest))
                .map { it.toShortLink(shortLinkProperties.publicHost) }
            return Pages.ofResponse(pageRequest, totalRecords, records)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ShortLinkServiceImpl::class.java)
    }
}
