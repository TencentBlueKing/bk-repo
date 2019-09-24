package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.constant.CommonMessageCode.ELEMENT_NOT_FOUND
import com.tencent.bkrepo.common.api.constant.CommonMessageCode.PARAMETER_IS_EXIST
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.IdValue
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.repository.model.TRepository
import com.tencent.bkrepo.repository.pojo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.RepoUpdateRequest
import com.tencent.bkrepo.repository.pojo.Repository
import com.tencent.bkrepo.repository.repository.RepoRepository
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.repository.findByIdOrNull
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
    private val nodeService: NodeService,
    private val mongoTemplate: MongoTemplate
) {
    fun getDetailById(id: String): Repository {

        return toRepository(repoRepository.findByIdOrNull(id)) ?: throw ErrorCodeException(ELEMENT_NOT_FOUND)
    }

    fun list(projectId: String): List<Repository> {
        return repoRepository.findByProjectId(projectId)
    }

    fun page(projectId: String, page: Int, size: Int): Page<Repository> {
        val tRepositoryPage = repoRepository.findByProjectId(projectId, PageRequest.of(page, size))
        return Page(page, size, tRepositoryPage.totalElements, tRepositoryPage.content)
    }

    fun exist(projectId: String, type: String, name: String): Boolean {
        name.takeIf { it.isNotBlank() } ?: return false
        val query = Query(Criteria.where("projectId").`is`(projectId)
                .and("type").`is`(type)
                .and("name").`is`(name))
        return mongoTemplate.exists(query, TRepository::class.java)
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun create(repoCreateRequest: RepoCreateRequest): IdValue {
        repoCreateRequest.takeUnless { exist(it.projectId, it.type, it.name) } ?: throw ErrorCodeException(PARAMETER_IS_EXIST)

        val tRepository = repoCreateRequest.let { TRepository(
                name = it.name,
                type = it.type,
                category = it.category,
                public = it.public,
                description = it.description,
                extension = it.extension,
                projectId = it.projectId,
                createdBy = it.createdBy,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = it.createdBy,
                lastModifiedDate = LocalDateTime.now()
            )
        }
        return IdValue(repoRepository.insert(tRepository).id!!)
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun updateById(id: String, repoUpdateRequest: RepoUpdateRequest) {
        val repository = repoRepository.findByIdOrNull(id) ?: throw ErrorCodeException(ELEMENT_NOT_FOUND)
        // 如果修改了name，校验唯一性
        repoUpdateRequest.name?.takeUnless {
            repository.name != it && exist(repository.projectId, repository.type, it)
        } ?: throw ErrorCodeException(PARAMETER_IS_EXIST)

        with(repoUpdateRequest) {
            name?.let { repository.name = it }
            category?.let { repository.category = it }
            public?.let { repository.public = it }
            extension?.let { repository.extension = it }
            description?.let { repository.description = it }
            repository.lastModifiedBy = modifiedBy
            repository.lastModifiedDate = LocalDateTime.now()
        }

        repoRepository.save(repository)
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun deleteById(id: String) {
        repoRepository.deleteById(id)
        nodeService.deleteByRepoId(id)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RepositoryService::class.java)

        fun toRepository(tRepository: TRepository?): Repository? {
            return tRepository?.let { Repository(
                    it.id!!,
                    it.name,
                    it.type,
                    it.category,
                    it.public,
                    it.description,
                    it.extension,
                    it.projectId
            ) }
        }
    }
}
