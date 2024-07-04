package com.tencent.bkrepo.media.stream

import com.google.common.hash.HashCode
import com.google.common.hash.Hashing
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.security.MessageDigest
import kotlin.random.Random

class ClientStreamTest {
    val scheduler = ThreadPoolTaskScheduler()

    @BeforeEach
    fun before() {
        MediaMetrics.registry = SimpleMeterRegistry()
        scheduler.initialize()
    }

    @AfterEach
    fun after() {
        scheduler.shutdown()
    }

    @Test
    fun streamTest() {
        val md5 = MessageDigest.getInstance("MD5")
        val md5Listener = object : AsyncStreamListener(scheduler) {
            override fun handler(packet: StreamPacket) {
                md5.update(packet.getData())
            }

            override fun init(name: String) {
                println("init stream $name")
            }
        }
        val data = Random.nextBytes(1024)
        val originMd5 = Hashing.md5().hashBytes(data).toString()
        val stream = ClientStream("test", "test", Long.MAX_VALUE, md5Listener)
        stream.start()
        stream.saveAs()
        val len = data.size / 10
        var pos = 0
        while (pos < data.size) {
            val end = (pos + len).coerceAtMost(data.size - 1)
            val d = data.sliceArray(pos..end)
            val packet = VideoData(d, System.currentTimeMillis())
            stream.dispatch(packet)
            pos = end + 1
        }
        stream.stop()
        val recvMd5 = HashCode.fromBytes(md5.digest()).toString()
        Assertions.assertEquals(originMd5, recvMd5)
    }
}
