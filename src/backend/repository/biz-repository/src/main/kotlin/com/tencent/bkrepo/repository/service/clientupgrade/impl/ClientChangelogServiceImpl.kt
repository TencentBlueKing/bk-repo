package com.tencent.bkrepo.repository.service.clientupgrade.impl

import com.tencent.bkrepo.common.api.exception.BadRequestException
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.repository.dao.ClientChangelogDao
import com.tencent.bkrepo.repository.model.TClientChangelog
import com.tencent.bkrepo.repository.pojo.clientupgrade.ClientChangelogEntry
import com.tencent.bkrepo.repository.pojo.clientupgrade.ClientChangelogListOption
import com.tencent.bkrepo.repository.pojo.clientupgrade.ClientChangelogStatus
import com.tencent.bkrepo.repository.pojo.clientupgrade.ClientChangelogUpsertRequest
import com.tencent.bkrepo.repository.pojo.clientupgrade.ClientChangelogVo
import com.tencent.bkrepo.repository.service.clientupgrade.ClientChangelogService
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ClientChangelogServiceImpl(
    private val clientChangelogDao: ClientChangelogDao,
) : ClientChangelogService {

    override fun findPublishedEntry(
        productId: String,
        version: String,
    ): ClientChangelogEntry? {
        val pid = requireNotBlank(productId, "productId")
        val ver = requireNotBlank(version, "version")
        val record = clientChangelogDao.findPublished(
            productId = normalizeKey(pid)!!,
            version = trimValue(ver)!!,
        ) ?: return null
        return record.toEntry()
    }

    override fun pagePublishedHistory(
        productId: String,
        pageNumber: Int,
        pageSize: Int,
    ): Page<ClientChangelogEntry> {
        val pid = normalizeKey(requireNotBlank(productId, "productId"))!!
        val page = clientChangelogDao.pagePublished(pid, pageNumber, pageSize)
        return Page(
            pageNumber = page.pageNumber,
            pageSize = page.pageSize,
            totalRecords = page.totalRecords,
            totalPages = page.totalPages,
            records = page.records.map { it.toEntry() },
        )
    }

    override fun listPage(option: ClientChangelogListOption): Page<ClientChangelogVo> {
        val page = clientChangelogDao.pageByOption(option)
        return Page(
            pageNumber = page.pageNumber,
            pageSize = page.pageSize,
            totalRecords = page.totalRecords,
            totalPages = page.totalPages,
            records = page.records.map { it.toVo() },
        )
    }

    override fun getById(id: String): ClientChangelogVo {
        val record = findRequiredRecord(id)
        return record.toVo()
    }

    override fun getByKey(
        productId: String,
        version: String,
    ): ClientChangelogVo {
        val pid = normalizeKey(requireNotBlank(productId, "productId"))!!
        val ver = trimValue(requireNotBlank(version, "version"))!!
        val record = clientChangelogDao.findByKey(pid, ver)
            ?: throw NotFoundException(
                CommonMessageCode.RESOURCE_NOT_FOUND,
                "client_changelog($pid,$ver)",
            )
        return record.toVo()
    }

    override fun upsert(userId: String, request: ClientChangelogUpsertRequest): ClientChangelogVo {
        validateRequest(request)
        val pid = normalizeKey(request.productId)!!
        val ver = trimValue(request.version)!!
        val releaseNotes = request.releaseNotes.trim()
        val now = LocalDateTime.now()

        val requestId = request.id?.trim()?.takeIf { it.isNotEmpty() }
        val existing = when {
            requestId != null -> findRequiredRecord(requestId)
            else -> clientChangelogDao.findByKey(pid, ver)
        }

        val saved = if (existing == null) {
            val record = TClientChangelog(
                createdBy = userId,
                createdDate = now,
                lastModifiedBy = userId,
                lastModifiedDate = now,
                productId = pid,
                version = ver,
                releasedAt = trimValue(request.releasedAt)!!,
                status = request.status.name,
                releaseNotes = releaseNotes,
            )
            try {
                clientChangelogDao.insert(record)
            } catch (e: DuplicateKeyException) {
                logger.warn(
                    "[client-changelog] duplicate key on insert: pid={}, ver={}",
                    pid, ver,
                )
                throw ErrorCodeException(CommonMessageCode.RESOURCE_EXISTED, "client_changelog($pid,$ver)")
            }
        } else {
            // 通过 id 路径修改时，不允许把唯一键改到与其他记录冲突
            if (requestId != null) {
                val sameKey = clientChangelogDao.findByKey(pid, ver)
                if (sameKey != null && sameKey.id != existing.id) {
                    throw ErrorCodeException(
                        CommonMessageCode.RESOURCE_EXISTED,
                        "client_changelog($pid,$ver)",
                    )
                }
            }
            existing.apply {
                this.productId = pid
                this.version = ver
                this.releasedAt = trimValue(request.releasedAt)!!
                this.status = request.status.name
                this.releaseNotes = releaseNotes
                this.lastModifiedBy = userId
                this.lastModifiedDate = now
            }
            clientChangelogDao.save(existing)
        }
        return saved.toVo()
    }

    override fun remove(userId: String, id: String) {
        if (id.isBlank()) {
            throw BadRequestException(code = CommonMessageCode.PARAMETER_EMPTY, "id")
        }
        clientChangelogDao.findById(id)
            ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, "client_changelog($id)")
        clientChangelogDao.removeById(id)
    }

    private fun findRequiredRecord(id: String): TClientChangelog {
        if (id.isBlank()) {
            throw BadRequestException(code = CommonMessageCode.PARAMETER_EMPTY, "id")
        }
        return clientChangelogDao.findById(id)
            ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, "client_changelog($id)")
    }

    private fun validateRequest(request: ClientChangelogUpsertRequest) {
        requireNotBlank(request.productId, "productId")
        requireNotBlank(request.version, "version")
        requireNotBlank(request.releasedAt, "releasedAt")
        if (request.releaseNotes.isBlank()) {
            throw BadRequestException(CommonMessageCode.PARAMETER_INVALID, "releaseNotes must not be blank")
        }
    }

    private fun TClientChangelog.toEntry() = ClientChangelogEntry(
        version = version,
        releasedAt = releasedAt,
        releaseNotes = releaseNotes,
    )

    private fun TClientChangelog.toVo() = ClientChangelogVo(
        id = id,
        productId = productId,
        version = version,
        releasedAt = releasedAt,
        status = runCatching { ClientChangelogStatus.valueOf(status) }
            .getOrDefault(ClientChangelogStatus.DRAFT),
        releaseNotes = releaseNotes,
        createdBy = createdBy,
        createdDate = createdDate,
        lastModifiedBy = lastModifiedBy,
        lastModifiedDate = lastModifiedDate,
    )

    private fun requireNotBlank(value: String?, field: String): String {
        if (value.isNullOrBlank()) {
            throw BadRequestException(code = CommonMessageCode.PARAMETER_EMPTY, field)
        }
        return value
    }

    private fun normalizeKey(value: String?): String? {
        return value?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
    }

    private fun trimValue(value: String?): String? {
        return value?.trim()?.takeIf { it.isNotEmpty() }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ClientChangelogServiceImpl::class.java)
    }
}
