package com.tencent.bkrepo.common.artifact.resolver

import com.tencent.bkrepo.common.artifact.api.ArtifactFileItem
import java.io.File
import org.apache.commons.io.FileCleaningTracker

/**
 * ArtifactFileItem工厂方法
 *
 * @author: carrypan
 * @date: 2019/10/30
 */
class ArtifactFileItemFactory(
    private val sizeThreshold: Int = DEFAULT_SIZE_THRESHOLD,
    private val repository: File = File(System.getProperty("java.io.tmpdir")),
    private val tracker: FileCleaningTracker? = null
) {

    fun build(): ArtifactFileItem {
        val fileItem = ArtifactFileItem(repository, sizeThreshold)
        tracker?.track(fileItem.getTempFile(), fileItem)
        return fileItem
    }

    companion object {
        /**
         * The default threshold above which uploads will be stored on disk.
         */
        private const val DEFAULT_SIZE_THRESHOLD = 1024 * 1024
    }
}
