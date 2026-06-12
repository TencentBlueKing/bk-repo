package com.tencent.bkrepo.fs.server.config.properties.drive

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import java.time.Duration

/**
 * Drive 操作审计日志配置
 */
@ConfigurationProperties("drive.operate-log")
class DriveOperateLogProperties {

    /**
     * 是否启用 Drive 操作审计，默认开启。
     * 关闭后跳过日志构造、入队和落库。
     */
    var enabled: Boolean = true

    /**
     * 按事件类型关闭审计，值为 EventType 枚举名（如 `DRIVE_BLOCK_READ`）。
     * 适用于关闭高频读/查询类操作，只保留写入与变更类审计。
     */
    var disabledTypes: MutableList<String> = mutableListOf()

    /**
     * 后台 worker 单次批量落库的最大条数，默认 200。
     */
    var batchSize: Int = 200

    /**
     * 后台 worker 定时 flush 间隔，默认 10 秒。
     * 队列为空时，到达间隔即使未满 [batchSize] 也会触发落库；有积压时 worker 连续 flush。
     */
    var flushInterval: Duration = Duration.ofSeconds(10)

    /**
     * 内存队列容量，默认 1000。
     * 队列满时按 [overflowStrategy] 处理溢出。
     */
    var queueCapacity: Int = 1000

    /**
     * 队列溢出策略，默认 `DROP_NEWEST`。
     * 可选值：`DROP_NEWEST`、`BLOCK`。
     * 优先保护 Drive 主流程，不阻塞业务请求。
     */
    var overflowStrategy: String = "DROP_NEWEST"

    /**
     * BLOCK 溢出策略下等待队列容量的重试间隔，默认 100 毫秒。
     */
    var queueBlockRetryInterval: Duration = Duration.ofMillis(100)

    /**
     * 错误日志输出间隔，默认不限制。
     * 设置为大于 0 的时间后，同类错误在间隔内只输出一次。
     */
    var errorLogInterval: Duration = Duration.ZERO

    /**
     * 每秒允许写入数据库的最大审计记录数，默认 100。
     * 仅在最终落库前通过限速器生效，不影响内存 batch 收集大小。
     * 落库时按该值分片 acquire 并写入，避免单次 save 超过 QPS 限制。
     * 限制的是聚合后的实际落库记录数，值小于等于 0 表示不限制。
     */
    var writeLimitQps: Int = 200

    /**
     * batch 内聚合配置，用于合并同一用户的高频相同操作，降低落库压力。
     */
    @NestedConfigurationProperty
    var aggregation: AggregationProperties = AggregationProperties()

    /**
     * Drive 操作审计 batch 内聚合配置。
     */
    class AggregationProperties {

        /**
         * 是否启用 batch 内聚合，默认开启。
         */
        var enabled: Boolean = true

        /**
         * 允许聚合的事件类型列表，默认包含高频读/查询类操作。
         * 写入、删除、更新、重命名等变更类操作不应加入此列表。
         */
        var types: MutableList<String> = mutableListOf(
            "DRIVE_BLOCK_READ",
            "DRIVE_NODE_LIST",
            "DRIVE_NODE_MODIFIED_LIST",
            "DRIVE_SNAPSHOT_LIST",
        )
    }
}
