package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.constant.CommonMessageCode.ELEMENT_NOT_FOUND
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.IdValue
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.repository.model.TRepository
import com.tencent.bkrepo.repository.pojo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.RepoUpdateRequest
import com.tencent.bkrepo.repository.pojo.Repository
import com.tencent.bkrepo.repository.repository.RepoRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
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
        private val repoRepository: RepoRepository
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

    @Transactional(rollbackFor=[Throwable::class])
    fun create(repoCreateRequest: RepoCreateRequest): IdValue {
        // TODO: 唯一性校验
        val tRepository = repoCreateRequest.let { TRepository(
                    name = it.name,
                    type = it.type,
                    category = it.category,
                    public = it.public,
                    description = it.description,
                    extension = it.extension,
                    projectId = it.projectId
            )
        }
        return IdValue(repoRepository.insert(tRepository).id)
    }

    @Transactional(rollbackFor=[Throwable::class])
    fun updateById(id: String, repoUpdateRequest: RepoUpdateRequest) {
        // TODO: 唯一性校验
        val tRepository = repoUpdateRequest.let {
            TRepository(
                id = id,
                name = it.name,
                category = it.category,
                public = it.public,
                extension = it.extension,
                description = it.description
            )
        }
        repoRepository.save(tRepository)
    }


    @Transactional(rollbackFor=[Throwable::class])
    fun deleteById(id: String) {
        // TODO: 删除仓库下面的数据。节点，元数据，文件
        repoRepository.deleteById(id)
    }


    companion object {
        private val logger = LoggerFactory.getLogger(RepositoryService::class.java)

        fun toRepository(tRepository: TRepository?): Repository? {
            return tRepository?.let { Repository(
                    it.id!!,
                    it.name!!,
                    it.type!!,
                    it.category!!,
                    it.public!!,
                    it.description,
                    it.extension!!,
                    it.projectId!!
            )}
        }
    }
}