package com.tencent.bkrepo.common.metadata.service.sign

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.dao.sign.SignConfigDao
import com.tencent.bkrepo.common.metadata.model.TSignConfig
import com.tencent.bkrepo.common.metadata.pojo.sign.SignConfig
import com.tencent.bkrepo.common.metadata.pojo.sign.SignConfigCreateRequest
import com.tencent.bkrepo.common.metadata.pojo.sign.SignConfigListOption
import com.tencent.bkrepo.common.metadata.pojo.sign.SignConfigUpdateRequest
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.security.util.SecurityUtils
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 签名配置服务实现类
 */
@Service
@Conditional(SyncCondition::class)
class SignConfigServiceImpl(
    private val signConfigDao: SignConfigDao
) : SignConfigService {

    override fun create(request: SignConfigCreateRequest): SignConfig {
        // 检查项目ID是否已存在
        if (signConfigDao.existsByProjectId(request.projectId)) {
            throw ErrorCodeException(CommonMessageCode.RESOURCE_EXISTED, request.projectId)
        }
        val userId = SecurityUtils.getUserId()
        val tSignConfig = with(request) {
            TSignConfig(
                projectId = projectId,
                scanner = scanner,
                tags = tags,
                createdBy = userId,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = userId,
                lastModifiedDate = LocalDateTime.now()
            )
        }
        val signConfig = signConfigDao.save(tSignConfig).convert()
        logger.info("User[$userId] create sign config success: $signConfig")
        return signConfig
    }

    override fun find(projectId: String): SignConfig? {
        return signConfigDao.findByProjectId(projectId)?.convert()
    }

    override fun update(request: SignConfigUpdateRequest): SignConfig {
        val existingConfig = signConfigDao.findByProjectId(request.projectId)
            ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_EXISTED, request.projectId)
        val userId = SecurityUtils.getUserId()
        val updatedConfig = existingConfig.copy(
            scanner = request.scanner,
            tags = request.tags,
            lastModifiedBy = userId,
            lastModifiedDate = LocalDateTime.now()
        )
        val signConfig = signConfigDao.save(updatedConfig).convert()
        logger.info("User[$userId] update sign config success: $signConfig")
        return signConfig
    }

    override fun delete(projectId: String): Boolean {
        logger.info("User[${SecurityUtils.getUserId()}] delete sign config: $projectId")
        return signConfigDao.deleteByProjectId(projectId)
    }

    override fun exists(projectId: String): Boolean {
        return signConfigDao.existsByProjectId(projectId)
    }

    override fun findPage(option: SignConfigListOption): Page<SignConfig> {
        with(option) {
            val pageRequest = Pages.ofRequest(pageNumber, pageSize)
            val query = if (projectId.isNullOrEmpty()) {
                Query()
            } else {
                Query(where(TSignConfig::projectId).isEqualTo(projectId))
            }
            val totalRecords = signConfigDao.count(query)
            val records = signConfigDao.find(query.with(pageRequest)).map { it.convert() }
            return Pages.ofResponse(pageRequest, totalRecords, records)
        }
    }

    private fun TSignConfig.convert(): SignConfig {
        return SignConfig(
            projectId = projectId,
            scanner = scanner,
            tags = tags,
            createdBy = createdBy,
            createdDate = createdDate,
            lastModifiedBy = lastModifiedBy,
            lastModifiedDate = lastModifiedDate
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SignConfigServiceImpl::class.java)
    }
}
