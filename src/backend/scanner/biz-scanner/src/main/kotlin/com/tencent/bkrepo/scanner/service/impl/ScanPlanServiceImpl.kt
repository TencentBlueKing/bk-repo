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

package com.tencent.bkrepo.scanner.service.impl

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.scanner.pojo.ScanPlan
import com.tencent.bkrepo.scanner.pojo.request.ArtifactPlanRelationRequest
import com.tencent.bkrepo.scanner.pojo.request.PlanArtifactRequest
import com.tencent.bkrepo.scanner.pojo.response.ArtifactPlanRelation
import com.tencent.bkrepo.scanner.pojo.response.PlanArtifactInfo
import com.tencent.bkrepo.scanner.pojo.response.ScanPlanInfo
import com.tencent.bkrepo.scanner.service.ScanPlanService
import org.springframework.stereotype.Service

@Service
class ScanPlanServiceImpl : ScanPlanService {
    override fun create(request: ScanPlan): ScanPlan {
        TODO("Not yet implemented")
    }

    override fun list(projectId: String, type: String?): List<ScanPlan> {
        TODO("Not yet implemented")
    }

    override fun page(
        projectId: String,
        type: String?,
        name: String?,
        pageNumber: Int,
        pageSize: Int
    ): Page<ScanPlanInfo> {
        TODO("Not yet implemented")
    }

    override fun find(projectId: String, id: String): ScanPlan? {
        TODO("Not yet implemented")
    }

    override fun delete(projectId: String, id: String) {
        TODO("Not yet implemented")
    }

    override fun update(request: ScanPlan): ScanPlan {
        TODO("Not yet implemented")
    }

    override fun latestScanTask(projectId: String, id: String): ScanPlanInfo? {
        TODO("Not yet implemented")
    }

    override fun planArtifactPage(request: PlanArtifactRequest): Page<PlanArtifactInfo> {
        TODO("Not yet implemented")
    }

    override fun artifactPlanList(request: ArtifactPlanRelationRequest): List<ArtifactPlanRelation> {
        TODO("Not yet implemented")
    }

    override fun artifactPlanStatus(request: ArtifactPlanRelationRequest): String {
        TODO("Not yet implemented")
    }
}
