/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.rpm.servcie

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.rpm.artifact.RpmArtifactInfo
import com.tencent.bkrepo.rpm.artifact.repository.RpmLocalRepository
import org.springframework.stereotype.Service

@Service
class RpmWebService : ArtifactService() {

    @Permission(type = ResourceType.REPO, action = PermissionAction.DELETE)
    fun deletePackage(rpmArtifactInfo: RpmArtifactInfo, packageKey: String) {
        val context = ArtifactRemoveContext()
        repository.remove(context)
    }

    @Permission(type = ResourceType.REPO, action = PermissionAction.DELETE)
    fun delete(rpmArtifactInfo: RpmArtifactInfo, packageKey: String, version: String?) {
        val context = ArtifactRemoveContext()
        repository.remove(context)
    }

    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    fun artifactDetail(rpmArtifactInfo: RpmArtifactInfo, packageKey: String, version: String?): Any? {
        val context = ArtifactQueryContext()
        return repository.query(context)
    }

    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    fun extList(@ArtifactPathVariable rpmArtifactInfo: RpmArtifactInfo, page: Int, size: Int): Page<String> {
        val context = ArtifactSearchContext()
        val repository = ArtifactContextHolder.getRepository(RepositoryCategory.LOCAL)
        return (repository as RpmLocalRepository).extList(context, page, size)
    }
}
