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

class UrlPrefixUploadRateLimitRuleTest : BaseRuleTest() {

    private val repoRule = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name,
        resource = "/generic/proj/repo/",
        limitDimension = LimitDimension.URL_PREFIX_UPLOAD_RATE.name,
        limit = 100,
        duration = Duration.ofSeconds(1),
        scope = WorkScope.LOCAL.name
    )
    private val searchRule = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name,
        resource = "/generic/proj/repo/search/",
        limitDimension = LimitDimension.URL_PREFIX_UPLOAD_RATE.name,
        limit = 10,
        duration = Duration.ofSeconds(1),
        scope = WorkScope.LOCAL.name
    )
    private val projectRule = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name,
        resource = "/generic/proj/",
        limitDimension = LimitDimension.URL_PREFIX_UPLOAD_RATE.name,
        limit = 200,
        duration = Duration.ofSeconds(1),
        scope = WorkScope.LOCAL.name
    )

    @Test
    fun `should be empty before adding rules`() {
        val rule = UrlPrefixUploadRateLimitRule()
        assertEquals(true, rule.isEmpty())
        rule.addRateLimitRule(repoRule)
        assertEquals(false, rule.isEmpty())
    }

    @Test
    fun `should match exact path`() {
        val rule = UrlPrefixUploadRateLimitRule()
        rule.addRateLimitRule(repoRule)
        val info = rule.getRateLimitRule(ResInfo("/generic/proj/repo/"))
        assertEqualsLimitInfo(info?.resourceLimit, repoRule)
        assertEquals("/generic/proj/repo/", info?.resource)
    }

    @Test
    fun `should prefix-match sub-path under configured rule`() {
        val rule = UrlPrefixUploadRateLimitRule()
        rule.addRateLimitRule(repoRule)

        val info1 = rule.getRateLimitRule(ResInfo("/generic/proj/repo/upload"))
        assertEqualsLimitInfo(info1?.resourceLimit, repoRule)

        val info2 = rule.getRateLimitRule(ResInfo("/generic/proj/repo/subdir/file.zip"))
        assertEqualsLimitInfo(info2?.resourceLimit, repoRule)
    }

    @Test
    fun `should return null for path outside configured prefix`() {
        val rule = UrlPrefixUploadRateLimitRule()
        rule.addRateLimitRule(repoRule)

        assertNull(rule.getRateLimitRule(ResInfo("/generic/other-proj/repo/")))
        assertNull(rule.getRateLimitRule(ResInfo("/api/node/batch")))
    }

    @Test
    fun `more specific rule should win over parent prefix`() {
        val rule = UrlPrefixUploadRateLimitRule()
        rule.addRateLimitRule(repoRule)
        rule.addRateLimitRule(searchRule)

        val info = rule.getRateLimitRule(ResInfo("/generic/proj/repo/search/query"))
        assertEqualsLimitInfo(info?.resourceLimit, searchRule)

        val fallback = rule.getRateLimitRule(ResInfo("/generic/proj/repo/upload"))
        assertEqualsLimitInfo(fallback?.resourceLimit, repoRule)
    }

    @Test
    fun `parent rule matches when no child rule exists`() {
        val rule = UrlPrefixUploadRateLimitRule()
        rule.addRateLimitRule(projectRule)
        rule.addRateLimitRule(repoRule)

        val info = rule.getRateLimitRule(ResInfo("/generic/proj/other-repo/file"))
        assertEqualsLimitInfo(info?.resourceLimit, projectRule)
    }

    @Test
    fun `each URL has its own ResLimitInfo resource for independent counters`() {
        val rule = UrlPrefixUploadRateLimitRule()
        rule.addRateLimitRule(repoRule)

        val info1 = rule.getRateLimitRule(ResInfo("/generic/proj/repo/a.zip"))
        val info2 = rule.getRateLimitRule(ResInfo("/generic/proj/repo/b.zip"))

        assertNotNull(info1)
        assertNotNull(info2)
        assertEquals("/generic/proj/repo/a.zip", info1?.resource)
        assertEquals("/generic/proj/repo/b.zip", info2?.resource)
        assertEqualsLimitInfo(info1?.resourceLimit, repoRule)
        assertEqualsLimitInfo(info2?.resourceLimit, repoRule)
    }

    @Test
    fun `should return null for blank resource`() {
        val rule = UrlPrefixUploadRateLimitRule()
        rule.addRateLimitRule(repoRule)
        assertNull(rule.getRateLimitRule(ResInfo("")))
    }

    @Test
    fun `should return null when no rules configured`() {
        val rule = UrlPrefixUploadRateLimitRule()
        assertNull(rule.getRateLimitRule(ResInfo("/generic/proj/repo/")))
    }

    @Test
    fun `should throw on invalid dimension`() {
        val rule = UrlPrefixUploadRateLimitRule()
        val invalidRule = ResourceLimit(
            algo = Algorithms.FIXED_WINDOW.name,
            resource = "/generic/proj/repo/",
            limitDimension = LimitDimension.URL_PREFIX_DOWNLOAD_RATE.name,
            limit = 10,
            duration = Duration.ofSeconds(1),
            scope = WorkScope.LOCAL.name
        )
        assertThrows<InvalidResourceException> { rule.addRateLimitRule(invalidRule) }
    }

    @Test
    fun `malformed URI with consecutive slashes still matches via tokenizeResourcePath normalization`() {
        val rule = UrlPrefixUploadRateLimitRule()
        rule.addRateLimitRule(repoRule)
        val info = rule.getRateLimitRule(ResInfo("///generic//proj//repo//file.zip"))
        assertNotNull(info)
        assertEqualsLimitInfo(info?.resourceLimit, repoRule)
    }

    @Test
    fun `malformed URI with URL-encoded slashes still matches via tokenizeResourcePath normalization`() {
        val rule = UrlPrefixUploadRateLimitRule()
        rule.addRateLimitRule(repoRule)
        val info = rule.getRateLimitRule(ResInfo("/generic/proj/repo%2Ffile.zip"))
        assertNotNull(info)
        assertEqualsLimitInfo(info?.resourceLimit, repoRule)
    }
}
