package app.pocketinstall.server

import java.net.InetAddress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbLinkPolicyTest {
    private fun ip(value: String) = InetAddress.getByName(value)

    @Test fun privateUsbAddressesAreEligible() {
        for (name in listOf("rndis0", "usb0", "ncm1"))
            assertTrue(UsbLinkPolicy.eligible(name, true, false, ip("192.168.42.129"), 24))
    }

    @Test fun wifiMobileAndVpnInterfacesAreExcluded() {
        for (name in listOf("wlan0", "rmnet0", "tun0", "eth0", "rndis0:1", "usb"))
            assertFalse(UsbLinkPolicy.eligible(name, true, false, ip("192.168.42.129"), 24))
    }

    @Test fun publicAndIpv6AddressesAreExcluded() {
        for (address in listOf("8.8.8.8", "127.0.0.1", "169.254.42.1", "::1"))
            assertFalse(UsbLinkPolicy.eligible("rndis0", true, false, ip(address), 24))
    }

    @Test fun disconnectedAndLoopbackInterfacesAreExcluded() {
        assertFalse(UsbLinkPolicy.eligible("rndis0", false, false, ip("192.168.42.129"), 24))
        assertFalse(UsbLinkPolicy.eligible("rndis0", true, true, ip("192.168.42.129"), 24))
    }

    @Test fun prefixesMustKeepTheEntireSubnetPrivate() {
        for (prefix in listOf(-1, 0, 1, 8, 33))
            assertFalse(UsbLinkPolicy.eligible("rndis0", true, false, ip("192.168.42.129"), prefix))
        assertFalse(UsbLinkPolicy.eligible("usb0", true, false, ip("172.16.1.1"), 11))
        assertTrue(UsbLinkPolicy.eligible("usb0", true, false, ip("172.16.1.1"), 12))
    }
}
