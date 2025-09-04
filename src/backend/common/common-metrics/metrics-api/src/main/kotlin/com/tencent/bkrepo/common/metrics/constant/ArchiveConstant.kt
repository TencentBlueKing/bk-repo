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

package com.tencent.bkrepo.common.metrics.constant

const val FILE_ARCHIVE_COUNTER = "file.archive.count"
const val FILE_ARCHIVE_COUNTER_DESC = "文件归档数量"
const val FILE_RESTORE_COUNTER = "file.restore.count"
const val FILE_RESTORE_COUNTER_DESC = "文件恢复数量"
const val FILE_ARCHIVE_SIZE_COUNTER = "file.archive.size.count"
const val FILE_ARCHIVE_SIZE_COUNTER_DESC = "文件归档大小"
const val FILE_RESTORE_SIZE_COUNTER = "file.restore.size.count"
const val FILE_RESTORE_SIZE_COUNTER_DESC = "文件恢复大小"
const val FILE_ARCHIVE_ACTIVE_COUNTER = "file.archive.active.count"
const val FILE_ARCHIVE_ACTIVE_COUNTER_DESC = "文件实时归档数量"
const val FILE_ARCHIVE_QUEUE_SIZE = "file.archive.queue.count"
const val FILE_ARCHIVE_QUEUE_SIZE_DESC = "文件归档队列大小"
const val FILE_ARCHIVE_TIME = "file.archive.time"
const val FILE_ARCHIVE_TIME_DESC = "文件归档耗时"
const val FILE_RESTORE_TIME = "file.restore.time"
const val FILE_RESTORE_TIME_DESC = "文件恢复耗时"
const val FILE_DOWNLOAD_ACTIVE_COUNT = "file.download.active.count"
const val FILE_DOWNLOAD_ACTIVE_COUNT_DESC = "文件下载实时数量"
const val FILE_DOWNLOAD_QUEUE_SIZE = "file.download.queue.size"
const val FILE_DOWNLOAD_QUEUE_SIZE_DESC = "文件下载队列大小"
const val FILE_COMPRESS_ACTIVE_COUNT = "file.compress.active.count"
const val FILE_COMPRESS_ACTIVE_COUNT_DESC = "文件压缩实时数量"
const val FILE_COMPRESS_QUEUE_SIZE = "file.compress.queue.size"
const val FILE_COMPRESS_QUEUE_SIZE_DESC = "文件压缩队列大小"
const val ARCHIVE_FILE_STATUS_COUNTER = "file.archive.status.count"
const val ARCHIVE_FILE_STATUS_COUNTER_DESC = "归档文件状态统计"
const val COMPRESS_FILE_STATUS_COUNTER = "file.compress.status.count"
const val COMPRESS_FILE_STATUS_COUNTER_DESC = "压缩文件状态统计"
const val FILE_COMPRESS_COUNTER = "file.compress.count"
const val FILE_COMPRESS_COUNTER_DESC = "文件压缩数量"
const val FILE_COMPRESS_SIZE_COUNTER = "file.compress.size.count"
const val FILE_COMPRESS_SIZE_COUNTER_DESC = "文件压缩大小"
const val FILE_COMPRESS_TIME = "file.compress.time"
const val FILE_COMPRESS_TIME_DESC = "文件压缩耗时"
const val FILE_UNCOMPRESS_COUNTER = "file.uncompress.count"
const val FILE_UNCOMPRESS_COUNTER_DESC = "文件压缩数量"
const val FILE_UNCOMPRESS_SIZE_COUNTER = "file.uncompress.size.count"
const val FILE_UNCOMPRESS_SIZE_COUNTER_DESC = "文件解压大小"
const val FILE_UNCOMPRESS_TIME = "file.uncompress.time"
const val FILE_UNCOMPRESS_TIME_DESC = "文件解压耗时"
const val STORAGE_FREE_SIZE_COUNTER = "storage.free.size.count"
const val STORAGE_FREE_SIZE_COUNTER_DESC = "存储释放大小"
const val STORAGE_FREE_COUNTER = "storage.free.count"
const val STORAGE_FREE_COUNTER_DESC = "存储释放文件个数"
const val STORAGE_ALLOCATE_SIZE_COUNTER = "storage.allocate.size.count"
const val STORAGE_ALLOCATE_SIZE_COUNTER_DESC = "存储新增大小"
const val STORAGE_ALLOCATE_COUNTER = "storage.allocate.count"
const val STORAGE_ALLOCATE_COUNTER_DESC = "存储新增文件个数"
const val FILE_ARCHIVE_SIZE_TOTAL_COUNTER = "file.archive.size.total.count"
const val FILE_ARCHIVE_SIZE_TOTAL_COUNTER_DESC = "文件归档总大小"
const val FILE_COMPRESS_SIZE_TOTAL_COUNTER = "file.compress.size.total.count"
const val FILE_COMPRESS_SIZE_TOTAL_COUNTER_DESC = "文件压缩总大小"