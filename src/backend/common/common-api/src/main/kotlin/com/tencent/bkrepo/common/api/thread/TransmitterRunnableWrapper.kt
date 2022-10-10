package com.tencent.bkrepo.common.api.thread

/**
 * 支持传播threadLocal的Runnable
 * */
class TransmitterRunnableWrapper(val runnable: Runnable) : Runnable {
    private val snapshot: Snapshot = Transmitter.capture()

    override fun run() {
        Transmitter.replay(snapshot)
        try {
            runnable.run()
        } finally {
            Transmitter.reset(snapshot)
        }
    }
}
