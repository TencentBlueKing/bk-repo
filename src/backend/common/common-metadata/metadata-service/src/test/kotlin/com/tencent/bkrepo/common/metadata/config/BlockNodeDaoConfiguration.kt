package com.tencent.bkrepo.common.metadata.config

import com.tencent.bkrepo.common.metadata.dao.blocknode.RBlockNodeDao
import com.tencent.bkrepo.common.metadata.properties.BlockNodeProperties
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
class BlockNodeDaoConfiguration {
    @Bean
    fun rBlockNodeDao(blockNodeProperties: BlockNodeProperties? = null): RBlockNodeDao {
        return RBlockNodeDao(blockNodeProperties)
    }
}
