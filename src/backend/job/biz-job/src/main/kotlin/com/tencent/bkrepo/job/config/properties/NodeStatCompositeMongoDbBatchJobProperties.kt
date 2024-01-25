package com.tencent.bkrepo.job.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("job.node-stat-composite-mongodb-batch")
class NodeStatCompositeMongoDbBatchJobProperties (
    override var cron: String = "0 0 15 * * ?",
    /**
     * 可用用于控制任务不执行或者执行执行哪些表数据
     * 当值小于 1 时，任务不执行
     * 当值大于 7 时，不特定指定执行哪些表
     * 当值为1 - 7时，优先执行node_num%7 +1 == runPolicy对应的node表
     */
    var runPolicy: Int = 8,
    /**
     * 是否分多次执行
     * false: 一次性将所有表执行完
     * true: 分7天去执行所有表，每天执行 node_num%7 +1 == 当天的node表
     */
    var multipleExecutions: Boolean = false,
    /**
     * 可用于将部分表的统计数据使用redis缓存存储临时存储
     * 避免使用内存进行缓存时导致使用内存过大
     */
    var redisCacheCollections: List<String> = emptyList(),
    /**
     * 是否遍历所有项目
     */
    var runAllProjects: Boolean = true
): CompositeJobProperties()
