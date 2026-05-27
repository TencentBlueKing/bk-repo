package com.tencent.bkrepo.repository.pojo.ilnet

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class IlnetApiResponse<T>(
    val code: Int,
    val msg: String,
    @JsonProperty("elapsed_ms")
    val elapsedMs: Int? = null,
    val data: T? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LinkTrafficData(
    val terminal: TerminalInfo? = null,
    @JsonProperty("access_device")
    val accessDevice: AccessDevice? = null,
    val links: List<LinkSegment>? = null,
    val topology: List<TopologyNode>? = null,
    val traffic: List<TrafficLayer>? = null,
    @JsonProperty("overall_status")
    val overallStatus: String? = null,
    @JsonProperty("threshold_percent")
    val thresholdPercent: Double? = null,
    val warnings: List<String>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TerminalInfo(
    val mac: String? = null,
    val ip: String? = null,
    val username: String? = null,
    @JsonProperty("link_speed")
    val linkSpeed: String? = null,
    val city: String? = null,
    val building: String? = null,
    @JsonProperty("auth_time")
    val authTime: String? = null,
    @JsonProperty("auth_source")
    val authSource: String? = null,
    @JsonProperty("auth_type")
    val authType: String? = null,
    @JsonProperty("ap_name")
    val apName: String? = null,
    val ssid: String? = null,
    @JsonProperty("ac_vendor")
    val acVendor: String? = null,
    @JsonProperty("ap_health")
    val apHealth: ApHealth? = null,
    @JsonProperty("client_health")
    val clientHealth: ClientHealth? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApHealth(
    val score: Double? = null,
    val level: String? = null,
    val threshold: Double? = null,
    @JsonProperty("sampled_at")
    val sampledAt: String? = null,
    @JsonProperty("ap_name")
    val apName: String? = null,
    @JsonProperty("ap_ip")
    val apIp: String? = null,
    @JsonProperty("ap_mac")
    val apMac: String? = null,
    @JsonProperty("client_count_24g")
    val clientCount24g: Double? = null,
    @JsonProperty("client_count_5g")
    val clientCount5g: Double? = null,
    @JsonProperty("utilization_24g")
    val utilization24g: Double? = null,
    @JsonProperty("utilization_5g")
    val utilization5g: Double? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ClientHealth(
    val score: Double? = null,
    val level: String? = null,
    val threshold: Double? = null,
    @JsonProperty("sampled_at")
    val sampledAt: String? = null,
    val rssi: Double? = null,
    val snr: Double? = null,
    val ssid: String? = null,
    @JsonProperty("ap_mac")
    val apMac: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AccessDevice(
    val name: String? = null,
    val ip: String? = null,
    val port: String? = null,
    @JsonProperty("ap_name")
    val apName: String? = null,
    @JsonProperty("ap_serial")
    val apSerial: String? = null,
    @JsonProperty("ap_uplink_port")
    val apUplinkPort: String? = null,
    val ssid: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LinkSegment(
    val layer: String? = null,
    val label: String? = null,
    val status: String? = null,
    val utilization: Double? = null,
    @JsonProperty("bandwidth_mbps")
    val bandwidthMbps: Int? = null,
    @JsonProperty("peak_kbps")
    val peakKbps: Double? = null,
    val reason: String? = null,
    val ports: List<LinkPort>? = null,
    val ap: ApHealth? = null,
    val client: ClientHealth? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LinkPort(
    val role: String? = null,
    val device: String? = null,
    val port: String? = null,
    val status: String? = null,
    val utilization: Double? = null,
    @JsonProperty("bandwidth_mbps")
    val bandwidthMbps: Int? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TopologyNode(
    val hop: Int? = null,
    val layer: String? = null,
    val role: String? = null,
    @JsonProperty("device_name")
    val deviceName: String? = null,
    @JsonProperty("device_ip")
    val deviceIp: String? = null,
    @JsonProperty("local_port_on_lower")
    val localPortOnLower: String? = null,
    @JsonProperty("peer_port")
    val peerPort: String? = null,
    @JsonProperty("is_aggregate")
    val isAggregate: Boolean? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TrafficLayer(
    val layer: String? = null,
    val role: String? = null,
    @JsonProperty("device_name")
    val deviceName: String? = null,
    @JsonProperty("device_ip")
    val deviceIp: String? = null,
    val port: String? = null,
    @JsonProperty("port_vlan")
    val portVlan: String? = null,
    @JsonProperty("port_band_mbps")
    val portBandMbps: Int? = null,
    @JsonProperty("is_aggregate")
    val isAggregate: Boolean? = null,
    @JsonProperty("peer_device")
    val peerDevice: String? = null,
    @JsonProperty("remote_port")
    val remotePort: String? = null,
    @JsonProperty("matched_port")
    val matchedPort: String? = null,
    val series: List<TrafficSeriesPoint>? = null,
    val summary: TrafficSummary? = null,
    val unit: String? = null,
    val error: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TrafficSeriesPoint(
    val time: String? = null,
    @JsonProperty("in_traffic")
    val inTraffic: Double? = null,
    @JsonProperty("out_traffic")
    val outTraffic: Double? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TrafficSummary(
    @JsonProperty("in_traffic")
    val inTraffic: TrafficMetrics? = null,
    @JsonProperty("out_traffic")
    val outTraffic: TrafficMetrics? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TrafficMetrics(
    val max: Double? = null,
    val avg: Double? = null,
    val p95: Double? = null,
    val count: Int? = null,
)

typealias LinkTrafficResponse = IlnetApiResponse<LinkTrafficData>
