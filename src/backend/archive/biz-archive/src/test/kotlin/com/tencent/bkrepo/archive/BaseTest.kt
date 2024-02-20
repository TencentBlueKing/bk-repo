package com.tencent.bkrepo.archive

import com.tencent.bkrepo.archive.utils.ArchiveUtils
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.common.storage.util.StorageUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.cloud.sleuth.Tracer
import org.springframework.cloud.sleuth.otel.bridge.OtelTracer
import org.springframework.context.annotation.ComponentScan
import org.springframework.test.context.TestPropertySource

@ComponentScan("com.tencent.bkrepo.archive")
@SpringBootConfiguration
@EnableAutoConfiguration
@TestPropertySource(locations = ["classpath:bootstrap-ut.properties"])
class BaseTest {

    @Autowired
    lateinit var archiveUtils: ArchiveUtils

    @Autowired
    lateinit var storageUtils: StorageUtils

    fun initMock() {
        mockkObject(SpringContextUtils)
        every { SpringContextUtils.publishEvent(any()) } returns Unit
        val tracer = mockk<OtelTracer>()
        every { SpringContextUtils.getBean<Tracer>() } returns tracer
        every { tracer.currentSpan() } returns null
    }
}
