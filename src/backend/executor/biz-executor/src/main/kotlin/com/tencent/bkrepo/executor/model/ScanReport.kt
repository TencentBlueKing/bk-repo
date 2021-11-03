package com.tencent.bkrepo.executor.model

import com.tencent.bkrepo.executor.pojo.ReportScanRecord
import com.tencent.bkrepo.executor.pojo.context.ScanTaskContext
import org.bson.Document
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition
import org.springframework.stereotype.Component

@Component
class ScanReport {

    @Autowired
    @Qualifier(value = "secondaryMongoTemplate")
    lateinit var mongoTemplate: MongoTemplate

    fun storeReport(record: ReportScanRecord, reportName: String) {
        val collectionName = getCollectionName(record.taskId, reportName)
        mongoTemplate.insert(record, collectionName)
    }

    fun buildReportCollection(taskId: String, reportName: String) {
        val collectionName = getCollectionName(taskId, reportName)
        val indexDefinition = CompoundIndexDefinition(
            Document()
                .append("taskId", 1).append("projectId", 1)
                .append("repoName", 1).append("fullPath", 1).append("status", 1)
        )
        mongoTemplate.getCollection(collectionName).drop()
        mongoTemplate.indexOps(collectionName).ensureIndex(indexDefinition)
    }

    fun setTaskStatus(context: ScanTaskContext) {
        with(context) {
            val collectionName = getCollectionName(taskId, reportName)
        }
    }

    private fun getCollectionName(taskId: String, reportName: String): String {
        return "$taskId::$reportName"
    }
}
