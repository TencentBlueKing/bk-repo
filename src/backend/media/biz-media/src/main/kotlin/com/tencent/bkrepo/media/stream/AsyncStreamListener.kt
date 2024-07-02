package com.tencent.bkrepo.media.stream

import org.slf4j.LoggerFactory
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 异步录制监听器
 * 使用queue缓存需要录制的数据，然后异步消费，以保证录制不对流的分发产生影响
 * */
abstract class AsyncStreamListener(private val scheduler: ThreadPoolTaskScheduler) :
    RecordingListener {

    val packetQueue = LinkedBlockingQueue<StreamPacket>(8192)
    private var future: Future<*>? = null
    private val processing = AtomicBoolean(false)
    val recording = AtomicBoolean(false)
    private val recvCount = MediaMetrics.getCounter(MediaMetrics.Action.PACKET_RECV)
    private val handlerCount = MediaMetrics.getCounter(MediaMetrics.Action.PACKET_HANDLER)
    private val handlerSizeCount = MediaMetrics.getSizeCounter(MediaMetrics.Action.PACKET_HANDLER)
    private val lossCount = MediaMetrics.getCounter(MediaMetrics.Action.PACKET_LOSS)
    private var streamName: String = ""

    override fun packetReceived(packet: StreamPacket) {
        recvCount.increment()
        if (recording.get()) {
            if (!packetQueue.offer(packet)) {
                logger.info("packet not added to recording queue")
                lossCount.increment()
            }
        } else {
            logger.info("A packet was received by recording listener, but it's not recording anymore")
        }
    }

    override fun init(name: String) {
        this.streamName = name
    }

    override fun start() {
        if (recording.compareAndSet(false, true)) {
            future = scheduler.scheduleWithFixedDelay(this::processQueue, DELAY)
            logger.info("Recording listener start success")
        } else {
            logger.info("Recording listener was already started")
        }
    }

    override fun stop() {
        if (!recording.compareAndSet(true, false)) {
            logger.info("Recording listener was already stopped.")
            return
        }
        if (packetQueue.isNotEmpty()) {
            if (!processing.get()) {
                logger.info("Packet queue was not empty on stop,processing...")
                while (packetQueue.isNotEmpty()) {
                    processQueue()
                }
            } else {
                logger.info("Packet queue was not empty on stop,wait...")
                while (packetQueue.isNotEmpty()) {
                    // wait
                }
            }
        }
        future?.cancel(true)
        logger.info("Recording listener stop success.")
    }

    override fun isRecording(): Boolean {
        return recording.get()
    }

    abstract fun handler(packet: StreamPacket)

    private fun processQueue() {
        if (processing.compareAndSet(false, true)) {
            logger.debug("Start process queue,stream: $streamName ,queue size: ${packetQueue.size}")
            try {
                var packet = packetQueue.poll()
                while (packet != null) {
                    handler(packet)
                    handlerCount.increment()
                    handlerSizeCount.increment(packet.getData().size.toDouble())
                    packet = packetQueue.poll()
                }
            } finally {
                logger.debug("Finish process queue ,stream: $streamName")
                processing.set(false)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AsyncStreamListener::class.java)
        private const val DELAY = 3000L
    }
}
