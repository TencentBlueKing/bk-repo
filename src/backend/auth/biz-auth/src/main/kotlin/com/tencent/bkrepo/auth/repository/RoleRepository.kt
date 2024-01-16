/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.auth.repository

import com.tencent.bkrepo.auth.model.TRole
import com.tencent.bkrepo.auth.pojo.enums.RoleType
import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface RoleRepository : MongoRepository<TRole, String> {
    fun findTRoleById(id: ObjectId): TRole?
    fun deleteTRolesById(id: ObjectId)
    fun findFirstById(id: String): TRole?
    fun findByIdIn(roles: List<String>): List<TRole>
    fun findByTypeAndProjectId(type: RoleType, projectId: String): List<TRole>
    fun findByTypeAndProjectIdAndRoleIdNotIn(type: RoleType, projectId: String, roles: List<String>): List<TRole>
    fun findByTypeAndProjectIdAndAdmin(type: RoleType, projectId: String, admin: Boolean): List<TRole>
    fun findByProjectIdAndRepoNameAndType(projectId: String, repoName: String, type: RoleType): List<TRole>
    fun findFirstByRoleIdAndProjectId(roleId: String, projectId: String): TRole?
    fun findFirstByProjectIdAndTypeAndName(projectId: String, type: RoleType, name: String): TRole?
    fun findFirstByRoleIdAndProjectIdAndRepoName(roleId: String, projectId: String, repoName: String): TRole?
    fun findByProjectIdAndTypeAndAdminAndIdIn(
        projectId: String,
        type: RoleType,
        admin: Boolean,
        ids: List<String>
    ): List<TRole>

    fun findByProjectIdAndTypeAndAdminAndRepoNameAndIdIn(
        projectId: String,
        type: RoleType,
        admin: Boolean,
        repoName: String,
        ids: List<String>
    ): List<TRole>


}
