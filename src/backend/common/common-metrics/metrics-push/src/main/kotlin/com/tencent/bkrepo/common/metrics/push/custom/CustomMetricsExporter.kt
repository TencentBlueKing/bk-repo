package com.tencent.bkrepo.common.metrics.push.custom

import com.tencent.bkrepo.common.metrics.push.custom.base.CustomEventItem
import com.tencent.bkrepo.common.metrics.push.custom.base.MetricsItem
import com.tencent.bkrepo.common.metrics.push.custom.base.PrometheusDrive
import com.tencent.bkrepo.common.metrics.push.custom.config.CustomEventConfig
import com.tencent.bkrepo.common.metrics.push.custom.config.CustomPushConfig
import com.tencent.bkrepo.common.metrics.push.custom.config.CustomReportConfig
import com.tencent.bkrepo.common.service.actuator.CommonTagProvider
import io.prometheus.client.CollectorRegistry
import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.autoconfigure.metrics.export.prometheus.PrometheusProperties
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.concurrent.ConcurrentHashMap

class CustomMetricsExporter(
    private val customPushConfig: CustomPushConfig,
    private val customReportConfig: CustomReportConfig,
    private val prometheusProperties: PrometheusProperties,
    private val scheduler: ThreadPoolTaskScheduler,
    private val customEventExporter: CustomEventExporter? = null,
    private val customEventConfig: CustomEventConfig? = null,
    drive: PrometheusDrive? = null,
    commonTagProvider: CommonTagProvider? = null,
) {

    /**
     * 来自 CommonTagProvider 的 base tags（service/instance/host），注入 event dimension，
     * 与 Micrometer MeterRegistry 侧的 commonTags 保持一致。
     */
    private val baseEventDimension: Map<String, String> = commonTagProvider?.provide().orEmpty()

    private val patternCache: ConcurrentHashMap<String, Regex> = ConcurrentHashMap()

    /** metrics push 路径：drive 为 null 时（仅开启 event）不创建，避免空指针 */
    private val scheduleMetricsExporter: ScheduleMetricsExporter? = drive?.let {
        ScheduleMetricsExporter(CollectorRegistry(), it, scheduler, prometheusProperties.pushgateway.pushRate)
    }

    fun reportMetrics(item: MetricsItem) {
        val enriched = enrichWithEnv(item)
        val mode = resolveReportMode(enriched)
        if (mode == ReportMode.METRIC_ONLY || mode == ReportMode.BOTH) {
            reportAsMetric(enriched)
        }
        if (mode == ReportMode.EVENT_ONLY || mode == ReportMode.BOTH) {
            reportAsEvent(enriched)
        }
    }

    /**
     * 若配置了 env，则将其注入到 labels 副本中，保证 metrics label 和 event dimension 均携带环境维度。
     */
    private fun enrichWithEnv(item: MetricsItem): MetricsItem {
        val env = customReportConfig.env
        if (env.isEmpty()) return item
        val enrichedLabels = HashMap<String, String>(item.labels.size + 1).apply {
            putAll(item.labels)
            put(ENV_LABEL_KEY, env)
        }
        return item.copy(labels = enrichedLabels)
    }

    private enum class ReportMode { METRIC_ONLY, EVENT_ONLY, BOTH }

    private fun resolveReportMode(item: MetricsItem): ReportMode {
        val config = customEventConfig ?: return ReportMode.METRIC_ONLY
        if (customEventExporter == null || !config.reportMetricsAsEvent) return ReportMode.METRIC_ONLY
        if (matchesIncludes(item.name, config.metricAndEventIncludes)) return ReportMode.BOTH
        if (matchesIncludes(item.name, config.metricEventIncludes)) return ReportMode.EVENT_ONLY
        return ReportMode.METRIC_ONLY
    }

    private fun reportAsMetric(item: MetricsItem) {
        if (!customPushConfig.enabled) return
        scheduleMetricsExporter?.queue?.offer(pruneLabelsForMetric(item))
    }

    /**
     * 根据 labelIncludes 配置裁剪 metrics 路径的 label，event 路径不经过此方法。
     * 未配置该指标名时原样返回。
     */
    private fun pruneLabelsForMetric(item: MetricsItem): MetricsItem {
        val keepKeys = customPushConfig.labelIncludes[item.name] ?: return item
        val pruned = item.labels.filterKeys { it in keepKeys }
        return item.copy(labels = pruned.toMutableMap())
    }

    private fun reportAsEvent(item: MetricsItem) {
        try {
            customEventExporter?.reportEvent(item.toEventItem())
        } catch (e: Exception) {
            logger.warn("report MetricsItem as event failed, name=${item.name}, errmsg=${e.message}")
        }
    }

    private fun matchesIncludes(name: String, includes: List<String>): Boolean {
        if (includes.isEmpty()) return false
        return includes.any { pattern ->
            when {
                pattern == "*" -> true
                '*' !in pattern -> pattern == name
                else -> {
                    if (patternCache.size > MAX_PATTERN_CACHE_SIZE) patternCache.clear()
                    patternCache.computeIfAbsent(pattern) { compileGlob(it) }.matches(name)
                }
            }
        }
    }

    private fun compileGlob(pattern: String): Regex {
        val regex = pattern.split('*').joinToString(".*") { Regex.escape(it) }
        return Regex("^$regex$")
    }

    private fun MetricsItem.toEventItem(): CustomEventItem {
        val dimension = HashMap<String, Any>(labels.size + baseEventDimension.size).apply {
            // base labels first (lowest priority), item labels override them
            baseEventDimension.forEach { (k, v) -> put(k, v) }
            labels.forEach { (k, v) -> if (k !in eventExtra) put(k, v) }
        }
        val extra = HashMap<String, Any>(eventExtra.size + 1).apply {
            putAll(eventExtra)
            put("value", value)
        }
        return CustomEventItem(
            eventName = name,
            content = help,
            dimension = dimension,
            extra = extra,
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CustomMetricsExporter::class.java)
        private const val MAX_PATTERN_CACHE_SIZE = 500
        private const val ENV_LABEL_KEY = "env"
    }
}
