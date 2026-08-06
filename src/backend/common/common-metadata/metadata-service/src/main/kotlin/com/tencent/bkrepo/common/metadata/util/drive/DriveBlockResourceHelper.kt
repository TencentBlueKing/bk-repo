package com.tencent.bkrepo.common.metadata.util.drive

import com.tencent.bkrepo.common.metadata.model.drive.TDriveBlockNode
import com.tencent.bkrepo.common.storage.pojo.RegionResource

object DriveBlockResourceHelper {

    fun toRegionResource(blockNode: TDriveBlockNode): RegionResource {
        return RegionResource(
            digest = blockNode.sha256,
            pos = blockNode.startPos,
            size = blockNode.size,
            off = 0,
            len = blockNode.size,
        )
    }
}
