package com.tencent.bkrepo.migrate.util.shell

class CommandWaitForThread(private val process: Process) : Thread() {
    var isFinish = false
    var exitValue = -1
        private set

    override fun run() {
        try {
            exitValue = process.waitFor()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        } finally {
            isFinish = true
        }
    }
}
