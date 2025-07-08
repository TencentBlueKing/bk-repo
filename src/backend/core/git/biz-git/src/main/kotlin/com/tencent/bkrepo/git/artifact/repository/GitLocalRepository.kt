/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.git.artifact.repository

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.resolve.file.chunk.ChunkedFileOutputStream
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.artifactStream
import com.tencent.bkrepo.git.artifact.GitContentArtifactInfo
import com.tencent.bkrepo.git.constant.GitMessageCode
import com.tencent.bkrepo.git.internal.CodeRepositoryResolver
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.PathFilter
import org.springframework.stereotype.Component

@Component
class GitLocalRepository : LocalRepository() {

    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        with(context) {
            val node = nodeService.getNodeDetail(artifactInfo)
            val responseName = artifactInfo.getResponseName()
            storageManager.loadArtifactInputStream(node, storageCredentials)?.let {
                return ArtifactResource(it, responseName, node, ArtifactChannel.PROXY, useDisposition)
            }
            val gitContentArtifactInfo = artifactInfo as GitContentArtifactInfo
            val db = CodeRepositoryResolver.open(projectId, repoName, storageCredentials)
            val rw = RevWalk(db)
            val commitId = ObjectId.fromString(gitContentArtifactInfo.commitId)
            val commit = rw.parseCommit(commitId)
            val tree = commit.tree
            val tw = TreeWalk(db)
            try {
                tw.addTree(tree)
                tw.isRecursive = true
                tw.filter = PathFilter.create(gitContentArtifactInfo.path)
                if (!tw.next()) {
                    throw ErrorCodeException(
                        GitMessageCode.GIT_PATH_NOT_FOUND,
                        gitContentArtifactInfo.path,
                        gitContentArtifactInfo.commitId
                    )
                }
                val objectId = tw.getObjectId(0)
                val objectLoader = db.open(objectId)
                val artifactFile = ArtifactFileFactory.buildChunked()
                val output = ChunkedFileOutputStream(artifactFile)
                objectLoader.copyTo(output)
                artifactFile.finish()
                val nodeCreateRequest = NodeCreateRequest(
                    projectId = projectId,
                    repoName = repoName,
                    folder = false,
                    fullPath = artifactInfo.getArtifactFullPath(),
                    size = artifactFile.getSize(),
                    sha256 = artifactFile.getFileSha256(),
                    md5 = artifactFile.getFileMd5(),
                    overwrite = true,
                    operator = userId
                )
                val nodeDetail = storageManager.storeArtifactFile(
                    nodeCreateRequest,
                    artifactFile,
                    storageCredentials
                )
                val inputStream = artifactFile.getInputStream().artifactStream(Range.full(artifactFile.getSize()))
                return ArtifactResource(
                    inputStream,
                    responseName,
                    nodeDetail,
                    ArtifactChannel.PROXY,
                    useDisposition
                )
            } finally {
                tw.close()
                rw.close()
            }
        }
    }
}
