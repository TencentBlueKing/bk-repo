/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.auth.service.bkiamv3.callback

import com.tencent.bk.sdk.iam.dto.PageInfoDTO
import com.tencent.bk.sdk.iam.dto.callback.request.CallbackRequestDTO
import com.tencent.bk.sdk.iam.dto.callback.response.BaseDataResponseDTO
import com.tencent.bk.sdk.iam.dto.callback.response.CallbackBaseResponseDTO
import com.tencent.bk.sdk.iam.dto.callback.response.InstanceInfoDTO
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.repository.api.ProjectClient
import com.tencent.bkrepo.repository.pojo.project.ProjectRangeQueryRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 项目资源回调实现
 */
@Component
class BkiamProjectResourceService(
    private val projectClient: ProjectClient
): BkiamResourceBaseService {
    override fun resourceType(): ResourceType {
        return ResourceType.PROJECT
    }

    override fun fetchInstanceInfo(request: CallbackRequestDTO): CallbackBaseResponseDTO {
        logger.info("v3 Project fetchInstanceInfo, request $request")
        val ids = request.filter.idList.map { it.toString() }
        return buildFetchInstanceInfoResponseDTO(filterProjectInfo(ids))
    }

    override fun searchInstanceInfo(request: CallbackRequestDTO): CallbackBaseResponseDTO {
        logger.info("v3 Project searchInstanceInfo, request $request")
        val ids = listOf(request.filter.keyword)
        return buildSearchInstanceResponseDTO(listProject(page = request.page, idList = ids))
    }

    override fun listInstanceInfo(request: CallbackRequestDTO): CallbackBaseResponseDTO {
        logger.info("v3 Project listInstanceInfo, request $request")
        return buildListInstanceResponseDTO(listProject(page = request.page))
    }


    private fun filterProjectInfo(idList: List<String>): List<InstanceInfoDTO> {
        logger.info("v3 filterProjectInfo, idList: $idList")
        val projectPage = projectClient.rangeQuery(ProjectRangeQueryRequest(idList, 0, 10000)).data!!

        return  projectPage.records.map {
            val entity = InstanceInfoDTO()
            entity.id = it!!.name
            entity.displayName = it.displayName
            entity
        }
    }

    private fun listProject(
        page: PageInfoDTO? = null,
        idList: List<String> = emptyList()
    ): BaseDataResponseDTO<InstanceInfoDTO> {
        logger.info("v3 listProject, page $page, ids: $idList")
        var offset = 0L
        var limit = 20
        if (page != null) {
            offset = page.offset
            limit = page.limit.toInt()
        }
        val projectPage = projectClient.rangeQuery(ProjectRangeQueryRequest(idList, offset, limit)).data!!
        val projects = projectPage.records.map {
            val entity = InstanceInfoDTO()
            entity.id = it!!.name
            entity.displayName = it.displayName
            entity
        }
        return buildBaseDataResponseDTO(projectPage.totalRecords, projects)
    }
    companion object {
        private val logger = LoggerFactory.getLogger(BkiamProjectResourceService::class.java)
    }
}
