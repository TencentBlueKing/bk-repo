package com.tencent.bkrepo.dockeradapter.service.harbor

import com.tencent.bkrepo.dockeradapter.client.HarborClient
import com.tencent.bkrepo.dockeradapter.pojo.ImageAccount
import com.tencent.bkrepo.dockeradapter.pojo.Repository
import com.tencent.bkrepo.dockeradapter.service.RepoService
import com.tencent.bkrepo.dockeradapter.util.AccountUtils
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.util.Random


@Service
@ConditionalOnProperty(prefix = "adapter", name = ["realm"], havingValue = "harbor")
class HarborRepoServiceImpl(
    private val harborClient: HarborClient
) : RepoService {
    override fun createRepo(projectId: String): Repository {
        logger.info("createRepo, projectId: $projectId")
        var harborProject = harborClient.getProjectByName(projectId)
        if (harborProject == null) {
            harborClient.createProject(projectId)
            harborProject = harborClient.getProjectByName(projectId)
        }
        return Repository(harborProject!!.name, "", harborProject!!.name)
    }

    override fun createAccount(projectId: String): ImageAccount {
        logger.info("createAccount, projectId: $projectId")
        val userName = System.currentTimeMillis().toString() + Random().nextInt(1000).toString()
        val password = AccountUtils.generateRandomPassword(8)

        var harborProject = harborClient.getProjectByName(projectId)
        if (harborProject == null) {
            harborClient.createProject(projectId)
            harborProject = harborClient.getProjectByName(projectId)
        }
        harborClient.createUser(userName, password)
        harborClient.addProjectMember(userName, harborProject!!.projectId)
        return ImageAccount(userName, password)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(HarborRepoServiceImpl::class.java)
    }
}