package app.pocketinstall

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import app.pocketinstall.server.Ipv4Subnet
import app.pocketinstall.server.UsbLinkPolicy
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

data class LanCandidate(
    val network: Network?,
    val address: Inet4Address,
    val prefix: Int,
    val label: String,
    val interfaceName: String
) {
    val id: String get() = "${if (network == null) "usb" else network.networkHandle.toString()}:$interfaceName:${address.hostAddress}/$prefix"
}

object LanNetwork {
    fun candidates(context: Context, usb: Boolean = false): List<LanCandidate> {
        if (usb) return usbCandidates()
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
                val name = properties.interfaceName ?: "LAN"
                LanCandidate(network, ip, link.prefixLength, "$type · $name", name)
            }
        }
    }

    /** Downstream tether interfaces need not be ConnectivityManager Networks. */
    fun usbCandidates(): List<LanCandidate> = runCatching {
        val enumeration = NetworkInterface.getNetworkInterfaces() ?: return@runCatching emptyList()
        Collections.list(enumeration).flatMap { iface ->
            iface.interfaceAddresses.mapNotNull { link ->
                val ip = link.address as? Inet4Address ?: return@mapNotNull null
                val prefix = link.networkPrefixLength.toInt()
                if (!UsbLinkPolicy.eligible(iface.name, iface.isUp, iface.isLoopback, ip, prefix))
                    return@mapNotNull null
                LanCandidate(null, ip, prefix, "USB potentiel · ${iface.name}", iface.name)
            }
        }.distinctBy { it.id }
    }.getOrDefault(emptyList())
}
