package app.pocketinstall

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import app.pocketinstall.server.Ipv4Subnet
import java.net.Inet4Address

data class LanCandidate(val network: Network, val address: Inet4Address, val prefix: Int, val label: String)

object LanNetwork {
    fun candidates(context: Context): List<LanCandidate> {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        return manager.allNetworks.flatMap { network ->
            val caps = manager.getNetworkCapabilities(network) ?: return@flatMap emptyList()
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                !(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)))
                return@flatMap emptyList()
            val properties = manager.getLinkProperties(network) ?: return@flatMap emptyList()
            properties.linkAddresses.mapNotNull { link ->
                val ip = link.address as? Inet4Address ?: return@mapNotNull null
                if (!Ipv4Subnet.isPrivate(ip) || !Ipv4Subnet(ip, link.prefixLength).isPrivate) return@mapNotNull null
                val type = if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) "Wi-Fi" else "Ethernet"
                LanCandidate(network, ip, link.prefixLength, "$type · ${properties.interfaceName ?: "LAN"}")
            }
        }
    }
}
