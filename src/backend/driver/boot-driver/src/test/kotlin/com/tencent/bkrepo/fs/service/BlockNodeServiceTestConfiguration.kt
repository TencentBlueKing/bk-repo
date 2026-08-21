package com.tencent.bkrepo.fs.service

import com.tencent.bkrepo.common.metadata.dao.blocknode.RBlockNodeDao
import com.tencent.bkrepo.common.metadata.dao.file.RFileReferenceDao
import com.tencent.bkrepo.common.metadata.properties.BlockNodeProperties
import com.tencent.bkrepo.common.metadata.service.blocknode.RBlockNodeService
import com.tencent.bkrepo.common.metadata.service.blocknode.impl.RBlockNodeServiceImpl
import com.tencent.bkrepo.common.metadata.service.file.RFileReferenceService
import com.tencent.bkrepo.common.metadata.service.file.impl.RFileReferenceServiceImpl
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

/**
 * 精确注册 reactive block-node 测试 bean，避免 @ComponentScan 拉起整个 metadata 上下文。
 * @Bean 直接 new 可绕过 [ReactiveCondition]（DataMongoTest 同时存在 sync/reactive driver 时条件为 false）。
 */
@TestConfiguration
class BlockNodeServiceTestConfiguration {

    @Bean
    fun rBlockNodeDao(blockNodeProperties: BlockNodeProperties): RBlockNodeDao =
        RBlockNodeDao(blockNodeProperties)

    @Bean
    fun rFileReferenceDao(): RFileReferenceDao = RFileReferenceDao()

    @Bean
    fun rFileReferenceService(fileReferenceDao: RFileReferenceDao): RFileReferenceService =
        RFileReferenceServiceImpl(fileReferenceDao)

    @Bean
    fun rBlockNodeService(
        rBlockNodeDao: RBlockNodeDao,
        rFileReferenceService: RFileReferenceService,
        rNodeDao: com.tencent.bkrepo.common.metadata.dao.node.RNodeDao,
    ): RBlockNodeService = RBlockNodeServiceImpl(rBlockNodeDao, rFileReferenceService, rNodeDao)
}
