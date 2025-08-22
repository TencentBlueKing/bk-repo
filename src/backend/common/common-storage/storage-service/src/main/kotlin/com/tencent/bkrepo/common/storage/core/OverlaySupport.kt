/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.storage.core

import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.ZeroInputStream
import com.tencent.bkrepo.common.artifact.stream.artifactStream
import com.tencent.bkrepo.common.storage.core.overlay.OverlayArtifactFileInputStream
import com.tencent.bkrepo.common.storage.core.overlay.OverlayRangeUtils
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.pojo.RegionResource
import org.slf4j.LoggerFactory

abstract class OverlaySupport : FileBlockSupport() {
    override fun load(
        blocks: List<RegionResource>,
        range: Range,
        storageCredentials: StorageCredentials?
    ): ArtifactInputStream? {
        if (logger.isDebugEnabled) {
            logger.debug("Range: $range, blocks: ${blocks.joinToString()}")
        }
        val ranges = OverlayRangeUtils.build(blocks, range)
        if (ranges.size == 1) {
            return loadResource(ranges.first(), storageCredentials)
        }
        if (ranges.isEmpty()) {
            return null
        }
        return OverlayArtifactFileInputStream(ranges) {
            val input = loadResource(it, storageCredentials)
            check(input != null) { "Block[${it.digest}] miss." }
            input
        }.artifactStream(range)
    }

    private fun loadResource(
        resource: RegionResource,
        storageCredentials: StorageCredentials?
    ): ArtifactInputStream? = if (resource.digest == RegionResource.ZERO_RESOURCE) {
        ZeroInputStream(resource.len)?.artifactStream(Range.full(resource.len))
    } else {
        val range = Range(resource.off, resource.off + resource.len - 1, resource.size)
        load(resource.digest, range, storageCredentials)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OverlaySupport::class.java)
    }
}
