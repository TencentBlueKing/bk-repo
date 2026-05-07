package com.tencent.bkrepo.common.ratelimiter.rule

import com.tencent.bkrepo.common.ratelimiter.enums.Algorithms
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.enums.WorkScope
import com.tencent.bkrepo.common.ratelimiter.rule.bandwidth.BandwidthResourceLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * 直接测试 PathResourceLimitRule.findResourceLimit 的优先级逻辑。
 *
 * 覆盖场景：
 *  - pathLengthCheck=false（URL前缀匹配）：高 priority 浅层规则 vs 低 priority 深层规则
 *  - pathLengthCheck=false：同 priority 时更具体（更深）规则胜出
 *  - pathLengthCheck=true（project/repo 精确匹配）：浅层高 priority 规则被深度过滤后，深层规则兜底
 *  - 无匹配规则时返回 null
 */
class PathResourceLimitRuleTest {

    // ─── pathLengthCheck=false (URL 前缀匹配) ────────────────────────────────────

    /**
     * 规则 A: /blueking           limit=100 priority=10  (浅层, 高优先级)
     * 规则 B: /blueking/generic   limit=999 priority=0   (深层, 低优先级)
     * 请求: /blueking/generic/file.txt
     * 期望: 规则 A 胜出 (priority 10 > 0)
     */
    @Test
    fun `pathLengthCheckFalse - high priority shallow rule wins over low priority deep rule`() {
        val rule = prefixRule()
        rule.addRateLimitRules(
            listOf(
                rl("/blueking", limit = 100L, priority = 10),
                rl("/blueking/generic", limit = 999L, priority = 0),
            )
        )

        val result = rule.getRateLimitRule(ResInfo("/blueking/generic/file.txt", emptyList()))
        assertNotNull(result)
        assertEquals(100L, result!!.resourceLimit.limit, "priority=10 的浅层规则应胜出")
        assertEquals(10, result.resourceLimit.priority)
    }

    /**
     * 规则 A: /blueking           limit=100 priority=0   (浅层)
     * 规则 B: /blueking/generic   limit=999 priority=0   (深层, 同优先级)
     * 请求: /blueking/generic/file.txt
     * 期望: 规则 B 胜出 (同 priority 时更具体/更深的路径胜出)
     */
    @Test
    fun `pathLengthCheckFalse - deeper rule wins when priorities are equal`() {
        val rule = prefixRule()
        rule.addRateLimitRules(
            listOf(
                rl("/blueking", limit = 100L, priority = 0),
                rl("/blueking/generic", limit = 999L, priority = 0),
            )
        )

        val result = rule.getRateLimitRule(ResInfo("/blueking/generic/file.txt", emptyList()))
        assertNotNull(result)
        assertEquals(999L, result!!.resourceLimit.limit, "同 priority 时深层规则应胜出")
    }

    /**
     * 三层嵌套规则，中间层 priority 最高。
     * 规则 A: /a           priority=0
     * 规则 B: /a/b         priority=20  (中层, 最高优先级)
     * 规则 C: /a/b/c       priority=5
     * 请求: /a/b/c/d
     * 期望: 规则 B 胜出 (priority 20 最高)
     */
    @Test
    fun `pathLengthCheckFalse - middle layer with highest priority wins`() {
        val rule = prefixRule()
        rule.addRateLimitRules(
            listOf(
                rl("/a", limit = 1L, priority = 0),
                rl("/a/b", limit = 2L, priority = 20),
                rl("/a/b/c", limit = 3L, priority = 5),
            )
        )

        val result = rule.getRateLimitRule(ResInfo("/a/b/c/d", emptyList()))
        assertNotNull(result)
        assertEquals(2L, result!!.resourceLimit.limit, "中间层 priority=20 应胜出")
    }

    /**
     * 只有一条规则时，无论 priority 值，直接返回该规则。
     */
    @Test
    fun `pathLengthCheckFalse - single rule is always returned`() {
        val rule = prefixRule()
        rule.addRateLimitRules(listOf(rl("/blueking", limit = 42L, priority = 0)))

        val result = rule.getRateLimitRule(ResInfo("/blueking/generic", emptyList()))
        assertNotNull(result)
        assertEquals(42L, result!!.resourceLimit.limit)
    }

    /**
     * 路径不匹配时返回 null（前缀都不存在）。
     */
    @Test
    fun `pathLengthCheckFalse - no matching rule returns null`() {
        val rule = prefixRule()
        rule.addRateLimitRules(listOf(rl("/other", limit = 10L, priority = 0)))

        val result = rule.getRateLimitRule(ResInfo("/blueking/generic", emptyList()))
        assertNull(result)
    }

    // ─── pathLengthCheck=true (project/repo 精确深度匹配) ────────────────────────

    /**
     * P0 Bug 验证（已修复）：
     * pathLengthCheck=true 下，浅层高 priority 规则 (depth=1) 不应导致深层精确匹配规则 (depth=2) 失效。
     *
     * 规则 A: /blueking           depth=1  priority=10  (高优先级但深度不匹配)
     * 规则 B: /blueking/generic   depth=2  priority=0   (深度精确匹配)
     * 请求资源: /blueking/generic  (depth=2)
     * 修复前：A 被 priority 选中 → pathLengthCheck 拒绝 → 返回 null (错误)
     * 修复后：先按深度过滤 → B 唯一候选 → 正确返回 B
     */
    @Test
    fun `pathLengthCheckTrue - shallow high-priority rule filtered out, deep rule correctly returned`() {
        val rule = exactRule()
        rule.addRateLimitRules(
            listOf(
                rl("/blueking", limit = 100L, priority = 10),
                rl("/blueking/generic", limit = 999L, priority = 0),
            )
        )

        // 请求深度=2 → 规则 A (depth=1) 被过滤，规则 B (depth=2) 返回
        val result = rule.getRateLimitRule(ResInfo("/blueking/generic", emptyList()))
        assertNotNull(result, "修复后 pathLengthCheck=true 不应因浅层高 priority 规则而返回 null")
        assertEquals(999L, result!!.resourceLimit.limit, "depth=2 的规则 B 应被返回")
    }

    /**
     * pathLengthCheck=true 下，请求深度=1，只有 depth=1 规则才能匹配。
     * 不受深层规则干扰。
     */
    @Test
    fun `pathLengthCheckTrue - only depth-matching rule is returned`() {
        val rule = exactRule()
        rule.addRateLimitRules(
            listOf(
                rl("/blueking", limit = 100L, priority = 0),
                rl("/blueking/generic", limit = 999L, priority = 10),
            )
        )

        // 请求深度=1 → 只有 depth=1 规则匹配
        val shallow = rule.getRateLimitRule(ResInfo("/blueking", emptyList()))
        assertNotNull(shallow)
        assertEquals(100L, shallow!!.resourceLimit.limit, "depth=1 请求应只匹配 depth=1 规则")

        // 请求深度=2 → 只有 depth=2 规则匹配
        val deep = rule.getRateLimitRule(ResInfo("/blueking/generic", emptyList()))
        assertNotNull(deep)
        assertEquals(999L, deep!!.resourceLimit.limit, "depth=2 请求应只匹配 depth=2 规则")
    }

    /**
     * pathLengthCheck=true 下，没有精确深度匹配时返回 null。
     * 验证 pathLengthCheck 过滤逻辑不会把不匹配的规则强行返回。
     */
    @Test
    fun `pathLengthCheckTrue - returns null when no depth-matching rule exists`() {
        val rule = exactRule()
        rule.addRateLimitRules(listOf(rl("/blueking", limit = 100L, priority = 10)))

        // 请求深度=2，规则 depth=1 → 过滤后为空 → null
        val result = rule.getRateLimitRule(ResInfo("/blueking/generic", emptyList()))
        assertNull(result, "无深度匹配规则时应返回 null")
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    /** pathLengthCheck=false：URL前缀匹配模式 */
    private fun prefixRule(): BandwidthResourceLimitRule =
        BandwidthResourceLimitRule(pathLengthCheck = false)

    /** pathLengthCheck=true：精确深度匹配模式（默认）*/
    private fun exactRule(): BandwidthResourceLimitRule =
        BandwidthResourceLimitRule(pathLengthCheck = true)

    private fun rl(path: String, limit: Long, priority: Int) = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name,
        resource = path,
        limitDimension = LimitDimension.DOWNLOAD_BANDWIDTH.name,
        limit = limit,
        duration = Duration.ofSeconds(1),
        scope = WorkScope.LOCAL.name,
        priority = priority,
    )
}
