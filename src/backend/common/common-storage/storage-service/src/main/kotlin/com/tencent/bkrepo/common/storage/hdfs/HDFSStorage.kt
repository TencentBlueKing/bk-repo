package com.tencent.bkrepo.common.storage.hdfs

import com.tencent.bkrepo.common.storage.core.AbstractFileStorage
import com.tencent.bkrepo.common.storage.credentials.HDFSCredentials
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import java.io.File
import java.net.URI

/**
 *
 * @author: carrypan
 * @date: 2020/1/13
 */
open class HDFSStorage : AbstractFileStorage<HDFSCredentials, HDFSClient>() {

    override fun store(path: String, filename: String, file: File, client: HDFSClient) {
        val localPath = Path(file.absolutePath)
        val hdfsPath = Path(path, filename)
        client.fileSystem.copyFromLocalFile(localPath, hdfsPath)
    }

    override fun load(path: String, filename: String, received: File, client: HDFSClient): File? {
        val localPath = Path(received.absolutePath)
        val hdfsPath = Path(path, filename)
        client.fileSystem.copyToLocalFile(hdfsPath, localPath)
        return received
    }

    override fun delete(path: String, filename: String, client: HDFSClient) {
        val hdfsPath = Path(path, filename)
        client.fileSystem.deleteOnExit(hdfsPath)
    }

    override fun exist(path: String, filename: String, client: HDFSClient): Boolean {
        val hdfsPath = Path(path, filename)
        return client.fileSystem.exists(hdfsPath)
    }

    override fun onCreateClient(credentials: HDFSCredentials): HDFSClient {
        val configuration = Configuration()
        val username = credentials.user
        var url = credentials.url
        if(credentials.clusterMode) {
            url = "hdfs://${credentials.clusterName}"
            configuration["fs.defaultFS"] = url
            configuration["dfs.nameservices"] = credentials.clusterName
            configuration["dfs.ha.namenodes.${credentials.clusterName}"] = credentials.nameNodeMap.keys.joinToString(separator = ",")
            credentials.nameNodeMap.forEach { (node, address) ->
                configuration["dfs.namenode.rpc-address.${credentials.clusterName}.$node"] = address
            }
            configuration["dfs.client.failover.proxy.provider.${credentials.clusterName}"] = "org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider"
            configuration["fs.hdfs.impl"] = "org.apache.hadoop.hdfs.DistributedFileSystem"
        }
        val fileSystem = FileSystem.get(URI.create(url), configuration, username)
        return HDFSClient(fileSystem)
    }

    override fun getDefaultCredentials() = storageProperties.hdfs
}