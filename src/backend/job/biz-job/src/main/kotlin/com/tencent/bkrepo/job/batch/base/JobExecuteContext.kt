package com.tencent.bkrepo.job.batch.base

import java.time.LocalDateTime

data class JobExecuteContext(
    /**
     * 任务id
     */
    var jobId: String,
    /**
     * 任务参数
     */
    var jobParamMap: Map<String, Any>,
    /**
     * 本次任务执行日志id
     */
    var logId: String,
    /**
     * 本次任务触发时间
     */
    var triggerTime: LocalDateTime,
    /**
     * 任务更新时间
     * */
    var updateTime: LocalDateTime,
    /**
     * 分片广播序号
     */
    var broadcastIndex: Int = 0,
    /**
     * 分片广播总数
     */
    var broadcastTotal: Int = 1,
    /**
     * 资源内容，可以是脚本，也可以是yaml
     * */
    var source: String?,
    /**
     * k8s任务使用的镜像
     * */
    var image: String?,
)
