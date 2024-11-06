package com.tencent.bkrepo.analyst.service.impl

import com.tencent.bkrepo.analyst.dao.ExecutionClusterDao
import com.tencent.bkrepo.analyst.model.TExecutionCluster
import com.tencent.bkrepo.analyst.pojo.execution.ExecutionCluster
import com.tencent.bkrepo.analyst.service.ExecutionClusterService
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.security.util.SecurityUtils
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ExecutionClusterServiceImpl(
    private val executionClusterDao: ExecutionClusterDao
) : ExecutionClusterService {
    override fun create(executionCluster: ExecutionCluster): ExecutionCluster {
        with(executionCluster) {
            if (executionClusterDao.existsByName(name)) {
                throw ErrorCodeException(CommonMessageCode.RESOURCE_EXISTED, name)
            }
            val now = LocalDateTime.now()
            val userId = SecurityUtils.getUserId()
            return executionClusterDao.insert(
                TExecutionCluster(
                    createdBy = userId,
                    createdDate = now,
                    lastModifiedBy = userId,
                    lastModifiedDate = now,
                    name = name,
                    type = type,
                    description = description,
                    config = executionCluster.toJsonString()
                )
            ).run { config.readJsonString() }
        }
    }

    override fun remove(name: String) {
        if (executionClusterDao.deleteByName(name) == 0L) {
            throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, name)
        }
    }

    override fun update(executionCluster: ExecutionCluster): ExecutionCluster {
        with(executionCluster) {
            val userId = SecurityUtils.getUserId()
            val savedExecutionCluster = executionClusterDao.findByName(name)
                ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, name)

            if (name != savedExecutionCluster.name || type != savedExecutionCluster.type) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, name, type)
            }

            return executionClusterDao.save(
                savedExecutionCluster.copy(
                    lastModifiedBy = userId,
                    lastModifiedDate = LocalDateTime.now(),
                    description = description,
                    config = executionCluster.toJsonString()
                )
            ).run { config.readJsonString() }

        }
    }

    override fun get(name: String): ExecutionCluster {
        return executionClusterDao.findByName(name)?.config?.readJsonString()
            ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, name)
    }

    override fun exists(name: String): Boolean {
        return executionClusterDao.existsByName(name)
    }

    override fun list(): List<ExecutionCluster> {
        return executionClusterDao.findAll().map { it.config.readJsonString() }
    }
}
