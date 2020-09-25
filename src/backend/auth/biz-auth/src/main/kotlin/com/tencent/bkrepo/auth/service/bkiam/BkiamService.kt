package com.tencent.bkrepo.auth.service.bkiam

import com.tencent.bk.sdk.iam.constants.CallbackMethodEnum
import com.tencent.bk.sdk.iam.dto.PageInfoDTO
import com.tencent.bk.sdk.iam.dto.callback.request.CallbackRequestDTO
import com.tencent.bk.sdk.iam.dto.callback.response.BaseDataResponseDTO
import com.tencent.bk.sdk.iam.dto.callback.response.CallbackBaseResponseDTO
import com.tencent.bk.sdk.iam.dto.callback.response.FetchInstanceInfoResponseDTO
import com.tencent.bk.sdk.iam.dto.callback.response.InstanceInfoDTO
import com.tencent.bk.sdk.iam.dto.callback.response.ListInstanceResponseDTO
import com.tencent.bkrepo.repository.api.ProjectClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.project.ProjectRangeQueryRequest
import com.tencent.bkrepo.repository.pojo.project.RepoRangeQueryRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class BkiamService @Autowired constructor(
    private val projectClient: ProjectClient,
    private val repositoryClient: RepositoryClient
) {
    fun queryProject(token: String, request: CallbackRequestDTO): CallbackBaseResponseDTO? {
        logger.info("queryProject, token: $token, request: $request")
        checkToken(token)
        val method = request.method
        if (method == CallbackMethodEnum.FETCH_INSTANCE_INFO) {
            val ids = request.filter.idList.map { it.toString() }
            return fetchProjectInfo(ids, request.filter.attributeList)
        }
        return listProject(request.page, method)
    }

    fun queryRepo(token: String, request: CallbackRequestDTO): CallbackBaseResponseDTO? {
        logger.info("queryRepo, token: $token, request: $request")
        checkToken(token)
        val method = request.method
        val projectId = request.filter.parent.id
        if (method == CallbackMethodEnum.FETCH_INSTANCE_INFO) {
            val ids = request.filter.idList.map { it.toString() }
            return fetchRepoInfo(projectId, ids, request.filter.attributeList)
        }
        return listRepo(projectId, request.page, request.method)
    }

    private fun listRepo(projectId: String, page: PageInfoDTO, method: CallbackMethodEnum): CallbackBaseResponseDTO? {
        logger.info("listRepo, projectId: $projectId, page: $page, method: $method")
        var offset = 0L
        var limit = 20
        if (page != null) {
            offset = page.offset
            limit = page.limit.toInt()
        }
        val repoPage = repositoryClient.rangeQuery(RepoRangeQueryRequest(listOf(), projectId, offset, limit)).data!!
        var result = ListInstanceResponseDTO()
        val repos = repoPage.records.map {
            val entity = InstanceInfoDTO()
            entity.id = it!!.name
            entity.displayName = it!!.name
            entity
        }
        val data = BaseDataResponseDTO<InstanceInfoDTO>()
        data.count = repoPage.totalRecords
        data.result = repos
        result.code = 0L
        result.message = ""
        result.data = data
        logger.info("listRepo, result: $result")
        return result
    }

    private fun fetchRepoInfo(projectId: String, idList: List<String>, attrs: List<String>): CallbackBaseResponseDTO? {
        logger.info("fetchRepoInfo, projectId: $projectId, idList: $idList, attrs: $attrs")
        val repoPage = repositoryClient.rangeQuery(RepoRangeQueryRequest(idList, projectId, 0, 10000)).data!!
        val repos = repoPage.records.map {
            val entity = InstanceInfoDTO()
            entity.id = it!!.name
            entity.displayName = it!!.name
            entity
        }
        val result = FetchInstanceInfoResponseDTO()
        result.code = 0
        result.message = ""
        result.data = repos
        return result
    }

    private fun listProject(page: PageInfoDTO, method: CallbackMethodEnum): ListInstanceResponseDTO {
        logger.info("listProject, page $method, method $page")
        var offset = 0L
        var limit = 20
        if (page != null) {
            offset = page.offset
            limit = page.limit.toInt()
        }
        val projectPage = projectClient.rangeQuery(ProjectRangeQueryRequest(listOf(), offset, limit)).data!!
        val projects = projectPage.records.map {
            val entity = InstanceInfoDTO()
            entity.id = it!!.name
            entity.displayName = it!!.displayName
            entity
        }
        val result = ListInstanceResponseDTO()
        val data = BaseDataResponseDTO<InstanceInfoDTO>()
        data.count = projectPage.totalRecords
        data.result = projects
        result.code = 0L
        result.message = ""
        result.data = data
        logger.info("listProject, result: $result")
        return result
    }

    private fun fetchProjectInfo(idList: List<String>, attrs: List<String>): FetchInstanceInfoResponseDTO {
        logger.info("fetchProjectInfo, idList: $idList, attrs: $attrs")
        val projectPage = projectClient.rangeQuery(ProjectRangeQueryRequest(idList, 0, 10000)).data!!

        val projects = projectPage.records.map {
            val entity = InstanceInfoDTO()
            entity.id = it!!.name
            entity.displayName = it!!.displayName
            entity
        }

        val result = FetchInstanceInfoResponseDTO()
        result.code = 0
        result.message = ""
        result.data = projects
        return result
    }

    private fun checkToken(token: String) {
        // TODO:
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BkiamService::class.java)
    }
}
