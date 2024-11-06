package com.tencent.bkrepo.git.internal.storage

import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import org.eclipse.jgit.internal.storage.dfs.DfsOutputStream
import org.eclipse.jgit.internal.storage.dfs.DfsPackDescription
import org.eclipse.jgit.internal.storage.dfs.ReadableChannel

class InMemoryRepositoryDataService : RepositoryDataService {

    var packs = mutableListOf<DfsPackDescription>()

    var db = mutableMapOf<String, ByteArray>()

    override fun savePackDescriptions(
        repository: CodeRepository,
        desc: Collection<DfsPackDescription>,
        replace: Collection<DfsPackDescription>?
    ) {
        val n: MutableList<DfsPackDescription>
        n = ArrayList(desc.size + packs.size)
        n.addAll(desc)
        n.addAll(packs)
        if (replace != null) n.removeAll(replace)
        packs = n
    }

    override fun deletePackDescriptions(repository: CodeRepository, desc: Collection<DfsPackDescription>) {
        throw RuntimeException("Not support")
    }

    override fun listPackDescriptions(repository: CodeRepository): List<DfsPackDescription> {
        return packs
    }

    override fun getReadableChannel(repository: CodeRepository, fileName: String, blockSize: Int): ReadableChannel {
        with(repository) {
            val key = "$projectId$repoName$fileName"
            val fileData = db[key] ?: throw FileNotFoundException(key)
            return DfsReadableChannel(blockSize, DfsByteArrayDataReader(fileData))
        }
    }

    override fun getOutputStream(repository: CodeRepository, fileName: String): DfsOutputStream {
        val key = "${repository.projectId}${repository.repoName}$fileName"
        return object : Out() {
            override fun flush() {
                db[key] = getOutData()
            }
        }
    }

    private open class Out : DfsOutputStream() {
        private val dst = ByteArrayOutputStream()
        var data: ByteArray? = null
        override fun write(buf: ByteArray, off: Int, len: Int) {
            data = null
            dst.write(buf, off, len)
        }

        override fun read(position: Long, buf: ByteBuffer): Int {
            val d = getOutData()
            val n = (d.size - position.toInt()).coerceAtMost(buf.remaining())
            if (n == 0) {
                return -1
            }
            buf.put(d, position.toInt(), n)
            return n
        }

        fun getOutData(): ByteArray {
            if (data == null) {
                data = dst.toByteArray()
            }
            return data!!
        }

        override fun close() {
            flush()
        }
    }
}
