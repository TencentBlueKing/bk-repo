package com.tencent.bkrepo.common.metadata.util

import com.tencent.bkrepo.common.metadata.model.TSeparationNode
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 降冷冷表查询构造：覆盖归档恢复路径、时间窗、前缀与 archived 标记。
 */
@DisplayName("SeparationQueryHelper 降冷查询")
class SeparationQueryHelperTest {

    private val separationDate = LocalDateTime.of(2024, 6, 15, 14, 30, 0)

    @Nested
    @DisplayName("pathQuery / archivedPathQuery")
    inner class PathQueryTests {

        @Test
        @DisplayName("archivedPathQuery 含 archived 为 true")
        fun archivedPathQueryRequiresArchivedTrue() {
            val q = SeparationQueryHelper.archivedPathQuery(
                "proj", "repo", separationDate, path = "/data/"
            )
            assertTrue(
                containsArchivedTrue(q.queryObject),
                "archivedPathQuery 应约束 archived == true"
            )
        }

        @Test
        @DisplayName("pathQuery archivedOnly false 不含 archived 约束")
        fun pathQueryWithoutArchivedFlagDoesNotAddArchivedCriteria() {
            val q = SeparationQueryHelper.pathQuery(
                "proj", "repo", separationDate,
                path = "/x/", archivedOnly = false
            )
            assertTrue(
                !containsArchivedField(q.queryObject),
                "普通 pathQuery 不应带 archived 条件"
            )
        }

        @Test
        @DisplayName("separationDate 落在当日开始到次日开始的左闭右开区间")
        fun separationDateUsesStartOfDayUntilNextDay() {
            val q = SeparationQueryHelper.pathQuery("p", "r", separationDate, archivedOnly = false)
            val range = findSeparationDateRange(q.queryObject)
            assertNotNull(range)
            val expectedStart = LocalDateTime.of(separationDate.toLocalDate(), LocalTime.MIN)
            val expectedEnd = expectedStart.plusDays(1)
            assertEquals(expectedStart, range!!.first)
            assertEquals(expectedEnd, range.second)
        }
    }

    private fun containsArchivedTrue(obj: Any?): Boolean {
        when (obj) {
            is Document -> {
                if (obj[TSeparationNode::archived.name] == true) return true
                return obj.values.any { containsArchivedTrue(it) }
            }
            is List<*> -> return obj.any { containsArchivedTrue(it) }
            else -> return false
        }
    }

    private fun containsArchivedField(obj: Any?): Boolean {
        when (obj) {
            is Document -> {
                if (obj.containsKey(TSeparationNode::archived.name)) return true
                return obj.values.any { containsArchivedField(it) }
            }
            is List<*> -> return obj.any { containsArchivedField(it) }
            else -> return false
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun findSeparationDateRange(obj: Any?): Pair<LocalDateTime, LocalDateTime>? {
        when (obj) {
            is Document -> {
                val sd = obj[TSeparationNode::separationDate.name]
                if (sd is Document) {
                    val gte = sd["\$gte"]
                    val lt = sd["\$lt"]
                    if (gte is LocalDateTime && lt is LocalDateTime) {
                        return gte to lt
                    }
                }
                for (v in obj.values) {
                    val r = findSeparationDateRange(v)
                    if (r != null) return r
                }
            }
            is List<*> -> {
                for (e in obj) {
                    val r = findSeparationDateRange(e)
                    if (r != null) return r
                }
            }
        }
        return null
    }
}
