package com.tencent.bkrepo.repository.service.schedule.impl

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.repository.dao.ScheduleLoadDao
import com.tencent.bkrepo.repository.model.TScheduleLoad
import com.tencent.bkrepo.repository.model.TScheduleRule
import com.tencent.bkrepo.repository.pojo.schedule.ScheduleLoadCreateRequest
import com.tencent.bkrepo.repository.pojo.schedule.ScheduleQueryRequest
import com.tencent.bkrepo.repository.pojo.schedule.ScheduleResult
import com.tencent.bkrepo.repository.pojo.schedule.ScheduleRule
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
    override fun saveScheduleLoad(request: ScheduleLoadCreateRequest) {

        val criteria = Criteria.where("userId").`is`(request.userId)
            .and("projectId").`is`(request.projectId)
            .and("pipeLineId").`is`(request.pipeLineId)
            .and("buildId").`is`(request.buildId)

        val task = scheduleLoadDao.findOne(Query(criteria))

        val rules = request.rules.map { (key, value) ->
            TScheduleRule(key = key, value = value)
        }

        val scheduleLoad = TScheduleLoad(
            id = task?.id,
            userId = request.userId,
            projectId = request.projectId,
            pipeLineId = request.pipeLineId,
            buildId = request.buildId,
            cronExpression = request.cronExpression,
            isEnabled = request.isEnabled,
            platform = request.platform,
            rules = rules,
            createdDate = task?.createdDate ?: LocalDateTime.now(),
            lastModifiedDate = LocalDateTime.now(),
        )

        try {
            scheduleLoadDao.save(scheduleLoad)
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
        request.pipeLineId?.let { criteria.and("pipeLineId").`is`(it) }
        request.buildId?.let { criteria.and("buildId").`is`(it) }
        request.isEnable?.let { criteria.and("isEnabled").`is`(it) }

        val query = Query(criteria)

        val records = scheduleLoadDao.find(query).map { tScheduleLoad ->
            val scheduleRules = tScheduleLoad.rules.map { tScheduleRule ->
                ScheduleRule(
                    key = tScheduleRule.key,
                    value = tScheduleRule.value,
                )
            }

            ScheduleResult(
                id = tScheduleLoad.id,
                projectId = tScheduleLoad.projectId,
                pipeLineId = tScheduleLoad.pipeLineId,
                buildId = tScheduleLoad.buildId,
                cronExpression = tScheduleLoad.cronExpression,
                isEnabled = tScheduleLoad.isEnabled,
                platform = tScheduleLoad.platform,
                rules = scheduleRules,
            )
        }

        val pageRequest = Pages.ofRequest(request.pageNumber, request.pageSize)
        val totalRecords = scheduleLoadDao.count(query)

        return Pages.ofResponse(pageRequest, totalRecords, records)
    }

    override fun getScheduleLoadById(id: String): TScheduleLoad? {
        return scheduleLoadDao.findById(id)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ScheduleLoadServiceImpl::class.java)
    }
}