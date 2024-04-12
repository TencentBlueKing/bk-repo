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

package com.tencent.bkrepo.job.migrate.pojo

/**
 * 制品数据迁移任务执行状态
 */
enum class MigrateRepoStorageTaskState {
    /**
     * 等待执行
     */
    PENDING,

    /**
     * 执行迁移中
     */
    MIGRATING,

    /**
     * 旧数据迁移完成
     */
    MIGRATE_FINISHED,

    /**
     * 矫正数据中，将迁移任务启动之后上传到旧存储中的数据迁移到新存储中
     * 此过程通常等待MIGRATING启动之后一段时间才执行，避免还有制品正在上传中导致数据丢失
     */
    CORRECTING,

    /**
     * 数据矫正结束，
     */
    CORRECT_FINISHED,

    /**
     * 尝试重新传输迁移失败的node，前面两个过程中迁移失败的node重新完成迁移后即可结束整个迁移流程
     */
    MIGRATING_FAILED_NODE,

    /**
     * 迁移结束
     */
    FINISHED;
}
