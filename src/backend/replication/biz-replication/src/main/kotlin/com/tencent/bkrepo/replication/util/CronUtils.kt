/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.replication.util

import com.tencent.bkrepo.replication.constant.DEFAULT_GROUP_ID
import org.quartz.CronExpression
import org.quartz.CronScheduleBuilder
import org.quartz.TriggerBuilder
import java.text.SimpleDateFormat
import java.util.Date

object CronUtils {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    /**
     * 判断表达式是否有效
     */
    fun isValid(cronExpression: String): Boolean {
        return CronExpression.isValidExpression(cronExpression)
    }

    // 上次执行时间
    fun getLastTriggerTime(id: String, cron: String): String? {
        if (!CronExpression.isValidExpression(cron)) {
            return null
        }
        val trigger = TriggerBuilder.newTrigger().withIdentity(id, DEFAULT_GROUP_ID)
            .withSchedule(CronScheduleBuilder.cronSchedule(cron)).build()
        val time0: Date = trigger.startTime
        val time1: Date = trigger.getFireTimeAfter(time0)
        val time2: Date = trigger.getFireTimeAfter(time1)
        val time3: Date = trigger.getFireTimeAfter(time2)
        val l = time1.time - (time3.time - time2.time)
        return dateFormat.format(Date(l))
    }

    // 获取下次执行时间
    fun getNextTriggerTime(id: String, cron: String): String? {
        if (!CronExpression.isValidExpression(cron)) {
            return null
        }
        val trigger = TriggerBuilder.newTrigger().withIdentity(id, DEFAULT_GROUP_ID)
            .withSchedule(CronScheduleBuilder.cronSchedule(cron)).build()
        val time0 = trigger.startTime
        val time1 = trigger.getFireTimeAfter(time0)
        return dateFormat.format(time1.time)
    }
}
