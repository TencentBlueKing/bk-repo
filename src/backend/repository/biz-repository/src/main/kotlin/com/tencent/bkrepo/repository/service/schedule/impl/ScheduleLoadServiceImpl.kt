package com.tencent.bkrepo.repository.service.schedule.impl

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.metadata.model.TMetadata
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.repository.dao.ScheduleLoadDao
import com.tencent.bkrepo.repository.model.TScheduleLoad
import com.tencent.bkrepo.repository.pojo.schedule.ScheduleLoadCreateRequest
import com.tencent.bkrepo.repository.pojo.schedule.ScheduleMetadata
import com.tencent.bkrepo.repository.pojo.schedule.ScheduleQueryRequest
import com.tencent.bkrepo.repository.pojo.schedule.ScheduleResult
import com.tencent.bkrepo.repository.service.schedule.ScheduleLoadService
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ScheduleLoadServiceImpl(
    private val scheduleLoadDao: ScheduleLoadDao
) : ScheduleLoadService {
    override fun createScheduleLoad(request: ScheduleLoadCreateRequest) {

        val metadata = request.nodeMetadata.map { (key, value) ->
            TMetadata(key = key, value = value)
        }.toMutableList()

        val scheduleLoad = TScheduleLoad(
            userId = request.userId,
            projectId = request.projectId,
            repoName = request.repoName,
            fullPathRegex = request.fullPathRegex,
            nodeMetadata = metadata,
            cronExpression = request.cronExpression,
            isEnabled = request.isEnabled,
            platform = request.platform,
            type = request.type,
            createdDate = LocalDateTime.now(),
            lastModifiedDate = LocalDateTime.now(),
        )

        try {
            scheduleLoadDao.insert(scheduleLoad)
        } catch (exception: DuplicateKeyException) {
            logger.warn("Duplicate key when saving schedule load: $request")
        }
    }

    override fun removeScheduleLoad(id: String) {
        scheduleLoadDao.remove(Query.query(Criteria.where("_id").`is`(id)))
    }

    override fun updateScheduleStatus(id: String, isEnabled: Boolean) {
        val query = Query.query(Criteria.where("_id").`is`(id))
        val update = Update().set("isEnabled", isEnabled).set("lastModifiedDate", LocalDateTime.now())
        scheduleLoadDao.updateFirst(query, update)
    }

    override fun queryScheduleLoad(userId: String, request: ScheduleQueryRequest): Page<ScheduleResult> {
        val criteria = Criteria.where("userId").`is`(userId)
        request.projectId?.let { criteria.and("projectId").`is`(it) }
        request.repoName?.let { criteria.and("repoName").`is`(it) }
        request.fullPathRegex?.let { criteria.and("fullPathRegex").`is`(it) }
        request.isEnable?.let { criteria.and("isEnabled").`is`(it) }

        // 处理元数据查询条件
        request.nodeMetadata?.takeIf { it.isNotEmpty() }?.let { metadataMap ->
            val metadataCriteria = metadataMap.map { (key, value) ->
                Criteria.where("nodeMetadata").elemMatch(
                    Criteria.where("key").`is`(key)
                        .and("value").`is`(value)
                )
            }
            criteria.andOperator(*metadataCriteria.toTypedArray())
        }

        val query = Query(criteria)

        // 创建分页请求对象
        val pageRequest = Pages.ofRequest(request.pageNumber, request.pageSize)

        val count = scheduleLoadDao.count(query)

        query.with(pageRequest)

        // 查询分页数据
        val records = scheduleLoadDao.find(query).map { tScheduleLoad ->
            val scheduleNodeMetadata = tScheduleLoad.nodeMetadata?.map { metadata ->
                ScheduleMetadata(
                    key = metadata.key,
                    value = metadata.value,
                )
            } ?: emptyList()

            ScheduleResult(
                id = tScheduleLoad.id,
                projectId = tScheduleLoad.projectId,
                repoName = tScheduleLoad.repoName?: "",
                fullPathRegex = tScheduleLoad.fullPathRegex?: "",
                nodeMetadata = scheduleNodeMetadata,
                cronExpression = tScheduleLoad.cronExpression,
                isEnabled = tScheduleLoad.isEnabled,
                platform = tScheduleLoad.platform,
            )
        }

        return Pages.ofResponse(pageRequest, count, records)
    }

    override fun getScheduleLoadById(id: String): TScheduleLoad? {
        return scheduleLoadDao.findById(id)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ScheduleLoadServiceImpl::class.java)
    }
}