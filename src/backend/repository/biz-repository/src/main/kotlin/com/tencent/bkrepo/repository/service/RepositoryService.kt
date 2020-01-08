package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.auth.api.ServiceRoleResource
import com.tencent.bkrepo.auth.api.ServiceUserResource
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode.REPOSITORY_NOT_FOUND
import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.repository.dao.NodeDao
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.model.TRepository
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoUpdateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
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
    private val projectService: ProjectService,
    private val roleResource: ServiceRoleResource,
    private val userResource: ServiceUserResource,
    private val nodeDao: NodeDao,
    private val mongoTemplate: MongoTemplate
) {

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
        val criteria = Criteria.where(TRepository::projectId.name).`is`(projectId).and(TRepository::name.name).`is`(name)

        if (!type.isNullOrBlank()) {
            criteria.and(TRepository::type.name).`is`(type)
        }

        return mongoTemplate.exists(Query(criteria), TRepository::class.java)
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun create(repoCreateRequest: RepoCreateRequest) {
        with(repoCreateRequest) {
            this.takeUnless { exist(it.projectId, it.name) } ?: throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_EXISTED, repoCreateRequest.name)
            projectService.checkProject(projectId)

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
            repoRepository.insert(repository)
            val roleId = roleResource.createRepoManage(projectId, name).data!!
            userResource.addUserRole(operator, roleId)
            logger.info("Create repository [$repoCreateRequest] success.")
        }
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
     * 检查仓库是否存在，不存在则抛异常
     * 首先检查项目是否存在，再检查仓库
     */
    fun checkRepository(projectId: String, repoName: String, repoType: String? = null): TRepository {
        return queryRepository(projectId, repoName, repoType) ?: throw ErrorCodeException(REPOSITORY_NOT_FOUND, repoName)
    }

    /**
     * 用于测试的函数，不对外提供
     */
    fun delete(projectId: String, name: String) {
        val repository = queryRepository(projectId, name) ?: throw ErrorCodeException(REPOSITORY_NOT_FOUND, name)

        repoRepository.deleteById(repository.id!!)
        nodeDao.remove(Query(Criteria
                .where(TNode::projectId.name)
                .`is`(repository.projectId)
                .and(TNode::repoName.name).`is`(repository.name)
        ))

        logger.info("Delete repository [$projectId/$name] success.")
    }

    private fun createListQuery(projectId: String): Query {
        val query = Query(Criteria.where(TRepository::projectId.name).`is`(projectId)).with(Sort.by(TRepository::name.name))
        query.fields().exclude(TRepository::storageCredentials.name)

        return query
    }

    fun queryRepository(projectId: String, name: String, type: String? = null): TRepository? {
        if (projectId.isBlank() || name.isBlank()) return null

        val criteria = Criteria.where(TRepository::projectId.name).`is`(projectId).and(TRepository::name.name).`is`(name)
        if (!type.isNullOrBlank()) {
            criteria.and(TRepository::type.name).`is`(type)
        }
        return mongoTemplate.findOne(Query(criteria), TRepository::class.java)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RepositoryService::class.java)
        private val objectMapper = JsonUtils.objectMapper

        private fun convert(tRepository: TRepository?): RepositoryInfo? {
            return tRepository?.let {
                RepositoryInfo(
                    name = it.name,
                    type = it.type,
                    category = it.category,
                    public = it.public,
                    description = it.description,
                    configuration = objectMapper.readValue(it.configuration, RepositoryConfiguration::class.java),
                    storageCredentials = it.storageCredentials?.let { property -> objectMapper.readValue(property, StorageCredentials::class.java) },
                    projectId = it.projectId
                )
            }
        }
    }
}
