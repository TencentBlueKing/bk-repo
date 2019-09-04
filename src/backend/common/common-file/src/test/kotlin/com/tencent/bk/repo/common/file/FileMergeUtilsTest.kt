package com.tencent.bk.repo.common.file

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

class FileMergeUtilsTest {

    @Test
    fun mergeFilesWithEmptyFile() {
        Assertions.assertThrows(IOException::class.java) {
            FileMergeUtils.mergeFiles(emptyList(), File("tmp"))
        }
    }

    @Test
    fun mergeFilesWithBadFile() {
        val data1 = "1234567890\n"
        val data2 = "0987654321\n"
        // mock split file
        val file1 = File.createTempFile("tmp1_", "txt")
        file1.writeText(data1)
        file1.deleteOnExit()
        val file2 = File.createTempFile("tmp2_", "txt")
        file2.writeText(data2)
        file2.deleteOnExit()
        val outputFile = File("/root/merge")

        Assertions.assertThrows(IOException::class.java) {
            FileMergeUtils.mergeFiles(listOf(file1, file2), outputFile)
        }
    }


    @Test
    fun mergeFiles() {
        val file = File.createTempFile("tmp_", "txt")
        file.deleteOnExit()
        val data1 = "1234567890\n"
        val data2 = "0987654321\n"
        // mock split file
        val file1 = File.createTempFile("tmp1_", "txt")
        file1.writeText(data1)
        file1.deleteOnExit()
        val file2 = File.createTempFile("tmp2_", "txt")
        file2.writeText(data2)
        file2.deleteOnExit()
        // merge File
        FileMergeUtils.mergeFiles(listOf(file1, file2), file)
        val expect = FileDigestUtils.fileSha256(arrayOf(file.absolutePath))
        println("expect_fileSha1=$expect")

        val actual = FileDigestUtils.fileSha256(arrayOf(file1.absolutePath, file2.absolutePath))
        println("actual_fileSha1=$actual")
        assertEquals(expect, actual)
    }
}