/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.job.backup.service.impl.base

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.job.backup.pojo.query.enums.BackupDataEnum
import com.tencent.bkrepo.job.backup.pojo.record.BackupContext
import com.tencent.bkrepo.job.backup.service.BackupDataHandler
import org.springframework.data.mongodb.core.query.Criteria

object BackupDataMappings {

    private val mappers = mutableMapOf<BackupDataEnum, BackupDataHandler>()

    init {
        addMapper(SpringContextUtils.getBean(BackupUserDataHandler::class.java))
        addMapper(SpringContextUtils.getBean(BackupAccountDataHandler::class.java))
        addMapper(SpringContextUtils.getBean(BackupRoleDataHandler::class.java))
        addMapper(SpringContextUtils.getBean(BackupTemporaryTokenDataHandler::class.java))
        addMapper(SpringContextUtils.getBean(BackupPermissionDataHandler::class.java))
        addMapper(SpringContextUtils.getBean(BackupProjectDataHandler::class.java))
        addMapper(SpringContextUtils.getBean(BackupRepositoryDataHandler::class.java))
        addMapper(SpringContextUtils.getBean(BackupPackageDataHandler::class.java))
        addMapper(SpringContextUtils.getBean(BackupPackageVersionDataHandler::class.java))
        addMapper(SpringContextUtils.getBean(BackupNodeDataHandler::class.java))
        addMapper(SpringContextUtils.getBean(BackupMavenMetadataDataHandler::class.java))
        addMapper(SpringContextUtils.getBean(BackupConanMetadataDataHandler::class.java))
    }

    private fun addMapper(mapper: BackupDataHandler) {
        mappers[mapper.dataType()] = mapper
    }

    fun buildQueryCriteria(backupDataEnum: BackupDataEnum, context: BackupContext): Criteria {
        val mapper = mappers[backupDataEnum] ?: return Criteria()
        return mapper.buildQueryCriteria(context)
    }

    fun getCollectionName(backupDataEnum: BackupDataEnum, context: BackupContext): String {
        val mapper = mappers[backupDataEnum] ?: return StringPool.EMPTY
        return mapper.getCollectionName(backupDataEnum, context)
    }

    fun <T> preBackupDataHandler(record: T, backupDataEnum: BackupDataEnum, context: BackupContext) {
        val mapper = mappers[backupDataEnum] ?: return
        mapper.preBackupDataHandler(record, backupDataEnum, context)
    }

    fun postBackupDataHandler(backupDataEnum: BackupDataEnum, context: BackupContext) {
        val mapper = mappers[backupDataEnum] ?: return
        mapper.postBackupDataHandler(context)
    }

    fun <T> returnLastId(data: T, backupDataEnum: BackupDataEnum): String {
        val mapper = mappers[backupDataEnum] ?: return StringPool.EMPTY
        return mapper.returnLastId(data)
    }

    fun preRestoreDataHandler(backupDataEnum: BackupDataEnum, context: BackupContext) {
        val mapper = mappers[backupDataEnum] ?: return
        mapper.preRestoreDataHandler(backupDataEnum, context)
    }

    fun <T> storeRestoreDataHandler(record: T, backupDataEnum: BackupDataEnum, context: BackupContext) {
        val mapper = mappers[backupDataEnum] ?: return
        mapper.storeRestoreDataHandler(record, backupDataEnum, context)
    }

    fun getSpecialDataEnum(backupDataEnum: BackupDataEnum, context: BackupContext): List<BackupDataEnum>? {
        val mapper = mappers[backupDataEnum] ?: return null
        return mapper.getSpecialDataEnum(context)
    }
}
