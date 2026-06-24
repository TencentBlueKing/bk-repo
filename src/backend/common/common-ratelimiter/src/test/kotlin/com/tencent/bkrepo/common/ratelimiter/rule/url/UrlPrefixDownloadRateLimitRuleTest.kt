package com.tencent.bkrepo.common.ratelimiter.rule.url

import com.tencent.bkrepo.common.ratelimiter.enums.Algorithms
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.enums.WorkScope
import com.tencent.bkrepo.common.ratelimiter.exception.InvalidResourceException
import com.tencent.bkrepo.common.ratelimiter.rule.BaseRuleTest
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

class UrlPrefixDownloadRateLimitRuleTest : BaseRuleTest() {

    private val repoRule = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name,
        resource = "/generic/proj/repo/",
        limitDimension = LimitDimension.URL_PREFIX_DOWNLOAD_RATE.name,
        limit = 100,
        duration = Duration.ofSeconds(1),
        scope = WorkScope.LOCAL.name
    )
    private val searchRule = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name,
        resource = "/generic/proj/repo/search/",
        limitDimension = LimitDimension.URL_PREFIX_DOWNLOAD_RATE.name,
        limit = 5,
        duration = Duration.ofSeconds(1),
        scope = WorkScope.LOCAL.name
    )

    @Test
    fun `should be empty before adding rules`() {
        val rule = UrlPrefixDownloadRateLimitRule()
        assertEquals(true, rule.isEmpty())
        rule.addRateLimitRule(repoRule)
        assertEquals(false, rule.isEmpty())
    }

    @Test
    fun `should prefix-match sub-path under configured rule`() {
        val rule = UrlPrefixDownloadRateLimitRule()
        rule.addRateLimitRule(repoRule)

        val info = rule.getRateLimitRule(ResInfo("/generic/proj/repo/path/to/file.zip"))
        assertEqualsLimitInfo(info?.resourceLimit, repoRule)
        assertEquals("/generic/proj/repo/path/to/file.zip", info?.resource)
    }

    @Test
    fun `should return null for path outside configured prefix`() {
        val rule = UrlPrefixDownloadRateLimitRule()
        rule.addRateLimitRule(repoRule)

        assertNull(rule.getRateLimitRule(ResInfo("/generic/other-proj/other-repo/")))
    }

    @Test
    fun `more specific rule should win over parent`() {
        val rule = UrlPrefixDownloadRateLimitRule()
        rule.addRateLimitRule(repoRule)
        rule.addRateLimitRule(searchRule)

        val info = rule.getRateLimitRule(ResInfo("/generic/proj/repo/search/q"))
        assertEqualsLimitInfo(info?.resourceLimit, searchRule)

        val fallback = rule.getRateLimitRule(ResInfo("/generic/proj/repo/download/file.zip"))
        assertEqualsLimitInfo(fallback?.resourceLimit, repoRule)
    }

    @Test
    fun `should throw on invalid dimension`() {
        val rule = UrlPrefixDownloadRateLimitRule()
        val invalidRule = ResourceLimit(
            algo = Algorithms.FIXED_WINDOW.name,
            resource = "/generic/proj/repo/",
            limitDimension = LimitDimension.URL_PREFIX_UPLOAD_RATE.name,
            limit = 10,
            duration = Duration.ofSeconds(1),
            scope = WorkScope.LOCAL.name
        )
        assertThrows<InvalidResourceException> { rule.addRateLimitRule(invalidRule) }
    }

    @Test
    fun `malformed URI with consecutive slashes still matches`() {
        val rule = UrlPrefixDownloadRateLimitRule()
        rule.addRateLimitRule(repoRule)
        val info = rule.getRateLimitRule(ResInfo("///generic//proj//repo//file.zip"))
        assertNotNull(info)
    }

    @Test
    fun `malformed URI with URL-encoded slashes still matches`() {
        val rule = UrlPrefixDownloadRateLimitRule()
        rule.addRateLimitRule(repoRule)
        val info = rule.getRateLimitRule(ResInfo("/generic/proj/repo%2Ffile.zip"))
        assertNotNull(info)
    }
}
