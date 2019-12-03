package com.tencent.bkrepo.repository.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.tencent.bkrepo.common.api.constant.CommonMessageCode.PARAMETER_IS_EXIST
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.IdValue
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.artifact.constant.ArtifactMessageCode.REPOSITORY_NOT_FOUND
import com.tencent.bkrepo.repository.dao.NodeDao
import com.tencent.bkrepo.repository.model.TRepository
import com.tencent.bkrepo.repository.model.TStorageCredentials
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoUpdateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import com.tencent.bkrepo.repository.pojo.repo.StorageCredentials
import com.tencent.bkrepo.repository.repository.RepoRepository
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 仓库service
 *
 * @author: carrypan
 * @date: 2019-09-20
 */
@Service
class RepositoryService @Autowired constructor(
    private val repoRepository: RepoRepository,
    private val nodeDao: NodeDao,
    private val mongoTemplate: MongoTemplate,
    private val objectMapper: ObjectMapper
) {
    private fun queryRepository(projectId: String, name: String, type: String? = null): TRepository? {
        if (projectId.isBlank() || name.isBlank()) return null

        val criteria = Criteria.where("projectId").`is`(projectId).and("name").`is`(name)

        if (!type.isNullOrBlank()) {
            criteria.and("type").`is`(type)
        }
        return mongoTemplate.findOne(Query(criteria), TRepository::class.java)
    }

    fun detail(projectId: String, name: String, type: String? = null): RepositoryInfo? {
        return convert(queryRepository(projectId, name, type))
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
        val criteria = Criteria.where("projectId").`is`(projectId).and("name").`is`(name)

        if (!type.isNullOrBlank()) {
            criteria.and("type").`is`(type)
        }

        return mongoTemplate.exists(Query(criteria), TRepository::class.java)
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun create(repoCreateRequest: RepoCreateRequest): IdValue {
        repoCreateRequest.takeUnless { exist(it.projectId, it.name) } ?: throw ErrorCodeException(PARAMETER_IS_EXIST)

        val tRepository = repoCreateRequest.let { TRepository(
                name = it.name,
                type = it.type,
                category = it.category,
                public = it.public,
                description = it.description,
                configuration = objectMapper.writeValueAsString(it.configuration),
                storageCredentials = it.storageCredentials?.let { item -> TStorageCredentials(item.type, item.credentials) },
                projectId = it.projectId,

                createdBy = it.operator,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = it.operator,
                lastModifiedDate = LocalDateTime.now()
            )
        }
        val idValue = IdValue(repoRepository.insert(tRepository).id!!)

        logger.info("Create repository [$repoCreateRequest] success.")
        return idValue
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun update(repoUpdateRequest: RepoUpdateRequest) {
        val projectId = repoUpdateRequest.projectId
        val name = repoUpdateRequest.name
        val repository = queryRepository(projectId, name) ?: throw ErrorCodeException(REPOSITORY_NOT_FOUND, name)

        with(repoUpdateRequest) {
            category?.let { repository.category = it }
            public?.let { repository.public = it }
            configuration?.let { repository.configuration = objectMapper.writeValueAsString(configuration) }
            description?.let { repository.description = it }
            repository.lastModifiedBy = repoUpdateRequest.operator
            repository.lastModifiedDate = LocalDateTime.now()
        }

        logger.info("Update repository [$projectId/$name] [$repoUpdateRequest] success.")
        repoRepository.save(repository)
    }

    /**
     * 用于测试的函数，不对外提供
     */
    fun delete(projectId: String, name: String) {
        val repository = queryRepository(projectId, name) ?: throw ErrorCodeException(REPOSITORY_NOT_FOUND, name)

        repoRepository.deleteById(repository.id!!)
        nodeDao.remove(Query(Criteria
                .where("projectId")
                .`is`(repository.projectId)
                .and("repoName").`is`(repository.name)
        ))

        logger.info("Delete repository [$projectId/$name] success.")
    }

    private fun createListQuery(projectId: String): Query {
        val query = Query(Criteria.where("projectId").`is`(projectId)).with(Sort.by("name"))
        query.fields().exclude("storageCredentials")

        return query
    }

    /**
     * 检查仓库是否存在，不存在则抛异常
     */
    fun checkRepository(projectId: String, repoName: String, repoType: String? = null) {
        if (!exist(projectId, repoName, repoType)) {
            throw ErrorCodeException(REPOSITORY_NOT_FOUND, repoName)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RepositoryService::class.java)

        private fun convert(tRepository: TRepository?): RepositoryInfo? {
            return tRepository?.let {
                RepositoryInfo(
                    name = it.name,
                    type = it.type,
                    category = it.category,
                    public = it.public,
                    description = it.description,
                    configuration = it.configuration,
                    storageCredentials = convert(it.storageCredentials),
                    projectId = it.projectId
                )
            }
        }

        private fun convert(tStorageCredentials: TStorageCredentials?): StorageCredentials? {
            return tStorageCredentials?.let {
                StorageCredentials(type = it.type, credentials = it.credentials)
            }
        }
    }
}
