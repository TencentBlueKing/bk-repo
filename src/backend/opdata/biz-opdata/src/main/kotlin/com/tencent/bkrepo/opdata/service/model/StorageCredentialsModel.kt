package com.tencent.bkrepo.opdata.service.model

import com.tencent.bkrepo.opdata.constant.TO_GIGABYTE
import com.tencent.bkrepo.opdata.pojo.enums.StatMetrics
import com.tencent.bkrepo.opdata.repository.ProjectMetricsRepository
import com.tencent.bkrepo.opdata.util.StatDateUtil
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class StorageCredentialsModel @Autowired constructor(
    private val projectMetricsRepository: ProjectMetricsRepository
) {

    fun getStorageCredentialsStat(metrics: StatMetrics): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        val projectMetricsList = projectMetricsRepository.findAllByCreatedDate(StatDateUtil.getStatDate())
        projectMetricsList.forEach { projectMetrics ->
            projectMetrics.repoMetrics.forEach {
                val value = if (metrics == StatMetrics.NUM) it.num else it.size
                if (result.containsKey(it.credentialsKey)) {
                    result[it.credentialsKey!!] = result[it.credentialsKey!!]!! + value
                } else {
                    result[it.credentialsKey!!] = value
                }
            }
        }
        if (metrics == StatMetrics.SIZE) {
            result.keys.forEach {
                result[it] = result[it]!! / TO_GIGABYTE
            }
        }
        return result
    }
}