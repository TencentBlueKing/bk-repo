package com.tencent.bkrepo.job.batch.task.project

import com.tencent.bkrepo.common.api.util.okhttp.HttpClientBuilderFactory
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.metadata.model.TProject
import com.tencent.bkrepo.common.metadata.pojo.webhook.BkCiDevXEnabledPayload
import com.tencent.bkrepo.common.metadata.service.webhook.BkciWebhookListener
import com.tencent.bkrepo.job.batch.base.DefaultContextJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.devops.api.pojo.Response
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component

@Component
class DevXProjectSyncJob(
    private val properties: DevXProjectSyncJobProperties,
    private val mongoTemplate: MongoTemplate,
    private val bkciWebhookListener: BkciWebhookListener
) : DefaultContextJob(properties) {

    val client by lazy { HttpClientBuilderFactory.create().build() }

    override fun doStart0(jobContext: JobContext) {
        val projects = mongoTemplate.findAll(TProject::class.java, "project").map { it.name }
        var page = 1
        while (true) {
            val request = Request.Builder()
                .url("${properties.url}?page=$page&pageSize=${properties.pageSize}")
                .header("X-Bkapi-Authorization", headerStr())
                .get()
                .build()
            val devXProjects = getDevXProjects(request)
            if (devXProjects.isEmpty()) {
                break
            }
            devXProjects.forEach {
                jobContext.total.addAndGet(1)
                if (!projects.contains(it.projectId)) {
                    create(it)
                    jobContext.success.addAndGet(1)
                }
            }
            page++
        }
    }

    private fun getDevXProjects(request: Request): List<DevXProject> {
        return try {
            client.newCall(request).execute().use {
                val data = it.body?.string()
                if (!it.isSuccessful) {
                    logger.error("request url [${request.url}] failed, code[${it.code}], message[$data]")
                    emptyList()
                } else {
                    data?.readJsonString<Response<List<DevXProject>>>()?.data ?: emptyList()
                }
            }
        } catch (e: Exception) {
            logger.error("get devx projects[${request.url}] failed: ", e)
            emptyList()
        }
    }

    private fun create(devXProject: DevXProject) {
        bkciWebhookListener.onDevXEnabled(BkCiDevXEnabledPayload(
            projectName = devXProject.projectName,
            projectCode = devXProject.projectId,
            bgId = null,
            bgName = null,
            centerId = null,
            centerName = null,
            deptId = null,
            deptName = null,
            englishName = devXProject.projectName,
            productId = null
        ))
    }

    private fun headerStr(): String {
        return mapOf(
            "bk_app_code" to properties.appCode,
            "bk_app_secret" to properties.appSecret
        ).toJsonString().replace("\\s".toRegex(), "")
    }


    data class DevXProject(
        val projectId: String,
        val projectName: String
    )

    companion object {
        private val logger = LoggerFactory.getLogger(DevXProjectSyncJob::class.java)
    }


}
