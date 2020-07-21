package com.tencent.bkrepo.common.service.util

import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.net.UnknownHostException

object NetUtils {

    private const val LOCAL_HOST = "127.0.0.1"

    fun getHostName(): String {
        return try {
            val address = InetAddress.getLocalHost()
            val hostname = address.hostName
            if (hostname.isEmpty()) address.toString() else hostname
        } catch (ignore: UnknownHostException) {
            LOCAL_HOST
        }
    }

    fun getHostIp(): String {
        return try {
            val address = getLocalHostLanAddress()
            val hostAddress = address.hostAddress
            if (hostAddress.isEmpty()) address.toString() else hostAddress
        } catch (ignore: UnknownHostException) {
            LOCAL_HOST
        }
    }

    private fun getLocalHostLanAddress(): InetAddress {
        try {
            var candidateAddress: InetAddress? = null
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val netInterface = interfaces.nextElement() as NetworkInterface
                val addresses = netInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val netAddress = addresses.nextElement() as InetAddress
                    if (!netAddress.isLoopbackAddress) {
                        if (netAddress.isSiteLocalAddress) {
                            return netAddress
                        } else if (candidateAddress == null) {
                            candidateAddress = netAddress
                        }
                    }
                }
            }
            return candidateAddress ?: InetAddress.getLocalHost()
        } catch (exception: SocketException) {
            val unknownHostException = UnknownHostException("Failed to determine LAN address: $exception")
            unknownHostException.initCause(exception)
            throw unknownHostException
        } catch (unknownHostException: UnknownHostException) {
            throw unknownHostException
        }
    }
}
