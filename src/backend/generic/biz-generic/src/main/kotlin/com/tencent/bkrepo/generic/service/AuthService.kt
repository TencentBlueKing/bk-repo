package com.tencent.bkrepo.generic.service

import com.tencent.bkrepo.auth.api.ServicePermissionResource
import com.tencent.bkrepo.auth.api.ServiceProjectResource
import com.tencent.bkrepo.auth.pojo.CheckPermissionRequest
import com.tencent.bkrepo.auth.pojo.CreateProjectRequest
import com.tencent.bkrepo.common.api.constant.CommonMessageCode
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.generic.constant.REPO_TYPE
import com.tencent.bkrepo.repository.api.RepositoryResource
import com.tencent.bkrepo.repository.constant.enum.RepositoryCategoryEnum
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class AuthService @Autowired constructor(
    private val serviceProjectResource: ServiceProjectResource,
    private val servicePermissionResource: ServicePermissionResource,
    private val repositoryResource: RepositoryResource
) {

    fun checkPermission(request: CheckPermissionRequest): Boolean {
        logger.info("checkPermission, request: $request")

        if (!request.project.isNullOrBlank()) {
            val projectName = request.project!!
            val project = serviceProjectResource.getByName(projectName).data
            if (project == null) {
                logger.info("project($projectName) not exist, create it")
                serviceProjectResource.createProject(
                    CreateProjectRequest(
                        name = projectName,
                        displayName = projectName,
                        description = ""
                    )
                )
            }

            if (!request.repo.isNullOrBlank()) {
                val repoName = request.repo!!
                val repo = repositoryResource.queryDetail(request.project!!, repoName, REPO_TYPE).data
                if (repo == null) {
                    if (repoName != "custom" && repoName != "pipeline" && repoName != "report"){
                        throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, repoName)
                    }
                    logger.info("repo($repoName) not exist, create it")
                    repositoryResource.create(
                        RepoCreateRequest(
                            projectId = projectName,
                            name = repoName,
                            type = REPO_TYPE,
                            category = RepositoryCategoryEnum.LOCAL,
                            public = false,
                            description = "repo $repoName",
                            extension = null,
                            storageCredentials = null,
                            operator = "system"
                        )
                    )
                }
            }
        }

        val checkResponse = servicePermissionResource.checkPermission(request)
        logger.info("checkResponse: $checkResponse")
        return checkResponse.data!!
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AuthService::class.java)
    }
}
