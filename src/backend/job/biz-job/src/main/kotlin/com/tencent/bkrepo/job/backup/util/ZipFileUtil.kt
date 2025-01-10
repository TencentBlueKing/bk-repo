package com.tencent.bkrepo.job.backup.util

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object ZipFileUtil {

    fun compressDirectory(directoryPath: String, zipFilePath: String) {
        val directory = File(directoryPath)
        val zipFile = File(zipFilePath)

        val zipOutputStream = ZipOutputStream(FileOutputStream(zipFile))

        compress(directory, directory.name, zipOutputStream)

        zipOutputStream.close()
    }

    fun decompressFile(zipFilePath: String, destinationFolder: String) {
        val zipFile = ZipFile(zipFilePath)
        val entries = zipFile.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val entryDestination = File(destinationFolder, entry.name)
            if (entry.isDirectory) {
                entryDestination.mkdirs()
            } else {
                entryDestination.parentFile.mkdirs()
                val inputStream = zipFile.getInputStream(entry)
                val outputStream = FileOutputStream(entryDestination)
                inputStream.copyTo(outputStream)
                inputStream.close()
                outputStream.close()
            }
        }
    }

    private fun compress(file: File, fileName: String, zipOutputStream: ZipOutputStream) {
        if (file.isDirectory) {
            val files = file.listFiles()
            for (childFile in files) {
                compress(childFile, fileName + File.separator + childFile.name, zipOutputStream)
            }
        } else {
            val buffer = ByteArray(1024)
            val fileInputStream = FileInputStream(file)

            zipOutputStream.putNextEntry(ZipEntry(fileName))

            var length: Int
            while (fileInputStream.read(buffer).also { length = it } > 0) {
                zipOutputStream.write(buffer, 0, length)
            }

            fileInputStream.close()
        }
    }
}