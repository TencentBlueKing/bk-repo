package com.tencent.bkrepo.common.metrics.constant

/**
 * 联邦仓库监控指标常量
 *
 */

// ==================== Lag Metrics（延迟指标） ====================

/**
 * 元数据跟踪延迟数
 * 记录元数据已同步但文件尚未传输的数量
 */
const val FEDERATION_METADATA_LAG = "federation.metadata.lag"
const val FEDERATION_METADATA_LAG_DESC = "联邦元数据跟踪延迟数量（元数据已同步但文件未传输）"

/**
 * 增量同步事件延迟数
 * 记录待处理的增量同步事件数量
 */
const val FEDERATION_EVENT_LAG = "federation.event.lag"
const val FEDERATION_EVENT_LAG_DESC = "联邦增量同步事件延迟数量（待处理的增量同步事件）"

/**
 * 全量同步失败延迟数
 * 记录全量同步中失败的节点和版本数量
 */
const val FEDERATION_FAILURE_LAG = "federation.failure.lag"
const val FEDERATION_FAILURE_LAG_DESC = "联邦全量同步失败延迟数量（失败的节点和版本）"

// ==================== Event Metrics（事件指标） ====================

/**
 * 事件处理总数
 */
const val FEDERATION_EVENT_TOTAL = "federation.event.total"
const val FEDERATION_EVENT_TOTAL_DESC = "联邦事件处理总数"

/**
 * 事件处理成功数
 */
const val FEDERATION_EVENT_SUCCESS = "federation.event.success"
const val FEDERATION_EVENT_SUCCESS_DESC = "联邦事件处理成功数"

/**
 * 事件处理失败数
 */
const val FEDERATION_EVENT_FAILED = "federation.event.failed"
const val FEDERATION_EVENT_FAILED_DESC = "联邦事件处理失败数"

/**
 * 事件重试次数
 */
const val FEDERATION_EVENT_RETRY_COUNT = "federation.event.retry.count"
const val FEDERATION_EVENT_RETRY_COUNT_DESC = "联邦事件重试总次数"

// ==================== Full Sync Metrics（全量同步指标） ====================

/**
 * 全量同步执行中的仓库数
 */
const val FEDERATION_FULL_SYNC_RUNNING = "federation.fullsync.running"
const val FEDERATION_FULL_SYNC_RUNNING_DESC = "联邦全量同步执行中的仓库数量"

/**
 * 全量同步总次数
 */
const val FEDERATION_FULL_SYNC_TOTAL = "federation.fullsync.total"
const val FEDERATION_FULL_SYNC_TOTAL_DESC = "联邦全量同步执行总次数"

/**
 * 全量同步成功次数
 */
const val FEDERATION_FULL_SYNC_SUCCESS = "federation.fullsync.success"
const val FEDERATION_FULL_SYNC_SUCCESS_DESC = "联邦全量同步成功次数"

/**
 * 全量同步失败次数
 */
const val FEDERATION_FULL_SYNC_FAILED = "federation.fullsync.failed"
const val FEDERATION_FULL_SYNC_FAILED_DESC = "联邦全量同步失败次数"

/**
 * 全量同步耗时（秒）
 */
const val FEDERATION_FULL_SYNC_DURATION = "federation.fullsync.duration.seconds"
const val FEDERATION_FULL_SYNC_DURATION_DESC = "联邦全量同步耗时（秒）"

/**
 * 全量同步传输字节数
 */
const val FEDERATION_FULL_SYNC_BYTES_TRANSFERRED = "federation.fullsync.bytes.transferred"
const val FEDERATION_FULL_SYNC_BYTES_TRANSFERRED_DESC = "联邦全量同步传输字节数"

/**
 * 全量同步传输文件数
 */
const val FEDERATION_FULL_SYNC_FILES_TRANSFERRED = "federation.fullsync.files.transferred"
const val FEDERATION_FULL_SYNC_FILES_TRANSFERRED_DESC = "联邦全量同步传输文件数量"

// ==================== Member State Metrics（成员状态指标） ====================

/**
 * 联邦成员总数
 * 包含当前仓库在联邦中的成员总数
 */
const val FEDERATION_MEMBER_TOTAL = "federation.member.total"
const val FEDERATION_MEMBER_TOTAL_DESC = "联邦成员总数"

/**
 * 健康的联邦成员数
 * 与所有成员正常通信的成员数
 */
const val FEDERATION_MEMBER_HEALTHY = "federation.member.healthy"
const val FEDERATION_MEMBER_HEALTHY_DESC = "健康的联邦成员数（可正常通信）"

/**
 * 延迟的联邦成员数
 * 同步延迟但仍可通信的成员数
 */
const val FEDERATION_MEMBER_DELAYED = "federation.member.delayed"
const val FEDERATION_MEMBER_DELAYED_DESC = "延迟的联邦成员数（同步延迟）"

/**
 * 错误的联邦成员数
 * 同步出现错误的成员数
 */
const val FEDERATION_MEMBER_ERROR = "federation.member.error"
const val FEDERATION_MEMBER_ERROR_DESC = "错误的联邦成员数（同步错误）"

/**
 * 禁用的联邦成员数
 * 已禁用的成员数
 */
const val FEDERATION_MEMBER_DISABLED = "federation.member.disabled"
const val FEDERATION_MEMBER_DISABLED_DESC = "禁用的联邦成员数"

/**
 * 不支持的联邦成员数
 * 版本不支持联邦功能的成员数
 */
const val FEDERATION_MEMBER_UNSUPPORTED = "federation.member.unsupported"
const val FEDERATION_MEMBER_UNSUPPORTED_DESC = "不支持的联邦成员数（版本不支持）"

/**
 * 联邦成员状态
 * 记录成员在联邦中的状态
 */
const val FEDERATION_MEMBER_STATE = "federation.member.state"
const val FEDERATION_MEMBER_STATE_DESC = "联邦成员状态计数"

// ==================== Miscellaneous Metrics（其他指标） ====================

/**
 * 联邦仓库总数
 */
const val FEDERATION_REPOSITORY_TOTAL = "federation.repository.total"
const val FEDERATION_REPOSITORY_TOTAL_DESC = "联邦仓库总数"

/**
 * 文件传输速率（bytes/s）
 */
const val FEDERATION_FILE_TRANSFER_RATE = "federation.file.transfer.rate"
const val FEDERATION_FILE_TRANSFER_RATE_DESC = "联邦文件传输速率（bytes/s）"

/**
 * 元数据同步速率（events/s）
 */
const val FEDERATION_METADATA_SYNC_RATE = "federation.metadata.sync.rate"
const val FEDERATION_METADATA_SYNC_RATE_DESC = "联邦元数据同步速率（events/s）"

/**
 * 元数据同步耗时
 */
const val FEDERATION_METADATA_SYNC_DURATION = "federation.metadata.sync.duration"
const val FEDERATION_METADATA_SYNC_DURATION_DESC = "联邦元数据同步耗时"

/**
 * 元数据同步成功数
 */
const val FEDERATION_METADATA_SYNC_SUCCESS = "federation.metadata.sync.success"
const val FEDERATION_METADATA_SYNC_SUCCESS_DESC = "联邦元数据同步成功数"

/**
 * 元数据同步失败数
 */
const val FEDERATION_METADATA_SYNC_FAILED = "federation.metadata.sync.failed"
const val FEDERATION_METADATA_SYNC_FAILED_DESC = "联邦元数据同步失败数"

/**
 * 文件传输字节数
 */
const val FEDERATION_FILE_TRANSFER_BYTES = "federation.file.transfer.bytes"
const val FEDERATION_FILE_TRANSFER_BYTES_DESC = "联邦文件传输字节数"

/**
 * 文件传输耗时
 */
const val FEDERATION_FILE_TRANSFER_DURATION = "federation.file.transfer.duration"
const val FEDERATION_FILE_TRANSFER_DURATION_DESC = "联邦文件传输耗时"

/**
 * 文件传输成功数
 */
const val FEDERATION_FILE_TRANSFER_SUCCESS = "federation.file.transfer.success"
const val FEDERATION_FILE_TRANSFER_SUCCESS_DESC = "联邦文件传输成功数"

/**
 * 文件传输失败数
 */
const val FEDERATION_FILE_TRANSFER_FAILED = "federation.file.transfer.failed"
const val FEDERATION_FILE_TRANSFER_FAILED_DESC = "联邦文件传输失败数"

/**
 * 文件传输线程池活跃数
 */
const val FEDERATION_FILE_TRANSFER_ACTIVE = "federation.file.transfer.active"
const val FEDERATION_FILE_TRANSFER_ACTIVE_DESC = "联邦文件传输线程池活跃任务数"

/**
 * 文件传输线程池队列大小
 */
const val FEDERATION_FILE_TRANSFER_QUEUE_SIZE = "federation.file.transfer.queue.size"
const val FEDERATION_FILE_TRANSFER_QUEUE_SIZE_DESC = "联邦文件传输线程池队列大小"

/**
 * 全量同步线程池活跃数
 */
const val FEDERATION_FULL_SYNC_ACTIVE = "federation.fullsync.active"
const val FEDERATION_FULL_SYNC_ACTIVE_DESC = "联邦全量同步线程池活跃任务数"

/**
 * 全量同步线程池队列大小
 */
const val FEDERATION_FULL_SYNC_QUEUE_SIZE = "federation.fullsync.queue.size"
const val FEDERATION_FULL_SYNC_QUEUE_SIZE_DESC = "联邦全量同步线程池队列大小"

/**
 * 最后一次全量同步完成时间戳（Unix timestamp）
 */
const val FEDERATION_LAST_FULL_SYNC_TIME = "federation.last.fullsync.timestamp"
const val FEDERATION_LAST_FULL_SYNC_TIME_DESC = "最后一次全量同步完成时间戳"

