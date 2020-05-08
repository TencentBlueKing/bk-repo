package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.repository.dao.repository.ModuleDepsRepository
import com.tencent.bkrepo.repository.model.TModuleDeps
import com.tencent.bkrepo.repository.pojo.module.deps.ModuleDepsInfo
import com.tencent.bkrepo.repository.pojo.module.deps.service.DepsCreateRequest
import com.tencent.bkrepo.repository.pojo.module.deps.service.DepsDeleteRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class ModuleDepsService : AbstractService() {
    @Autowired
    private lateinit var moduleDepsRepository: ModuleDepsRepository

    @Autowired
    private lateinit var repositoryService: RepositoryService

    /**
     * 创建依赖关系
     */
    @Transactional(rollbackFor = [Throwable::class])
    fun create(depsCreateRequest: DepsCreateRequest): ModuleDepsInfo {
        with(depsCreateRequest) {
            this.takeIf { name.isNotBlank() } ?: throw ErrorCodeException(
                CommonMessageCode.PARAMETER_MISSING,
                this::name.name
            )
            this.takeIf { deps.isNotBlank() } ?: throw ErrorCodeException(
                CommonMessageCode.PARAMETER_MISSING,
                this::deps.name
            )
            repositoryService.checkRepository(projectId, repoName)
            if (exist(projectId, repoName, name, deps)) {
                if (!overwrite) {
                    throw ErrorCodeException(ArtifactMessageCode.NODE_EXISTED, name)
                } else {
                    delete(DepsDeleteRequest(projectId, repoName, name, deps, operator))
                }
            }
            val moduleDeps = TModuleDeps(
                projectId = projectId,
                repoName = repoName,
                name = name,
                deps = deps,
                createdBy = operator,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = operator,
                lastModifiedDate = LocalDateTime.now()
            )
            return moduleDepsRepository.insert(moduleDeps)
                .also { logger.info("Create module deps [$depsCreateRequest] success.") }
                .let { convert(it)!! }
        }
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun batchCreate(depsCreateRequestList: List<DepsCreateRequest>) {
        depsCreateRequestList.takeUnless { it.isNullOrEmpty() }
        val createList = mutableListOf<TModuleDeps>()
        depsCreateRequestList.forEach continuing@{
            with(it) {
                this.takeIf { name.isNotBlank() } ?: throw ErrorCodeException(
                    CommonMessageCode.PARAMETER_MISSING,
                    this::name.name
                )
                this.takeIf { deps.isNotBlank() } ?: throw ErrorCodeException(
                    CommonMessageCode.PARAMETER_MISSING,
                    this::deps.name
                )
                repositoryService.checkRepository(projectId, repoName)
                if (exist(projectId, repoName, name, deps)) {
                    if (!overwrite) {
                        throw ErrorCodeException(ArtifactMessageCode.NODE_EXISTED, name)
                    }
                    return@continuing
                    // else {
                    //     delete(DepsDeleteRequest(projectId, repoName, name, deps, operator))
                    // }
                }

                val moduleDeps = TModuleDeps(
                    projectId = projectId,
                    repoName = repoName,
                    name = name,
                    deps = deps,
                    createdBy = operator,
                    createdDate = LocalDateTime.now(),
                    lastModifiedBy = operator,
                    lastModifiedDate = LocalDateTime.now()
                )
                createList.add(moduleDeps)
            }
        }
        if(createList.isNotEmpty()){
            moduleDepsRepository.insert(createList)
            logger.info("batch insert module deps, size: [${createList.size}] success.")
        }
    }

    private fun exist(projectId: String, repoName: String, name: String?, deps: String): Boolean {
        if (deps.isBlank()) return false
        val criteria =
            Criteria.where(TModuleDeps::projectId.name).`is`(projectId).and(TModuleDeps::repoName.name).`is`(repoName)
                .and(TModuleDeps::deps.name).`is`(deps)
                .and(TModuleDeps::deleted.name).`is`(null)
        name?.run { criteria.and(TModuleDeps::name.name).`is`(name) }

        return mongoTemplate.exists(Query(criteria), TModuleDeps::class.java)
    }

    fun delete(depsDeleteRequest: DepsDeleteRequest, soft: Boolean = true) {
        with(depsDeleteRequest) {
            this.takeIf { !name.isNullOrBlank() } ?: throw ErrorCodeException(
                CommonMessageCode.PARAMETER_MISSING,
                this::name.name
            )
            repositoryService.checkRepository(projectId, repoName)
            if (!exist(projectId, repoName, name, deps)) throw ErrorCodeException(
                ArtifactMessageCode.NODE_EXISTED,
                deps
            )
            val depsQuery = depsQuery(this)
            if (soft) {
                mongoTemplate.updateMulti(depsQuery, depsDeleteUpdate(operator), TModuleDeps::class.java)
            } else {
                mongoTemplate.remove(depsQuery, TModuleDeps::class.java)
            }
            logger.info("Delete module deps [$depsDeleteRequest] by [$operator] success.")
        }
    }

    fun deleteAllByName(depsDeleteRequest: DepsDeleteRequest, soft: Boolean = true) {
        with(depsDeleteRequest) {
            repositoryService.checkRepository(projectId, repoName)
            if (!exist(projectId, repoName, name, deps)) throw ErrorCodeException(
                ArtifactMessageCode.NODE_EXISTED,
                deps
            )
            val depsQuery = depsQuery(this)
            if (soft) {
                mongoTemplate.updateMulti(depsQuery, depsDeleteUpdate(operator), TModuleDeps::class.java)
            } else {
                mongoTemplate.remove(depsQuery, TModuleDeps::class.java)
            }
            logger.info("Delete all module deps [$depsDeleteRequest] by [$operator] success.")
        }
    }

    private fun depsQuery(depsDeleteRequest: DepsDeleteRequest): Query {
        with(depsDeleteRequest) {
            val criteria =
                Criteria.where(TModuleDeps::projectId.name).`is`(projectId).and(TModuleDeps::repoName.name)
                    .`is`(repoName)
                    .and(TModuleDeps::deps.name).`is`(deps)
            name?.let { criteria.and(TModuleDeps::name.name).`is`(it) }
            return Query(criteria)
        }
    }

    private fun depsDeleteUpdate(operator: String): Update {
        return Update()
            .set(TModuleDeps::lastModifiedDate.name, LocalDateTime.now())
            .set(TModuleDeps::lastModifiedBy.name, operator)
            .set(TModuleDeps::deleted.name, LocalDateTime.now())
    }

    fun find(projectId: String, repoName: String, name: String, deps: String): ModuleDepsInfo {
        repositoryService.checkRepository(projectId, repoName)
        val criteria =
            Criteria.where(TModuleDeps::projectId.name).`is`(projectId).and(TModuleDeps::repoName.name).`is`(repoName)
                .and(TModuleDeps::name.name).`is`(name).and(TModuleDeps::deps.name).`is`(deps).and(TModuleDeps::deleted.name).`is`(null)
        if (mongoTemplate.count(Query.query(criteria), TModuleDeps::class.java) >= THRESHOLD) {
            throw ErrorCodeException(ArtifactMessageCode.NODE_LIST_TOO_LARGE)
        }
        return mongoTemplate.findOne(Query.query(criteria), TModuleDeps::class.java).let { convert(it)!! }
    }

    fun list(projectId: String, repoName: String, name: String): List<ModuleDepsInfo> {
        repositoryService.checkRepository(projectId, repoName)
        val criteria =
            Criteria.where(TModuleDeps::projectId.name).`is`(projectId).and(TModuleDeps::repoName.name).`is`(repoName)
                .and(TModuleDeps::name.name).`is`(name).and(TModuleDeps::deleted.name).`is`(null)
        val query = Query.query(criteria).with(Sort.by(TModuleDeps::createdDate.name))
        if (mongoTemplate.count(query, TModuleDeps::class.java) >= THRESHOLD) {
            throw ErrorCodeException(ArtifactMessageCode.NODE_LIST_TOO_LARGE)
        }
        return mongoTemplate.find(query, TModuleDeps::class.java).map { convert(it)!! }
    }

    fun page(projectId: String, repoName: String, page: Int, size: Int, name: String): Page<ModuleDepsInfo> {
        page.takeIf { it >= 0 } ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "page")
        size.takeIf { it >= 0 } ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "size")
        repositoryService.checkRepository(projectId, repoName)
        val criteria =
            Criteria.where(TModuleDeps::projectId.name).`is`(projectId).and(TModuleDeps::repoName.name).`is`(repoName)
                .and(TModuleDeps::name.name).`is`(name).and(TModuleDeps::deleted.name).`is`(null)
        val query = Query.query(criteria).with(Sort.by(TModuleDeps::createdDate.name))
        val listData = mongoTemplate.find(query, TModuleDeps::class.java).map { convert(it)!! }
        val count = mongoTemplate.count(query, TModuleDeps::class.java)
        return Page(page, size, count, listData)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ModuleDepsService::class.java)

        private const val THRESHOLD: Long = 100000L

        fun convert(tModuleDeps: TModuleDeps?): ModuleDepsInfo? {
            return tModuleDeps?.let {
                ModuleDepsInfo(
                    name = it.name,
                    deps = it.deps,
                    projectId = it.projectId,
                    repoName = it.repoName,
                    createdBy = it.createdBy,
                    createdDate = it.createdDate.format(DateTimeFormatter.ISO_DATE_TIME)
                )
            }
        }
    }
}
