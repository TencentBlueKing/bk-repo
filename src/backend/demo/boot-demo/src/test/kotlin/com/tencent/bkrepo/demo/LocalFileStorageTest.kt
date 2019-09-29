package com.tencent.bkrepo.demo

import com.tencent.bkrepo.common.storage.local.LocalFileStorage
import com.tencent.bkrepo.common.storage.local.LocalStorageProperties
import com.tencent.bkrepo.common.storage.strategy.LocateStrategy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * LocalFileStorageTest
 *
 * @author: carrypan
 * @date: 2019-09-26
 */
@DisplayName("LocalFileStorage测试")
@SpringBootTest
class LocalFileStorageTest {

    @Autowired
    private lateinit var properties: LocalStorageProperties

    @Autowired
    private lateinit var locateStrategy: LocateStrategy

    private var fileStorage: LocalFileStorage? = null

    @BeforeEach
    fun beforeEach() {
        fileStorage = LocalFileStorage(locateStrategy, properties)
    }

}