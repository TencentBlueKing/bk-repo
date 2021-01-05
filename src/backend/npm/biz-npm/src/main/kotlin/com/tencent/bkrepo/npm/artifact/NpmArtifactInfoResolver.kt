/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.npm.artifact

import com.tencent.bkrepo.common.artifact.resolve.path.ArtifactInfoResolver
import com.tencent.bkrepo.common.artifact.resolve.path.Resolver
<<<<<<< HEAD
=======
import com.tencent.bkrepo.npm.constants.FILE_SEPARATOR
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.servlet.http.HttpServletRequest

@Resolver(NpmArtifactInfo::class)
class NpmArtifactInfoResolver : ArtifactInfoResolver {
    override fun resolve(
        projectId: String,
        repoName: String,
        artifactUri: String,
        request: HttpServletRequest
    ): NpmArtifactInfo {
<<<<<<< HEAD
        return NpmArtifactInfo(projectId, repoName, artifactUri)
    }

    companion object {
=======
        val uri = URLDecoder.decode(request.requestURI, characterEncoding)

        val pathElements = LinkedList<String>()
        val stringTokenizer = StringTokenizer(uri.substringBefore(DELIMITER), FILE_SEPARATOR)
        while (stringTokenizer.hasMoreTokens()) {
            pathElements.add(stringTokenizer.nextToken())
        }
        if (pathElements.size < 2) {
            logger.debug(
                "Cannot build NpmArtifactInfo from '{}'. The pkgName are unreadable.",
                uri
            )
            return NpmArtifactInfo(projectId, repoName, artifactUri)
        }
        val isScope = pathElements[2].contains(AT)
        val scope = if (isScope) pathElements[2] else StringPool.EMPTY
        val pkgName = if (isScope){
            require(pathElements.size > 3) {
                val message = "npm resolver artifact error with requestURI [${request.requestURI}]"
                logger.error(message)
                throw IllegalArgumentException(message)
            }
            pathElements[3]
        } else pathElements[2]

        val version =
            if (pathElements.size > 4) pathElements[4] else (if (!isScope && pathElements.size == 4) pathElements[3] else StringPool.EMPTY)
        return NpmArtifactInfo(projectId, repoName, artifactUri, scope, pkgName, version)
    }

    companion object {
        const val characterEncoding: String = "utf-8"
        const val DELIMITER: String = "/-rev"
        const val AT: Char = '@'
>>>>>>> 95b43eea8c90c411aa9a5cae9e282ea1496e56b4
        val logger: Logger = LoggerFactory.getLogger(NpmArtifactInfo::class.java)
    }
}
