package com.tencent.bkrepo.auth.service.bk

import com.tencent.bkrepo.auth.model.TProject
import com.tencent.bkrepo.auth.pojo.CreateProjectRequest
import com.tencent.bkrepo.auth.pojo.Project
import com.tencent.bkrepo.auth.repository.ProjectRepository
import com.tencent.bkrepo.auth.service.ProjectService
import com.tencent.bkrepo.common.api.constant.CommonMessageCode
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "auth", name = ["realm"], havingValue = "bk")
class BkProjectServiceImpl @Autowired constructor(
) : ProjectService {
    override fun listProject(): List<Project> {
        throw ErrorCodeException(CommonMessageCode.NOT_SUPPORTED, "not supported")
    }

    override fun createProject(request: CreateProjectRequest) {

    }

    override fun deleteByName(name: String) {
        throw ErrorCodeException(CommonMessageCode.NOT_SUPPORTED, "not supported")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BkProjectServiceImpl::class.java)
    }
}