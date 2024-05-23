/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.auth.service.impl

import com.tencent.bkrepo.auth.dao.RootDirectoryPermissionDao
import com.tencent.bkrepo.auth.model.TRootDirectoryPermission
import com.tencent.bkrepo.auth.pojo.rootdirectorypermission.UpdateRootDirectoryPermissionRequest
import com.tencent.bkrepo.auth.service.RootDirectoryPermissionService
import com.tencent.bkrepo.common.security.util.SecurityUtils
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.where
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class RootDirectoryPermissionServiceImpl (
    private val rootDirectoryPermissionDao: RootDirectoryPermissionDao
):  RootDirectoryPermissionService{
    override fun createPermission(updateRequest: UpdateRootDirectoryPermissionRequest): Boolean {
        val rootDirectoryPermission = TRootDirectoryPermission(
            projectId = updateRequest.projectId,
            repoName = updateRequest.repoName,
            createdAt = LocalDateTime.now(),
            createdBy = updateRequest.userId,
            updatedBy = updateRequest.userId,
            updateAt = LocalDateTime.now(),
            status = updateRequest.status
        )
        rootDirectoryPermissionDao.save(rootDirectoryPermission)
        return true
    }

    override fun modifyPermission(id: String, status: Boolean) {
        val findAndModifyOptions = FindAndModifyOptions()
        findAndModifyOptions.upsert(true)
        rootDirectoryPermissionDao.findAndModify(
            Query.query(where(TRootDirectoryPermission::id).isEqualTo(id)),
            Update.update(TRootDirectoryPermission::status.name, status)
                .set(TRootDirectoryPermission::updateAt.name, LocalDateTime.now())
                .set(TRootDirectoryPermission::updatedBy.name,SecurityUtils.getUserId()),
            findAndModifyOptions,
            TRootDirectoryPermission::class.java
        )
    }

    override fun createOrUpdatePermission(updateRequest: UpdateRootDirectoryPermissionRequest): Boolean {
        if (!checkPermissionExist(updateRequest.projectId, updateRequest.repoName)) {
            createPermission(updateRequest)
        } else {
            rootDirectoryPermissionDao.upsert(
                Query.query(
                    Criteria.where(TRootDirectoryPermission::projectId.name).`is`(updateRequest.projectId)
                        .and(TRootDirectoryPermission::repoName.name).`is`(updateRequest.repoName)),
                Update.update(TRootDirectoryPermission::status.name, updateRequest.status)
                    .set(TRootDirectoryPermission::updateAt.name,LocalDateTime.now())
                    .set(TRootDirectoryPermission::updatedBy.name,updateRequest.userId)
            )
        }
        return true
    }

    override fun getPermissionByStatus(status: Boolean): List<TRootDirectoryPermission> {
        return rootDirectoryPermissionDao.find(
            Query.query(where(TRootDirectoryPermission::status).isEqualTo(status))
        )
    }

    override fun getPermissionByRepoConfig(projectId: String, repoName: String): List<TRootDirectoryPermission> {
        return rootDirectoryPermissionDao.find(
            Query.query(
                Criteria.where(TRootDirectoryPermission::projectId.name).`is`(projectId)
                .and(TRootDirectoryPermission::repoName.name).`is`(repoName))
        )
    }

    override fun checkPermissionExist(id: String): Boolean {
        return rootDirectoryPermissionDao.exists(
            Query.query(
                Criteria.where(TRootDirectoryPermission::id.name).`is`(id)
            )
        )
    }

    private fun checkPermissionExist(projectId: String, repoName: String): Boolean {
        return rootDirectoryPermissionDao.exists(
            Query.query(
                Criteria.where(TRootDirectoryPermission::projectId.name).`is`(projectId)
                    .and(TRootDirectoryPermission::repoName.name).`is`(repoName))
        )
    }
}