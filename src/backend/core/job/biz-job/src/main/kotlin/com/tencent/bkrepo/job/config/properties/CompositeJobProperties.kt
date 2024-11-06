package com.tencent.bkrepo.job.config.properties

open class CompositeJobProperties(
    /**
     * 为空时表示全部子任务启用
     */
    var enabledChildJobs: Set<String> = emptySet(),
    /**
     * 禁用的子任务，为空时表示不禁用任何子任务，仅在[enabledChildJobs]为空时生效
     */
    var disabledChildJobs: Set<String> = emptySet()
): MongodbJobProperties()
