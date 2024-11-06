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

package com.tencent.bkrepo.common.ratelimiter.rule.bandwidth

import com.tencent.bkrepo.common.ratelimiter.enums.Algorithms
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.enums.WorkScope
import com.tencent.bkrepo.common.ratelimiter.exception.InvalidResourceException
import com.tencent.bkrepo.common.ratelimiter.rule.BaseRuleTest
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

class UploadBandwidthRateLimitRuleTest : BaseRuleTest() {

    private val l1 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name, resource = "/project3/",
        limitDimension = LimitDimension.UPLOAD_BANDWIDTH.name, limit = 52428800,
        duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
    )
    private val l2 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name, resource = "/project1/repo1/",
        limitDimension = LimitDimension.UPLOAD_BANDWIDTH.name, limit = 52428800,
        duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
    )
    private val l3 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name, resource = "/*/repo1/",
        limitDimension = LimitDimension.UPLOAD_BANDWIDTH.name, limit = 52428800,
        duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
    )
    private val l4 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name, resource = "/project1/*/",
        limitDimension = LimitDimension.UPLOAD_BANDWIDTH.name, limit = 52428800,
        duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
    )
    private val l5 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name, resource = "/*/*/",
        limitDimension = LimitDimension.UPLOAD_BANDWIDTH.name, limit = 52428800,
        duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
    )
    private val l6 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name, resource = "/*/",
        limitDimension = LimitDimension.UPLOAD_BANDWIDTH.name, limit = 52428800,
        duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
    )
    private val l7 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name, resource = "/project3/repo3/",
        limitDimension = LimitDimension.UPLOAD_BANDWIDTH.name, limit = 52428800,
        duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
    )

    @Test
    fun testIsEmpty() {
        val uploadBandwidthRateLimitRule = UploadBandwidthRateLimitRule()
        assertEquals(uploadBandwidthRateLimitRule.isEmpty(), true)
        uploadBandwidthRateLimitRule.addRateLimitRule(l1)
        assertEquals(uploadBandwidthRateLimitRule.isEmpty(), false)
    }

    @Test
    fun testBandwidthRateLimitRuleAndGetRateLimitRule() {
        val uploadBandwidthRateLimitRule = UploadBandwidthRateLimitRule()
        uploadBandwidthRateLimitRule.addRateLimitRule(l1)
        uploadBandwidthRateLimitRule.addRateLimitRule(l2)
        uploadBandwidthRateLimitRule.addRateLimitRule(l3)
        uploadBandwidthRateLimitRule.addRateLimitRule(l4)

        var resInfo = ResInfo("/project1/repo1/", listOf("/project1/"))
        var actualInfo = uploadBandwidthRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l2)
        assertEquals(actualInfo?.resource, "/project1/repo1/")

        resInfo = ResInfo("/project1/repo2/", listOf("/project1/"))
        actualInfo = uploadBandwidthRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l4)
        assertEquals(actualInfo?.resource, "/project1/repo2/")

        resInfo = ResInfo("/project2/repo2/", listOf("/project2/"))
        actualInfo = uploadBandwidthRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, null)
        assertEquals(actualInfo?.resource, null)

        resInfo = ResInfo("/project2/repo1/", listOf("/project2/"))
        actualInfo = uploadBandwidthRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l3)
        assertEquals(actualInfo?.resource, "/project2/repo1/")

        resInfo = ResInfo("/project3/repo3/", listOf("/project3/"))
        actualInfo = uploadBandwidthRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l1)
        assertEquals(actualInfo?.resource, "/project3/")

        uploadBandwidthRateLimitRule.addRateLimitRule(l6)

        resInfo = ResInfo("/project4/repo4/", listOf("/project4/"))
        actualInfo = uploadBandwidthRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l6)
        assertEquals(actualInfo?.resource, "/project4/")

        uploadBandwidthRateLimitRule.addRateLimitRule(l5)

        resInfo = ResInfo("/project4/repo4/", listOf("/project4/"))
        actualInfo = uploadBandwidthRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l5)
        assertEquals(actualInfo?.resource, "/project4/repo4/")

        resInfo = ResInfo("/project1/repo1/", listOf("/project1/"))
        actualInfo = uploadBandwidthRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l2)
        assertEquals(actualInfo?.resource, "/project1/repo1/")

        resInfo = ResInfo("/project1/repo2/", listOf("/project1/"))
        actualInfo = uploadBandwidthRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l4)
        assertEquals(actualInfo?.resource, "/project1/repo2/")

        resInfo = ResInfo("/project2/repo2/", listOf("/project2/"))
        actualInfo = uploadBandwidthRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l5)
        assertEquals(actualInfo?.resource, "/project2/repo2/")

        resInfo = ResInfo("/project2/repo1/", listOf("/project2/"))
        actualInfo = uploadBandwidthRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l3)
        assertEquals(actualInfo?.resource, "/project2/repo1/")

        resInfo = ResInfo("/project3/repo3/", listOf("/project3/"))
        actualInfo = uploadBandwidthRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l1)
        assertEquals(actualInfo?.resource, "/project3/")

        uploadBandwidthRateLimitRule.addRateLimitRule(l7)

        resInfo = ResInfo("/project3/repo3/", listOf("/project3/"))
        actualInfo = uploadBandwidthRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l7)
        assertEquals(actualInfo?.resource, "/project3/repo3/")
    }

    @Test
    fun testBandwidthRateLimitRuleAndGetRateLimitRuleWithEmptyRule() {
        val rule = UploadBandwidthRateLimitRule()
        var resInfo = ResInfo("/project1/repo1/", listOf("/project1/"))
        var info = rule.getRateLimitRule(resInfo)
        assertNull(info)
        resInfo = ResInfo("/", listOf())
        info = rule.getRateLimitRule(resInfo)
        assertNull(info)
    }

    @Test
    fun testBandwidthRateLimitRuleAndGetRateLimitRuleWithResEmpty() {
        val rule = UploadBandwidthRateLimitRule()
        rule.addRateLimitRule(l1)
        var resInfo = ResInfo("", listOf())
        var actualInfo = rule.getRateLimitRule(resInfo)
        assertNull(actualInfo?.resourceLimit)
        resInfo = ResInfo("//", listOf())
        actualInfo = rule.getRateLimitRule(resInfo)
        assertNull(actualInfo?.resourceLimit)
    }

    @Test
    fun testBandwidthRateLimitRuleAndGetRateLimitRuleWithDifferentOrder() {
        val rule = UploadBandwidthRateLimitRule()
        rule.addRateLimitRule(l3)
        rule.addRateLimitRule(l2)

        var resInfo = ResInfo("/project1/repo1/", listOf("/project1/"))
        var actualInfo = rule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l2)

        resInfo = ResInfo("/project2/repo1/", listOf("/project2/"))
        actualInfo = rule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l3)
    }

    @Test
    fun testBandwidthRateLimitRuleAndGetRateLimitRuleWithDuplicatedLimitInfos() {
        val rule = UploadBandwidthRateLimitRule()
        rule.addRateLimitRule(l3)
        rule.addRateLimitRule(l3)
        val resInfo = ResInfo("/project2/repo1/", listOf("/project2/"))
        val actualInfo = rule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l3)
    }

    @Test
    fun testBandwidthRateLimitRuleWithInvalidLimitInfo() {
        val rule = UploadBandwidthRateLimitRule()
        val rl = ResourceLimit(
            algo = Algorithms.FIXED_WINDOW.name, resource = "/2/",
            limitDimension = LimitDimension.URL.name, limit = 52428800,
            duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
        )
        assertThrows<InvalidResourceException> { rule.addRateLimitRule(rl) }
    }

    @Test
    fun testBandwidthRateLimitRuleWithInvalidUrl() {
        val rule = UploadBandwidthRateLimitRule()
        rule.addRateLimitRule(l1)
        rule.addRateLimitRule(l2)
        val resInfo = ResInfo("invalid url", listOf("invalid url"))
        assertThrows<InvalidResourceException> { rule.getRateLimitRule(resInfo) }
    }
}