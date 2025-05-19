/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.metadata.service.metadata.impl

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.config.RepositoryProperties
import com.tencent.bkrepo.common.metadata.dao.metadata.MetadataLabelDao
import com.tencent.bkrepo.common.metadata.model.TMetadataLabel
import com.tencent.bkrepo.common.metadata.service.metadata.MetadataLabelService
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.repository.pojo.metadata.label.MetadataLabelDetail
import com.tencent.bkrepo.repository.pojo.metadata.label.MetadataLabelRequest
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.regex.Pattern

@Service
@Conditional(SyncCondition::class)
class MetadataLabelServiceImpl(
    private val metadataLabelDao: MetadataLabelDao,
    private val repositoryProperties: RepositoryProperties
) : MetadataLabelService {
    override fun create(request: MetadataLabelRequest) {
        with(request) {
            checkColor(labelColorMap)
            metadataLabelDao.findByProjectIdAndLabelKey(projectId, labelKey)?.let {
                throw ErrorCodeException(CommonMessageCode.RESOURCE_EXISTED, labelKey)
            }
            val userId = SecurityUtils.getUserId()
            val metadataLabel = TMetadataLabel(
                projectId = projectId,
                labelKey = labelKey,
                labelColorMap = labelColorMap,
                enumType = enumType,
                enableColorConfig = enableColorConfig,
                display = display,
                category = category,
                description = description,
                createdBy = userId,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = userId,
                lastModifiedDate = LocalDateTime.now()
            )
            metadataLabelDao.insert(metadataLabel)
            logger.info("$userId create metadata label[$metadataLabel] success")
        }
    }

    override fun update(request: MetadataLabelRequest) {
        with(request) {
            checkColor(labelColorMap)
            val metadataLabel = metadataLabelDao.findByProjectIdAndLabelKey(projectId, labelKey)
                ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, labelKey)
            val userId = SecurityUtils.getUserId()
            metadataLabel.labelColorMap = labelColorMap
            display?.let { metadataLabel.display = it }
            enumType?.let { metadataLabel.enumType = it }
            enableColorConfig?.let { metadataLabel.enableColorConfig = it }
            category?.let { metadataLabel.category = it }
            description?.let { metadataLabel.description = it }
            metadataLabel.lastModifiedBy = userId
            metadataLabel.lastModifiedDate = LocalDateTime.now()
            metadataLabelDao.save(metadataLabel)
            logger.info("$userId update metadata label[$metadataLabel] success")
        }
    }

    override fun batchSave(requests: List<MetadataLabelRequest>) {
        val oldLabels = metadataLabelDao.findByProjectId(requests.first().projectId)
        val mutableRequests = requests.toMutableList()
        oldLabels.forEach {
            val request = requests.find { request -> request.labelKey == it.labelKey }
            if (request == null) {
                delete(it.projectId, it.labelKey)
            } else {
                update(request)
                mutableRequests.remove(request)
            }
        }
        mutableRequests.forEach {
            create(it)
        }
    }

    override fun listAll(projectId: String): List<MetadataLabelDetail> {
        return metadataLabelDao.findByProjectId(projectId).map { convert(it) }
    }

    override fun detail(projectId: String, labelKey: String): MetadataLabelDetail {
        return metadataLabelDao.findByProjectIdAndLabelKey(projectId, labelKey)?.let { convert(it) }
            ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, labelKey)
    }

    override fun delete(projectId: String, labelKey: String) {
        if (repositoryProperties.systemMetadataLabels.contains(labelKey)) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, labelKey)
        }
        val result = metadataLabelDao.deleteByProjectIdAndLabelKey(projectId, labelKey)
        if (result.deletedCount == 0L) {
            throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, labelKey)
        }
        logger.info("${SecurityUtils.getUserId()} delete metadata label[$projectId/$labelKey] success")
    }

    private fun convert(tMetadataLabel: TMetadataLabel): MetadataLabelDetail {
        with(tMetadataLabel) {
            return MetadataLabelDetail(
                labelKey = labelKey,
                labelColorMap = labelColorMap,
                enumType = enumType ?: false,
                display = display ?: true,
                category = category,
                system = repositoryProperties.systemMetadataLabels.contains(labelKey),
                enableColorConfig = enableColorConfig ?: true,
                description = description ?: "",
                createdBy = createdBy,
                createdDate = createdDate,
                lastModifiedBy = lastModifiedBy,
                lastModifiedDate = lastModifiedDate
            )
        }
    }

    private fun checkColor(labelColorMap: Map<String, String>) {
        labelColorMap.values.forEach {
            if (!pattern.matcher(it).matches()) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, it)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MetadataLabelServiceImpl::class.java)
        private const val HEX_COLOR_PATTERN = "^#([a-fA-F0-9]{6}|[a-fA-F0-9]{3})\$"
        private val pattern = Pattern.compile(HEX_COLOR_PATTERN)
    }
}
