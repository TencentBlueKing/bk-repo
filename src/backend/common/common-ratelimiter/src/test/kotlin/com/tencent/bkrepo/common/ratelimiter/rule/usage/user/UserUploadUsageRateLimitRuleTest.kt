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

package com.tencent.bkrepo.common.ratelimiter.rule.usage.user

import com.tencent.bkrepo.common.ratelimiter.enums.Algorithms
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.enums.WorkScope
import com.tencent.bkrepo.common.ratelimiter.exception.InvalidResourceException
import com.tencent.bkrepo.common.ratelimiter.rule.BaseRuleTest
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

class UserUploadUsageRateLimitRuleTest : BaseRuleTest() {

    private val l1 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name, resource = "*:/",
        limitDimension = LimitDimension.USER_UPLOAD_USAGE.name, limit = 52428800,
        duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
    )
    private val l2 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name, resource = "user1:/project1/",
        limitDimension = LimitDimension.USER_UPLOAD_USAGE.name, limit = 52428800,
        duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
    )
    private val l3 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name, resource = "user1:/project1/repo1/",
        limitDimension = LimitDimension.USER_UPLOAD_USAGE.name, limit = 52428800,
        duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
    )
    private val l4 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name, resource = "user1:/project2/*/",
        limitDimension = LimitDimension.USER_UPLOAD_USAGE.name, limit = 52428800,
        duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
    )
    private val l5 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name, resource = "user1:/*/*/",
        limitDimension = LimitDimension.USER_UPLOAD_USAGE.name, limit = 52428800,
        duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
    )
    private val l6 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name, resource = "user1:/*/",
        limitDimension = LimitDimension.USER_UPLOAD_USAGE.name, limit = 52428800,
        duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
    )
    private val l7 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name, resource = "user1:/project3/repo3/",
        limitDimension = LimitDimension.USER_UPLOAD_USAGE.name, limit = 52428800,
        duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
    )
    private val l8 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name, resource = "user1:/project3/{(^[a-zA-Z]*\$)}/",
        limitDimension = LimitDimension.USER_UPLOAD_USAGE.name, limit = 52428800,
        duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
    )
    private val l9 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name, resource = "user1:/project3/{(^[0-9]*\$)}/",
        limitDimension = LimitDimension.USER_UPLOAD_USAGE.name, limit = 52428800,
        duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
    )
    private val l10 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name, resource = "user1:/project3/{repo}}/",
        limitDimension = LimitDimension.USER_UPLOAD_USAGE.name, limit = 52428800,
        duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
    )
    private val l11 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name, resource = "user1:/project3/",
        limitDimension = LimitDimension.USER_UPLOAD_USAGE.name, limit = 52428800,
        duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
    )
    private val l12 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name, resource = "user1:/project1/repo1/",
        limitDimension = LimitDimension.USER_UPLOAD_USAGE.name, limit = 52428800,
        duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
    )

    private val l13 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name, resource = "*:",
        limitDimension = LimitDimension.USER_UPLOAD_USAGE.name, limit = 52428800,
        duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
    )

    private val l14 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name, resource = "user1:",
        limitDimension = LimitDimension.USER_UPLOAD_USAGE.name, limit = 52428800,
        duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
    )

    @Test
    fun testIsEmpty() {
        val userUploadUsageRateLimitRule = UserUploadUsageRateLimitRule()
        assertEquals(userUploadUsageRateLimitRule.isEmpty(), true)
        userUploadUsageRateLimitRule.addRateLimitRule(l1)
        assertEquals(userUploadUsageRateLimitRule.isEmpty(), false)
    }

    @Test
    fun testUserUploadUsageRateLimitRuleAndGetRateLimitRule() {
        val userUploadUsageRateLimitRule = UserUploadUsageRateLimitRule()
        userUploadUsageRateLimitRule.addRateLimitRule(l13)
        var resInfo = ResInfo("user1:/project3/repo2/", listOf("user1:/project3/", "user1:"))
        var actualInfo = userUploadUsageRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l13)
        Assertions.assertEquals(actualInfo?.resource, "user1:")

        userUploadUsageRateLimitRule.addRateLimitRule(l14)

        resInfo = ResInfo("user1:/project3/repo2/", listOf("user1:/project3/", "user1:"))
        actualInfo = userUploadUsageRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l14)
        Assertions.assertEquals(actualInfo?.resource, "user1:")


        userUploadUsageRateLimitRule.addRateLimitRule(l1)

        resInfo = ResInfo("user1:///", listOf("user1:"))
        actualInfo = userUploadUsageRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l14)
        Assertions.assertEquals(actualInfo?.resource, "user1:")


        resInfo = ResInfo("user1:/", listOf("user1:"))
        actualInfo = userUploadUsageRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l1)
        Assertions.assertEquals(actualInfo?.resource, "user1:/")



        userUploadUsageRateLimitRule.addRateLimitRule(l2)
        userUploadUsageRateLimitRule.addRateLimitRule(l3)
        userUploadUsageRateLimitRule.addRateLimitRule(l4)

        resInfo = ResInfo("user1:/project1/", listOf("user1:"))
        actualInfo = userUploadUsageRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l2)
        Assertions.assertEquals(actualInfo?.resource, "user1:/project1/")

        resInfo = ResInfo("user1:/project2/", listOf("user1:"))
        actualInfo = userUploadUsageRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l14)
        Assertions.assertEquals(actualInfo?.resource, "user1:")

        userUploadUsageRateLimitRule.addRateLimitRule(l11)

        resInfo = ResInfo("user1:/project3/", listOf("user1:"))
        actualInfo = userUploadUsageRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l11)
        Assertions.assertEquals(actualInfo?.resource, "user1:/project3/")

        resInfo = ResInfo("user1:/project1/repo1/", listOf("user1:/project1/", "user1:"))
        actualInfo = userUploadUsageRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l3)
        Assertions.assertEquals(actualInfo?.resource, "user1:/project1/repo1/")

        resInfo = ResInfo("user1:/project3/repo3/", listOf("user1:/project3/", "user1:"))
        actualInfo = userUploadUsageRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l11)
        Assertions.assertEquals(actualInfo?.resource, "user1:/project3/")

        userUploadUsageRateLimitRule.addRateLimitRule(l6)

        resInfo = ResInfo("user1:/project4/repo4/", listOf("user1:/project4/", "user1:"))
        actualInfo = userUploadUsageRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l6)
        Assertions.assertEquals(actualInfo?.resource, "user1:/project4/")

        userUploadUsageRateLimitRule.addRateLimitRule(l5)

        resInfo = ResInfo("user1:/project4/repo4/", listOf("user1:/project4/", "user1:"))
        actualInfo = userUploadUsageRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l5)
        Assertions.assertEquals(actualInfo?.resource, "user1:/project4/repo4/")

        resInfo = ResInfo("user1:/project1/repo1/", listOf("user1:/project1/", "user1:"))
        actualInfo = userUploadUsageRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l3)
        Assertions.assertEquals(actualInfo?.resource, "user1:/project1/repo1/")

        resInfo = ResInfo("user1:/project1/repo2/", listOf("user1:/project1/", "user1:"))
        actualInfo = userUploadUsageRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l2)
        Assertions.assertEquals(actualInfo?.resource, "user1:/project1/")

        resInfo = ResInfo("user1:/project2/repo2/", listOf("user1:/project2/", "user1:"))
        actualInfo = userUploadUsageRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l4)
        Assertions.assertEquals(actualInfo?.resource, "user1:/project2/repo2/")

        resInfo = ResInfo("user1:/project3/repo1/", listOf("user1:/project3/", "user1:"))
        actualInfo = userUploadUsageRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l11)
        Assertions.assertEquals(actualInfo?.resource, "user1:/project3/")

        resInfo = ResInfo("user1:/project4/", listOf("user1:"))
        actualInfo = userUploadUsageRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l6)
        Assertions.assertEquals(actualInfo?.resource, "user1:/project4/")

        resInfo = ResInfo("user1:/project4/repo4/", listOf("user1:/project4/", "user1:"))
        actualInfo = userUploadUsageRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l5)
        Assertions.assertEquals(actualInfo?.resource, "user1:/project4/repo4/")

        userUploadUsageRateLimitRule.addRateLimitRule(l7)
        userUploadUsageRateLimitRule.addRateLimitRule(l8)
        userUploadUsageRateLimitRule.addRateLimitRule(l9)

        resInfo = ResInfo("user1:/project3/repo3/", listOf("user1:/project3/", "user1:"))
        actualInfo = userUploadUsageRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l7)
        Assertions.assertEquals(actualInfo?.resource, "user1:/project3/repo3/")

        resInfo = ResInfo("user1:/project3/repo4/", listOf("user1:/project3/", "user1:"))
        actualInfo = userUploadUsageRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l11)
        Assertions.assertEquals(actualInfo?.resource, "user1:/project3/")

        resInfo = ResInfo("user1:/project3/xxxx/", listOf("user1:/project3/", "user1:"))
        actualInfo = userUploadUsageRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l8)
        Assertions.assertEquals(actualInfo?.resource, "user1:/project3/xxxx/")

        resInfo = ResInfo("user1:/project3/1234/", listOf("user1:/project3/", "user1:"))
        actualInfo = userUploadUsageRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l9)
        Assertions.assertEquals(actualInfo?.resource, "user1:/project3/1234/")

        userUploadUsageRateLimitRule.addRateLimitRule(l10)
        resInfo = ResInfo("user1:/project3/xxxx/", listOf("user1:/project3/", "user1:"))
        actualInfo = userUploadUsageRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l10)
        Assertions.assertEquals(actualInfo?.resource, "user1:/project3/xxxx/")


        resInfo = ResInfo("user2:/", listOf("user2:"))
        actualInfo = userUploadUsageRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l1)
        Assertions.assertEquals(actualInfo?.resource, "user2:/")

        resInfo = ResInfo("user2:/project3/xxxx/", listOf("user2:/project3/", "user2:"))
        actualInfo = userUploadUsageRateLimitRule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l13)
        Assertions.assertEquals(actualInfo?.resource, "user2:")

    }

    @Test
    fun testUserUploadUsageRateLimitRuleAndGetRateLimitRuleWithEmptyRule() {
        val rule = UserUploadUsageRateLimitRule()
        var resInfo = ResInfo("user1:/project1/repo1/", listOf("user1:/project1/", "user1:"))
        var info = rule.getRateLimitRule(resInfo)
        Assertions.assertNull(info)
        resInfo = ResInfo("user1:/", listOf("user1:"))
        info = rule.getRateLimitRule(resInfo)
        Assertions.assertNull(info)
    }

    @Test
    fun testUserUploadUsageRateLimitRuleAndGetRateLimitRuleWithResEmpty() {
        val rule = UserUploadUsageRateLimitRule()
        rule.addRateLimitRule(l1)
        var resInfo = ResInfo("", listOf(""))
        assertThrows<InvalidResourceException> { rule.getRateLimitRule(resInfo) }
    }

    @Test
    fun testUserUploadUsageRateLimitRuleAndGetRateLimitRuleWithDifferentOrder() {
        val rule = UserUploadUsageRateLimitRule()
        rule.addRateLimitRule(l5)

        var resInfo = ResInfo("user1:/project1/repo1/", listOf("user1:/project1/", "user1:"))
        var actualInfo = rule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l5)

        rule.addRateLimitRule(l3)

        actualInfo = rule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l3)
    }

    @Test
    fun testUserUploadUsageRateLimitRuleAndGetRateLimitRuleWithDuplicatedLimitInfos() {
        val rule = UserUploadUsageRateLimitRule()
        rule.addRateLimitRule(l3)
        rule.addRateLimitRule(l12)
        var resInfo = ResInfo("user1:/project1/repo1/", listOf("user1:/project1/", "user1:"))
        val actualInfo = rule.getRateLimitRule(resInfo)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l3)
        assertEqualsLimitInfo(actualInfo?.resourceLimit, l12)
    }

    @Test
    fun testUserUploadUsageRateLimitRuleWithInvalidLimitInfo() {
        val rule = UserUploadUsageRateLimitRule()
        val rl = ResourceLimit(
            algo = Algorithms.FIXED_WINDOW.name, resource = "/2/",
            limitDimension = LimitDimension.URL.name, limit = 52428800,
            duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
        )
        assertThrows<InvalidResourceException> { rule.addRateLimitRule(rl) }
    }

    @Test
    fun testUserUploadUsageRateLimitRuleWithInvalidUrl() {
        val rule = UserUploadUsageRateLimitRule()
        rule.addRateLimitRule(l1)
        rule.addRateLimitRule(l2)
        val resInfo = ResInfo("invalid url")
        assertThrows<InvalidResourceException> { rule.getRateLimitRule(resInfo) }
    }
}