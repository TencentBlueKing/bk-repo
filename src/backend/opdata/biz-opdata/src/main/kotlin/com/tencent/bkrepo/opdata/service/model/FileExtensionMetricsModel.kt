package com.tencent.bkrepo.opdata.service.model

import com.tencent.bkrepo.opdata.constant.OPDATA_FILE_EXTENSION_METRICS
import com.tencent.bkrepo.opdata.model.TFileExtensionMetrics
import com.tencent.bkrepo.opdata.pojo.enums.StatMetrics
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Service

@Service
class FileExtensionMetricsModel @Autowired constructor(
    private val mongoTemplate: MongoTemplate
) {
    /**
     * 获取总体的文件后缀名的统计信息
     */
    fun getFileExtensionMetrics(metrics: StatMetrics): List<HashMap<String, Any>> {
        return aggregateQuery(Criteria(), metrics)
    }

    /**
     * 获取项目的文件后缀名的统计信息
     */
    fun getProjFileExtensionMetrics(projectId: String, metrics: StatMetrics): List<HashMap<String, Any>> {
        val criteria = where(TFileExtensionMetrics::projectId).isEqualTo(projectId)
        return aggregateQuery(criteria, metrics)
    }

    /**
     * 获取仓库的文件后缀名的统计信息
     */
    fun getRepoFileExtensionMetrics(
        projectId: String,
        repoName: String,
        metrics: StatMetrics
    ): List<HashMap<String, Any>> {
        val criteria = where(TFileExtensionMetrics::projectId).isEqualTo(projectId)
            .and(TFileExtensionMetrics::repoName).isEqualTo(repoName)
        return aggregateQuery(criteria, metrics)
    }

    @Suppress("UNCHECKED_CAST")
    private fun aggregateQuery(criteria: Criteria, metrics: StatMetrics): List<HashMap<String, Any>> {
        val field = metrics.name.toLowerCase()
        val aggregate = Aggregation.newAggregation(
            Aggregation.match(criteria),
            Aggregation.group(TFileExtensionMetrics::extension.name).sum(field).`as`(field),
            Aggregation.sort(Sort.Direction.DESC, field),
            Aggregation.project().andInclude(field).and(ID).`as`(TFileExtensionMetrics::extension.name).andExclude(ID)
        )

        val aggregateResult = mongoTemplate.aggregate(aggregate, OPDATA_FILE_EXTENSION_METRICS, HashMap::class.java)
        return aggregateResult.mappedResults as? List<HashMap<String, Any>> ?: listOf()
    }

    companion object {
        private const val ID = "_id"
    }
}