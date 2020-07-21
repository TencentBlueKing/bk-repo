package com.tencent.bkrepo.common.storage.innercos.endpoint

class RegionEndpointBuilder : EndpointBuilder {

    override fun buildEndpoint(region: String, bucket: String): String {
        return "%s.%s.tencent-cloud.com".format(bucket, formatRegion(region))
    }

    companion object {
        private fun formatRegion(region: String): String? {
            return if (region == "hk" || region == "njc" || region == "gzc") {
                "$region.vod"
            } else if (region == "sh" || region == "sz") {
                "$region.gfp"
            } else {
                region
            }
        }
    }
}
