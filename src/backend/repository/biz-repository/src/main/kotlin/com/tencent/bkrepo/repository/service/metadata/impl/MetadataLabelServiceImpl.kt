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

package com.tencent.bkrepo.repository.service.metadata.impl

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.repository.dao.repository.MetadataLabelRepository
import com.tencent.bkrepo.repository.model.TMetadataLabel
import com.tencent.bkrepo.repository.pojo.metadata.label.MetadataLabelDetail
import com.tencent.bkrepo.repository.pojo.metadata.label.MetadataLabelRequest
import com.tencent.bkrepo.repository.service.metadata.MetadataLabelService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.regex.Pattern

@Service
class MetadataLabelServiceImpl(
    private val metadataLabelRepository: MetadataLabelRepository
) : MetadataLabelService {
    override fun create(request: MetadataLabelRequest) {
        with(request) {
            checkColor(labelColorMap)
            metadataLabelRepository.findByProjectIdAndLabelKey(projectId, labelKey)?.let {
                throw ErrorCodeException(CommonMessageCode.RESOURCE_EXISTED, labelKey)
            }
            val userId = SecurityUtils.getUserId()
            val metadataLabel = TMetadataLabel(
                projectId = projectId,
                labelKey = labelKey,
                labelColorMap = labelColorMap,
                display = display,
                createdBy = userId,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = userId,
                lastModifiedDate = LocalDateTime.now()
            )
            metadataLabelRepository.insert(metadataLabel)
            logger.info("$userId create metadata label[$metadataLabel] success")
        }
    }

    override fun update(request: MetadataLabelRequest) {
        with(request) {
            checkColor(labelColorMap)
            val metadataLabel = metadataLabelRepository.findByProjectIdAndLabelKey(projectId, labelKey)
                ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, labelKey)
            val userId = SecurityUtils.getUserId()
            metadataLabel.labelColorMap = labelColorMap
            display?.let { metadataLabel.display = it }
            metadataLabel.lastModifiedBy = userId
            metadataLabel.lastModifiedDate = LocalDateTime.now()
            metadataLabelRepository.save(metadataLabel)
            logger.info("$userId update metadata label[$metadataLabel] success")
        }
    }

    override fun listAll(projectId: String): List<MetadataLabelDetail> {
        return metadataLabelRepository.findByProjectId(projectId).map { convert(it) }
    }

    override fun detail(projectId: String, labelKey: String): MetadataLabelDetail {
        return metadataLabelRepository.findByProjectIdAndLabelKey(projectId, labelKey)?.let { convert(it) }
            ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, labelKey)
    }

    override fun delete(projectId: String, labelKey: String) {
        val metadataLabel = metadataLabelRepository.findByProjectIdAndLabelKey(projectId, labelKey)
            ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, labelKey)
        metadataLabelRepository.delete(metadataLabel)
        logger.info("${SecurityUtils.getUserId()} delete metadata label[$metadataLabel] success")
    }

    private fun convert(tMetadataLabel: TMetadataLabel): MetadataLabelDetail {
        with(tMetadataLabel) {
            return MetadataLabelDetail(
                labelKey = labelKey,
                labelColorMap = labelColorMap,
                display = display ?: true,
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
