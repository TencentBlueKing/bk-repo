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

package com.tencent.bkrepo.oci.controller.service

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.oci.api.OciClient
import com.tencent.bkrepo.oci.dao.OciReplicationRecordDao
import com.tencent.bkrepo.oci.model.TOciReplicationRecord
import com.tencent.bkrepo.oci.pojo.artifact.OciManifestArtifactInfo
import com.tencent.bkrepo.oci.pojo.third.OciReplicationRecordInfo
import com.tencent.bkrepo.oci.service.OciOperationService
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.web.bind.annotation.RestController

@RestController
class OciPackageController(
    private val operationService: OciOperationService,
    private val ociReplicationRecordDao: OciReplicationRecordDao
): OciClient {
    override fun packageCreate(record: OciReplicationRecordInfo): Response<Void> {
        with(record) {
            val ociArtifactInfo = OciManifestArtifactInfo(
                projectId, repoName, packageName, "", packageVersion, false
            )
            val result = operationService.createPackageForThirdPartyImage(
                manifestPath = manifestPath,
                ociArtifactInfo = ociArtifactInfo,
            )
            if (result) {
                val criteria = Criteria.where(TOciReplicationRecord::projectId.name).`is`(projectId)
                    .and(TOciReplicationRecord::repoName.name).`is`(repoName)
                    .and(TOciReplicationRecord::packageName.name).`is`(packageName)
                    .and(TOciReplicationRecord::packageVersion.name).`is`(packageVersion)
                    .and(TOciReplicationRecord::manifestPath.name).`is`(manifestPath)
                val query = Query(criteria)
                ociReplicationRecordDao.remove(query)
            }
            return ResponseBuilder.success()
        }
    }
}
