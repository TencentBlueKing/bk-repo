package com.tencent.bkrepo.migrate.util.shell

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.LinkedList

class CommandStreamGobbler(
    private val `is`: InputStream,
    private val command: String,
    prefix: String
) : Thread() {

    private var prefix = ""
    var isReadFinish = false
        private set
    var isReady = false
        private set

    // 命令执行结果,0:执行中 1:超时 2:执行完成
    private var commandResult = 0
    private val infoList: MutableList<String?> = LinkedList()
    override fun run() {
        var isr: InputStreamReader? = null
        var br: BufferedReader? = null
        try {
            isr = InputStreamReader(`is`)
            br = BufferedReader(isr)
            var line: String?
            isReady = true
            while (commandResult != 1) {
                if (br.ready() || commandResult == 2) {
                    if (br.readLine().also { line = it } != null) {
                        infoList.add(line)
                    } else {
                        break
                    }
                } else {
                    sleep(100)
                }
            }
        } catch (ioe: InterruptedException) {
            println("正式执行命令：" + prefix + command + "有IO异常")
        } catch (ioe: IOException) {
            println("正式执行命令：" + prefix + command + "有IO异常")
        } finally {
            try {
                br?.close()
                isr?.close()
            } catch (ioe: IOException) {
                println("正式执行命令：" + prefix + command + "有IO异常")
            }
            isReadFinish = true
        }
    }

    fun getInfoList(): List<String?> {
        return infoList
    }

    fun setTimeout(timeout: Int) {
        commandResult = timeout
    }

    init {
        this.prefix = prefix
    }
}
