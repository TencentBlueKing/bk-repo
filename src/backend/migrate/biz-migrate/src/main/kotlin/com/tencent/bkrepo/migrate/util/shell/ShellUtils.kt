package com.tencent.bkrepo.migrate.util.shell

import com.tencent.bkrepo.migrate.pojo.ShellResult
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Date

object ShellUtils {

    fun runShell(path: String): ShellResult {
        var process: Process? = null
        val timeout = (30 * 60 * 1000).toLong()
        val processList = mutableListOf<String?>()
        var exitValue = 0
        try {
            process = Runtime.getRuntime().exec(path)
            val errorGobbler = CommandStreamGobbler(process.errorStream, path, "ERR")
            val outputGobbler = CommandStreamGobbler(process.inputStream, path, "STD")
            errorGobbler.start()
            // 必须先等待错误输出ready再建立标准输出
            while (!errorGobbler.isReady) {
                Thread.sleep(10)
            }
            outputGobbler.start()
            while (!outputGobbler.isReady) {
                Thread.sleep(10)
            }
            val commandThread = CommandWaitForThread(process)
            commandThread.start()
            val commandTime = Date().time
            var nowTime = Date().time
            var timeoutFlag = false
            while (!commandIsFinish(commandThread, errorGobbler, outputGobbler)) {
                if (nowTime - commandTime > timeout) {
                    timeoutFlag = true
                    break
                } else {
                    Thread.sleep(100)
                    nowTime = Date().time
                }
            }
            if (timeoutFlag) {
                // 命令超时
                errorGobbler.setTimeout(1)
                outputGobbler.setTimeout(1)
                println("正式执行命令：" + path + "超时")
            } else {
                // 命令执行完成
                errorGobbler.setTimeout(2)
                outputGobbler.setTimeout(2)
            }
            while (true) {
                if (errorGobbler.isReadFinish && outputGobbler.isReadFinish) {
                    break
                }
                Thread.sleep(10)
            }
            processList.addAll(outputGobbler.getInfoList())
            exitValue = process.exitValue()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            process?.destroy()
        }
        return ShellResult(exitValue = exitValue, outputStr = processList)
    }

    fun runShellWithResult(shell: String): ShellResult {
        val processList = mutableListOf<String>()
        var result = 0
        try {
            val process = Runtime.getRuntime().exec(shell)
            var line: String?
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                while (reader.readLine().also { line = it } != null) {
                    line?.let { processList.add(it) }
                }
            }
        } catch (e: Exception) {
            println("Shell :$shell failed!")
            e.printStackTrace()
        }
        return ShellResult(exitValue = result, outputStr = processList)
    }

    private fun commandIsFinish(
        commandThread: CommandWaitForThread?,
        errorGobbler: CommandStreamGobbler,
        outputGobbler: CommandStreamGobbler
    ): Boolean {
        return commandThread?.isFinish ?: (errorGobbler.isReadFinish && outputGobbler.isReadFinish)
    }
}
