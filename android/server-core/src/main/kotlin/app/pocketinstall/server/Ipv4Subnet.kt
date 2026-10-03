package app.pocketinstall.server

import java.net.Inet4Address
import java.net.InetAddress

class Ipv4Subnet(address: Inet4Address, val prefix: Int) {
    init { require(prefix in 0..32) }
    private val mask = if (prefix == 0) 0L else (0xffffffffL shl (32 - prefix)) and 0xffffffffL
    private val network = number(address) and mask
    fun contains(address: InetAddress): Boolean = address is Inet4Address && number(address) and mask == network
    val isPrivate: Boolean get() = isPrivateNumber(network) && isPrivateNumber(network or (mask xor 0xffffffffL))
    override fun toString(): String = "${(24 downTo 0 step 8).joinToString(".") { ((network shr it) and 255).toString() }}/$prefix"

    companion object {
        private fun number(address: Inet4Address): Long = address.address.fold(0L) { n, byte -> (n shl 8) or (byte.toLong() and 255) }
        private fun isPrivateNumber(n: Long): Boolean = n shr 24 == 10L || n shr 20 == 0xac1L || n shr 16 == 0xc0a8L
        fun isPrivate(address: InetAddress): Boolean = address is Inet4Address && isPrivateNumber(number(address))
    }
}
