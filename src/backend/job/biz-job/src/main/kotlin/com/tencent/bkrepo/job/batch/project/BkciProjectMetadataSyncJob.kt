/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.job.batch.project

import com.tencent.bkrepo.common.api.exception.SystemErrorException
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.service.util.okhttp.HttpClientBuilderFactory
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.repository.pojo.project.ProjectMetadata
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 定时从蓝盾同步项目元数据
 */
@Component
@EnableConfigurationProperties(BkciProjectMetadataSyncJobProperties::class)
class BkciProjectMetadataSyncJob(
    private val properties: BkciProjectMetadataSyncJobProperties,
) : DefaultContextMongoDbJob<BkciProjectMetadataSyncJob.Project>(properties) {

    val client by lazy { HttpClientBuilderFactory.create().build() }

    override fun run(row: Project, collectionName: String, context: JobContext) {
        if (properties.ignoredProjectPrefix.any { row.name.startsWith(it) }) {
            return
        }
        sync(row)
    }

    private fun sync(project: Project) {
        val projectName = project.name
        logger.info("start sync $projectName from bkci")
        val url = "${properties.ciServer}/ms/project/api/open/project/$projectName"
        val reqBuilder = Request.Builder()
            .get()
            .url(url)
            .header(DEVOPS_BK_TOKEN, properties.ciToken)
            .header(DEVOPS_PROJECT_ID, projectName)
            .get()
        if (properties.routeToGray) {
            reqBuilder.header(DEVOPS_GATEWAY_TAG, "rbac-gray")
        }

        val res = client.newCall(reqBuilder.build()).execute()
        var err: String? = null
        if (res.isSuccessful) {
            val data = res.body!!.byteStream().readJsonString<BkciResponse>()
            if (data.code == 0) {
                updateProjectInfo(project, data.data!!)
                logger.info("sync $projectName from bkci success")
            } else {
                err = data.message
            }
        } else {
            err = res.body?.string()
        }
        if (err != null && !err.contains("not found", ignoreCase = true)) {
            logger.error("sync $projectName from bkci failed, code[${res.code}], message[$err]")
            throw SystemErrorException()
        } else if (err != null) {
            logger.warn("sync $projectName from bkci failed, code[${res.code}], message[$err]")
        }
    }

    private fun updateProjectInfo(project: Project, bkciProject: BkciProject) {
        val query = Query(Project::name.isEqualTo(project.name))
        val newMetadata = ArrayList<ProjectMetadata>(8)
        bkciProject.bgId?.let { newMetadata.add(ProjectMetadata(ProjectMetadata.KEY_BG_ID, it)) }
        bkciProject.bgName?.let { newMetadata.add(ProjectMetadata(ProjectMetadata.KEY_BG_NAME, it)) }
        bkciProject.deptId?.let { newMetadata.add(ProjectMetadata(ProjectMetadata.KEY_DEPT_ID, it)) }
        bkciProject.deptName?.let { newMetadata.add(ProjectMetadata(ProjectMetadata.KEY_DEPT_NAME, it)) }
        bkciProject.centerId?.let { newMetadata.add(ProjectMetadata(ProjectMetadata.KEY_CENTER_ID, it)) }
        bkciProject.centerName?.let { newMetadata.add(ProjectMetadata(ProjectMetadata.KEY_CENTER_NAME, it)) }
        bkciProject.productId?.let { newMetadata.add(ProjectMetadata(ProjectMetadata.KEY_PRODUCT_ID, it)) }
        bkciProject.enabled?.let { newMetadata.add(ProjectMetadata(ProjectMetadata.KEY_ENABLED, it)) }

        val metadataMap = project.metadata.associateByTo(HashMap()) { it.key }
        newMetadata.forEach { metadataMap[it.key] = it }
        val update = Update().set(Project::metadata.name, metadataMap.values)
        mongoTemplate.updateFirst(query, update, "project")
    }

    override fun getLockAtMostFor(): Duration = Duration.ofDays(1)

    override fun collectionNames() = listOf(COLLECTION_NAME)

    override fun buildQuery() = Query()

    override fun mapToEntity(row: Map<String, Any?>): Project {
        val metadata = (row[Project::metadata.name] as? List<Map<String, Any>>)?.map {
            ProjectMetadata(it[ProjectMetadata::key.name].toString(), it[ProjectMetadata::value.name]!!)
        } ?: emptyList()

        return Project(row[Project::name.name]!! as String, metadata)
    }

    override fun entityClass() = Project::class

    companion object {
        private val logger = LoggerFactory.getLogger(BkciProjectMetadataSyncJob::class.java)
        private const val COLLECTION_NAME = "project"
        private const val DEVOPS_BK_TOKEN = "X-DEVOPS-BK-TOKEN"
        private const val DEVOPS_PROJECT_ID = "X-DEVOPS-PROJECT-ID"
        private const val DEVOPS_GATEWAY_TAG = "X-GATEWAY-TAG"
    }

    data class Project(val name: String, val metadata: List<ProjectMetadata> = emptyList())

    private data class BkciResponse(
        val code: Int? = null,
        val data: BkciProject? = null,
        val message: String? = null,
        val status: String? = null,
    )

    private data class BkciProject(
        /**
         * 项目ID
         */
        val projectId: String,
        /**
         * 项目名称
         */
        val projectName: String,
        /**
         * 项目代码（蓝盾项目Id）
         */
        val projectCode: String,
        /**
         * 项目类型
         */
        val projectType: Int?,
        /**
         * 创建时间
         */
        val createdAt: String?,
        /**
         * 创建人
         */
        val creator: String?,
        /**
         * 事业群ID
         */
        val bgId: String?,
        /**
         * 事业群名字
         */
        val bgName: String?,
        /**
         * 中心ID
         */
        val centerId: String?,
        /**
         * 中心名称
         */
        val centerName: String?,
        /**
         * 部门ID
         */
        val deptId: String?,
        /**
         * 部门名称
         */
        val deptName: String?,
        /**
         * 描述
         */
        val description: String?,
        /**
         * 英文缩写
         */
        val englishName: String,
        /**
         * 启用
         */
        val enabled: Boolean?,
        /**
         * 是否灰度
         */
        val gray: Boolean,
        /**
         * 运营产品ID
         */
        val productId: Int?,
    )
}
