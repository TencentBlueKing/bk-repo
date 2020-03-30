package com.tencent.bkrepo.helm.artifact.util

import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileInputStream
import java.io.Serializable
import java.util.UUID
import java.util.zip.GZIPInputStream

internal class TarUtilTest{
    @Test
    fun unTar() {
        val file = File("/Users/weaving/workspace/bkrepo/helm/package/hellochart-0.1.0.tgz")
        val tarIn: TarArchiveInputStream = TarArchiveInputStream(GZIPInputStream(FileInputStream(file)))
        val outputDir = File((TarUtilTest::class.java.classLoader.getResource("").path)+"${UUID.randomUUID()}")
        // println(TarUtilTest::class.java.classLoader.getResource("").path)
        var entry: ArchiveEntry? = null
        while ({entry = tarIn.nextEntry; entry}() != null) {
            if(entry!!.isDirectory){

            }
            else {
                val tempFile = File("$outputDir/${entry!!.name}")
                println(entry!!.name)
            }
        }
    }



}