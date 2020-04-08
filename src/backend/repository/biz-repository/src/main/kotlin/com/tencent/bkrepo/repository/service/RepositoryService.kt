package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode.REPOSITORY_NOT_FOUND
import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import com.tencent.bkrepo.repository.dao.repository.RepoRepository
import com.tencent.bkrepo.repository.listener.event.repo.RepoCreatedEvent
import com.tencent.bkrepo.repository.listener.event.repo.RepoDeletedEvent
import com.tencent.bkrepo.repository.listener.event.repo.RepoUpdatedEvent
import com.tencent.bkrepo.repository.model.TRepository
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoDeleteRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoUpdateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import com.tencent.bkrepo.repository.util.NodeUtils.ROOT_PATH
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 仓库service
 *
 * @author: carrypan
 * @date: 2019-09-20
 */
@Service
class RepositoryService: AbstractService() {

    @Autowired
    private lateinit var repoRepository: RepoRepository

    @Autowired
    private lateinit var nodeService: NodeService

    @Autowired
    private lateinit var projectService: ProjectService

    fun detail(projectId: String, name: String, type: String? = null): RepositoryInfo? {
        return convert(queryRepository(projectId, name, type))
    }

    fun queryRepository(projectId: String, name: String, type: String? = null): TRepository? {
        if (projectId.isBlank() || name.isBlank()) return null

        val criteria = Criteria.where(TRepository::projectId.name).`is`(projectId).and(TRepository::name.name).`is`(name)
        if (!type.isNullOrBlank()) {
            criteria.and(TRepository::type.name).`is`(type)
        }
        return mongoTemplate.findOne(Query(criteria), TRepository::class.java)
    }

    fun list(projectId: String): List<RepositoryInfo> {
        val query = createListQuery(projectId)

        return mongoTemplate.find(query, TRepository::class.java).map { convert(it)!! }
    }

    fun page(projectId: String, page: Int, size: Int): Page<RepositoryInfo> {
        val query = createListQuery(projectId).with(PageRequest.of(page, size))
        val data = mongoTemplate.find(query, TRepository::class.java).map { convert(it)!! }
        val count = mongoTemplate.count(query, TRepository::class.java)

        return Page(page, size, count, data)
    }

    fun exist(projectId: String, name: String, type: String? = null): Boolean {
        if (projectId.isBlank() || name.isBlank()) return false
        val criteria = Criteria.where(TRepository::projectId.name).`is`(projectId).and(TRepository::name.name).`is`(name)

        if (!type.isNullOrBlank()) {
            criteria.and(TRepository::type.name).`is`(type)
        }

        return mongoTemplate.exists(Query(criteria), TRepository::class.java)
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun create(repoCreateRequest: RepoCreateRequest): RepositoryInfo {
        with(repoCreateRequest) {
            projectService.checkProject(projectId)
            if (exist(projectId, name)) throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_EXISTED, name)
            // 创建仓库
            val repository = TRepository(
                name = name,
                type = type,
                category = category,
                public = public,
                description = description,
                configuration = objectMapper.writeValueAsString(configuration),
                storageCredentials = storageCredentials?.let { objectMapper.writeValueAsString(it) },
                projectId = projectId,
                createdBy = operator,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = operator,
                lastModifiedDate = LocalDateTime.now()
            )

            return repoRepository.insert(repository)
                .also { nodeService.createRootNode(it.projectId, it.name, it.createdBy) }
                .also { createRepoManager(it.projectId, it.name, it.createdBy) }
                .also { publishEvent(RepoCreatedEvent(repoCreateRequest)) }
                .also { logger.info("Create repository [$repoCreateRequest] success.") }
                .let { convert(repository)!! }
        }
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun update(repoUpdateRequest: RepoUpdateRequest) {
        repoUpdateRequest.apply {
            val repository = checkRepository(projectId, name)
            repository.category = category ?: repository.category
            repository.public = public ?: repository.public
            repository.description = description ?: repository.description
            repository.lastModifiedBy = operator
            repository.lastModifiedDate = LocalDateTime.now()
            configuration?.let { repository.configuration = objectMapper.writeValueAsString(configuration) }
            repoRepository.save(repository)
        }.also { publishEvent(RepoUpdatedEvent(it)) }
            .also { logger.info("Update repository[$it] success.") }
    }

    /**
     * 删除仓库，需要保证文件已经被删除
     */
    @Transactional(rollbackFor = [Throwable::class])
    fun delete(repoDeleteRequest: RepoDeleteRequest) {
        repoDeleteRequest.apply {
            val repository = checkRepository(projectId, name)
            nodeService.countFileNode(projectId, name).takeIf { it == 0L } ?: throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_CONTAINS_FILE)
            nodeService.deleteByPath(projectId, name, ROOT_PATH, SYSTEM_USER, false)
            repoRepository.delete(repository)
        }.also { publishEvent(RepoDeletedEvent(it)) }
            .also { logger.info("Delete repository [$it] success.") }
    }

    /**
     * 检查仓库是否存在，不存在则抛异常
     */
    fun checkRepository(projectId: String, repoName: String, repoType: String? = null): TRepository {
        return queryRepository(projectId, repoName, repoType) ?: throw ErrorCodeException(REPOSITORY_NOT_FOUND, repoName)
    }

    private fun createListQuery(projectId: String): Query {
        val query = Query(Criteria.where(TRepository::projectId.name).`is`(projectId)).with(Sort.by(TRepository::name.name))
        query.fields().exclude(TRepository::storageCredentials.name)

        return query
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RepositoryService::class.java)
        private val objectMapper = JsonUtils.objectMapper

        fun convert(tRepository: TRepository?): RepositoryInfo? {
            return tRepository?.let {
                RepositoryInfo(
                    name = it.name,
                    type = it.type,
                    category = it.category,
                    public = it.public,
                    description = it.description,
                    configuration = objectMapper.readValue(it.configuration, RepositoryConfiguration::class.java),
                    storageCredentials = it.storageCredentials?.let { property -> objectMapper.readValue(property, StorageCredentials::class.java) },
                    projectId = it.projectId,
                    createdBy = it.createdBy,
                    createdDate = it.createdDate.format(DateTimeFormatter.ISO_DATE_TIME),
                    lastModifiedBy = it.lastModifiedBy,
                    lastModifiedDate = it.lastModifiedDate.format(DateTimeFormatter.ISO_DATE_TIME)
                )
            }
        }
    }
}
