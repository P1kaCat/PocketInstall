package app.pocketinstall.server

import java.net.Inet4Address
import java.net.InetAddress

/** Conservative name heuristic, not proof of tethering or firmware support. */
object UsbLinkPolicy {
    private val names = Regex("^(?:rndis|usb|ncm)[0-9]+$")
    fun eligible(name: String, up: Boolean, loopback: Boolean, address: InetAddress, prefix: Int): Boolean {
        if (!up || loopback || !names.matches(name) || address !is Inet4Address ||
            prefix !in 0..32 || !Ipv4Subnet.isPrivate(address)) return false
        return Ipv4Subnet(address, prefix).isPrivate
    }
}
