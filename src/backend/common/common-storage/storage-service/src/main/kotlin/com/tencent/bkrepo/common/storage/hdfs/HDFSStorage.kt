package com.tencent.bkrepo.common.storage.hdfs

import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.bound
import com.tencent.bkrepo.common.storage.core.AbstractFileStorage
import com.tencent.bkrepo.common.storage.credentials.HDFSCredentials
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import java.io.File
import java.io.InputStream
import java.net.URI

/**
 *
 * @author: carrypan
 * @date: 2020/1/13
 */
open class HDFSStorage : AbstractFileStorage<HDFSCredentials, HDFSClient>() {

    override fun store(path: String, filename: String, file: File, client: HDFSClient) {
        val localPath = Path(file.absolutePath)
        val remotePath = concatRemotePath(path, filename, client)
        client.fileSystem.copyFromLocalFile(localPath, remotePath)
    }

    override fun store(path: String, filename: String, inputStream: InputStream, size: Long, client: HDFSClient) {
        val remotePath = concatRemotePath(path, filename, client)
        val outputStream = client.fileSystem.create(remotePath, true)
        outputStream.use { inputStream.copyTo(outputStream) }
    }

    override fun load(path: String, filename: String, range: Range, client: HDFSClient): InputStream? {
        val remotePath = concatRemotePath(path, filename, client)
        val inputStream = client.fileSystem.open(remotePath)
        return inputStream.apply { seek(range.start) }.bound(range)
    }

    override fun delete(path: String, filename: String, client: HDFSClient) {
        val remotePath = concatRemotePath(path, filename, client)
        if (client.fileSystem.exists(remotePath)) {
            client.fileSystem.delete(remotePath, false)
        }
    }

    override fun exist(path: String, filename: String, client: HDFSClient): Boolean {
        val remotePath = concatRemotePath(path, filename, client)
        return client.fileSystem.exists(remotePath)
    }

    private fun concatRemotePath(path: String, filename: String, client: HDFSClient): Path {
        val childPath = Path(path, filename)
        val parentPath = client.workingPath
        return Path.mergePaths(parentPath, childPath)
    }

    override fun onCreateClient(credentials: HDFSCredentials): HDFSClient {
        val configuration = Configuration()
        val username = credentials.user
        var url = credentials.url
        val workingPath = Path(URI.create(credentials.workingDirectory))
        if (credentials.clusterMode) {
            url = "hdfs://${credentials.clusterName}"
            configuration["fs.defaultFS"] = url
            configuration["dfs.replication"] = 2.toString()
            configuration["dfs.nameservices"] = credentials.clusterName
            configuration["dfs.ha.namenodes.${credentials.clusterName}"] =
                credentials.nameNodeMap.keys.joinToString(separator = ",")
            credentials.nameNodeMap.forEach { (node, address) ->
                configuration["dfs.namenode.rpc-address.${credentials.clusterName}.$node"] = address
            }
            configuration["dfs.client.failover.proxy.provider.${credentials.clusterName}"] =
                "org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider"
        }
        val fileSystem = FileSystem.get(URI.create(url), configuration, username)
        return HDFSClient(workingPath, fileSystem)
    }
}
