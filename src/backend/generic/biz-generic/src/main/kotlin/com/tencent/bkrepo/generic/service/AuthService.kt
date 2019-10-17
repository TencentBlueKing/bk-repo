package com.tencent.bkrepo.generic.service

import com.tencent.bkrepo.auth.api.ServicePermissionResource
import com.tencent.bkrepo.auth.api.ServiceProjectResource
import com.tencent.bkrepo.auth.pojo.CheckPermissionRequest
import com.tencent.bkrepo.auth.pojo.CreateProjectRequest
import com.tencent.bkrepo.auth.pojo.CreateRoleRequest
import com.tencent.bkrepo.common.api.constant.CommonMessageCode
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.storage.core.FileStorage
import com.tencent.bkrepo.generic.constant.REPO_TYPE
import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.repository.api.RepositoryResource
import com.tencent.bkrepo.repository.constant.enum.RepositoryCategoryEnum
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * 通用文件上传服务类
 *
 * @author: carrypan
 * @date: 2019-10-08
 */
@Service
class AuthService @Autowired constructor(
    private val serviceProjectResource: ServiceProjectResource,
    private val servicePermissionResource: ServicePermissionResource,
    private val repositoryResource: RepositoryResource
) {
    fun checkPermission(request: CheckPermissionRequest): Boolean {
        if (!request.project.isNullOrBlank()) {
            val projectName = request.project!!
            val project = serviceProjectResource.getByName(projectName).data
            if (project == null) {
                serviceProjectResource.createProject(
                    CreateProjectRequest(
                        name = projectName,
                        displayName = projectName,
                        description = ""
                    )
                )

            }
        }

        if (!request.repo.isNullOrBlank()) {
            val repoName = request.repo!!
            val repo = repositoryResource.query(request.project!!, repoName, REPO_TYPE).data
            if (repo == null) {
                repositoryResource.create(
                    RepoCreateRequest(
                        createdBy = "system",
                        name = "",
                        type = REPO_TYPE,
                        category = RepositoryCategoryEnum.LOCAL,
                        public = false,
                        projectId = request.project!!,
                        description = "repo $repoName",
                        extension = null,
                        storageType = null,
                        storageCredentials = null
                    )
                )
            }
        }

        return servicePermissionResource.checkPermission(request).data!!
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AuthService::class.java)
    }
}
