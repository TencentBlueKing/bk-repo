package com.tencent.bkrepo.common.storage.filesystem

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.channels.Channels
import kotlin.concurrent.thread

internal class FileLockExecutorTest {

    private val inputFileName = "test.file"
    private val outputFileName = "output.file"
    private val inputFile = File(javaClass.getResource("/$inputFileName").file)
    private val fileSystemClient = FileSystemClient(javaClass.getResource("/").path)

    @BeforeEach
    fun beforeEach() {
        javaClass.getResource("/$outputFileName")?.file?.let {
            val outputFile = File(it)
            if (outputFile.exists()) {
                outputFile.delete()
            }
        }
    }

    @Test
    fun executeInLock1() {
        val file = fileSystemClient.touch("/", outputFileName)
        val thread1 = thread {
            FileLockExecutor.executeInLock(file) {
                // 独占锁
                println("thread1: " + System.currentTimeMillis() / 1000)
                it.transferFrom(inputFile.inputStream().channel, 0, inputFile.length())
                Thread.sleep(1000 * 5)
            }
        }

        Thread.sleep(1000 * 1)
        val thread2 = thread {
            FileLockExecutor.executeInLock(file.inputStream()) {
                // 拿到锁的时间比thread1应该晚5秒
                println("thread2: " + System.currentTimeMillis() / 1000)
                // 读取
                Assertions.assertEquals("Hello, world!", file.readText())
            }
        }

        thread1.join()
        thread2.join()
    }

    @Test
    fun executeInLock2() {
        val file = fileSystemClient.touch("/", outputFileName)
        file.outputStream().channel.transferFrom(inputFile.inputStream().channel, 0, inputFile.length())
        val thread1 = thread {
            FileLockExecutor.executeInLock(file.inputStream()) {
                // 共享锁
                println("thread1: " + System.currentTimeMillis() / 1000)
                Assertions.assertEquals("Hello, world!", file.readText())
                Thread.sleep(1000 * 5)
            }
        }

        Thread.sleep(1000 * 1)
        val thread2 = thread {
            FileLockExecutor.executeInLock(file.inputStream()) {
                // 拿到锁的时间比thread1应该晚1秒
                println("thread2: " + System.currentTimeMillis() / 1000)
                Assertions.assertEquals("Hello, world!", file.readText())
            }
        }

        thread1.join()
        thread2.join()
    }

    @Test
    fun executeInLock3() {
        val file = fileSystemClient.touch("/", outputFileName)
        file.outputStream().channel.transferFrom(inputFile.inputStream().channel, 0, inputFile.length())

        val thread1 = thread {
            FileLockExecutor.executeInLock(file.inputStream()) {
                // 共享锁
                println("thread1: " + System.currentTimeMillis() / 1000)
                Assertions.assertEquals("Hello, world!", file.readText())
                Thread.sleep(1000 * 5)
            }
        }

        Thread.sleep(1000 * 1)
        val thread2 = thread {
            FileLockExecutor.executeInLock(file) {
                // 独占锁，拿到锁的时间比thread1应该晚5秒
                println("thread3: " + System.currentTimeMillis() / 1000)
                // 删除
                file.delete()
            }
        }

        thread1.join()
        thread2.join()
    }

    @Test
    fun concurrentTest() {
        val threadList = mutableListOf<Thread>()
        val file = fileSystemClient.touch("/", outputFileName)
        repeat(100) {
            val thread = thread {
                val input = Channels.newChannel("0".byteInputStream())
                FileLockExecutor.executeInLock(file) { channel ->
                    // 独占锁
                    channel.transferFrom(input, channel.size(), 1)
                }
            }
            threadList.add(thread)
        }
        threadList.forEach { it.join() }
        println(file.readText().length)
        // Assertions.assertEquals(100, file.readText().length)
    }

    @Test
    fun nonConcurrentTest() {
        val threadList = mutableListOf<Thread>()
        val file = fileSystemClient.touch("/", outputFileName)
        repeat(100) {
            val thread = thread {
                val channel = file.outputStream().channel
                val input = Channels.newChannel("0".byteInputStream())
                channel.transferFrom(input, channel.size(), 1)
            }
            threadList.add(thread)
        }
        threadList.forEach { it.join() }
        println(file.readText().length)
    }
}
