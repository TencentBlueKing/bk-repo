package com.tencent.bkrepo.executor.dao

import com.tencent.bkrepo.executor.pojo.ReportScanRecord
import com.tencent.bkrepo.executor.pojo.context.ScanTaskContext
import com.tencent.bkrepo.executor.pojo.enums.ScanTaskReport
import com.tencent.bkrepo.executor.pojo.response.FileRunResponse
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition
import org.springframework.stereotype.Component

@Component
class ScanRunResult {

    @Autowired
    @Qualifier(value = "secondaryMongoTemplate")
    lateinit var mongoTemplate: MongoTemplate

    fun buildReportCollection(taskId: String, reportName: String) {
        val collectionName = getReportCollName(taskId, reportName)
        val indexDefinition = CompoundIndexDefinition(
            Document()
                .append("taskId", 1).append("projectId", 1)
                .append("repoName", 1).append("fullPath", 1)
        ).unique()
        mongoTemplate.getCollection(collectionName).drop()
        mongoTemplate.indexOps(collectionName).ensureIndex(indexDefinition)
    }

    fun buildTaskCollection(taskId: String) {
        val collectionName = getTaskCollName(taskId)
        val indexDefinition = CompoundIndexDefinition(
            Document()
                .append("taskId", 1).append("projectId", 1)
                .append("repoName", 1).append("fullPath", 1)
        ).unique()
        mongoTemplate.getCollection(collectionName).drop()
        mongoTemplate.indexOps(collectionName).ensureIndex(indexDefinition)
    }

    fun getTotalTaskCount(taskId: String): Long {
        val collectionName = getTaskCollName(taskId)
        val filter = Document()
        filter["taskId"] = taskId
        return mongoTemplate.getCollection(collectionName).countDocuments(filter)
    }

    fun getTotalTaskRecord(taskId: String, pageNum: Int?, pageSize: Int?): List<FileRunResponse> {
        val collectionName = getTaskCollName(taskId)
        val filter = Document()
        filter["taskId"] = taskId
        val result = mutableListOf<FileRunResponse>()
        val queryPageSize = pageSize ?: MAX_PAGE_SIZE
        val queryPageNum = pageNum ?: 1
        val skip = (queryPageNum - 1) * queryPageSize
        mongoTemplate.getCollection(collectionName).find(filter).skip(skip).limit(queryPageSize).forEach {
            val fileResponse = FileRunResponse(
                projectId = it["projectId"] as String,
                repoName = it["repoName"] as String,
                fullPath = it["fullPath"] as String,
                status = it["status"] as String
            )
            result.add(fileResponse)
        }
        return result
    }

    fun geTaskRecord(
        taskId: String,
        reportName: ScanTaskReport?,
        projectId: String,
        repoName: String,
        fullPath: String
    ): String? {
        val report = reportName ?: ScanTaskReport.CVESEC
        val collectionName = getReportCollName(taskId, report.value)
        val filter = Document().append("taskId", taskId).append("projectId", projectId).append("repoName", repoName)
            .append("fullPath", fullPath)
        mongoTemplate.getCollection(collectionName).find(filter).forEach {
            return it["content"] as String
        }
        return null
    }

    fun setReport(record: ReportScanRecord, reportName: String) {
        val collectionName = getReportCollName(record.taskId, reportName)
        mongoTemplate.insert(record, collectionName)
    }

    fun setTaskStatus(context: ScanTaskContext): Boolean {
        with(context) {
            try {
                val collectionName = getTaskCollName(taskId)
                val filter = Document()
                filter["taskId"] = taskId
                filter["projectId"] = projectId
                filter["repoName"] = repoName
                filter["fullPath"] = fullPath
                if (mongoTemplate.getCollection(collectionName).find(filter).count() == 0) {
                    filter["status"] = status.toString()
                    mongoTemplate.getCollection(collectionName).insertOne(filter)
                    return true
                }
                val update = Document().append("status", status.toString()).append("taskId", taskId)
                    .append("projectId", projectId).append("repoName", repoName).append("fullPath", fullPath)
                mongoTemplate.getCollection(collectionName).replaceOne(filter, update)
                return true
            } catch (e: Exception) {
                logger.warn("set task status exception[$context, $e]")
                return false
            }
        }
    }

    private fun getReportCollName(taskId: String, reportName: String): String {
        return "$taskId::$reportName"
    }

    private fun getTaskCollName(taskId: String): String {
        return "$taskId::status"
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ScanRunResult::class.java)
        private const val MAX_PAGE_SIZE = 100
    }
}
