#!/usr/bin/env python3
"""Safety checks for PXE setup inputs and the generated proxy-only configuration."""
import ipaddress
from pathlib import Path
import unittest
from prepare_pxe_relay import configuration, validate_network, validate_url


class RelayTest(unittest.TestCase):
    def test_exact_phone_url(self):
        host, path = validate_url("http://192.168.1.42:8080/" + "a" * 32 + "/bootx64.efi")
        self.assertEqual(str(host), "192.168.1.42")
        self.assertTrue(path.endswith("/bootx64.efi"))

    def test_rejects_external_urls_redirect_targets_and_other_resources(self):
        path = "/" + "a" * 32 + "/bootx64.efi"
        for value in ("http://8.8.8.8:8080" + path, "http://127.0.0.1:8080" + path,
                      "http://example.com:8080" + path, "https://192.168.1.42:8080" + path,
                      "http://user@192.168.1.42:8080" + path,
                      "http://192.168.1.42:8080" + path + "?other=1",
                      "http://192.168.1.42:8080" + path + "#fragment",
                      "http://192.168.1.42:8080/bootx64.efi",
                      "http://192.168.1.42:8080/" + "a" * 32 + "/install.wim"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                validate_url(value)

    def test_same_lan_and_input_injection_checks(self):
        phone = ipaddress.IPv4Address("192.168.1.42")
        args = ("192.168.1.10", phone, 24, "enp3s0", "52:54:00:12:34:56")
        self.assertEqual(str(validate_network(*args)), "192.168.1.0/24")
        for bad in (("192.168.2.10", phone, 24, "enp3s0", args[4]),
                    (args[0], phone, 0, "enp3s0", args[4]),
                    (args[0], phone, 24, "enp3s0\nport=53", args[4]),
                    (args[0], phone, 24, "enp3s0", "ff:ff:ff:ff:ff:ff"),
                    (args[0], phone, 24, "enp3s0", "00:00:00:00:00:00")):
            with self.subTest(args=bad), self.assertRaises(ValueError):
                validate_network(*bad)

    def test_proxy_only_one_mac_and_both_uefi_architecture_codes(self):
        config = configuration("192.168.1.10", ipaddress.IPv4Network("192.168.1.0/24"),
                               "enp3s0", "52:54:00:12:34:56", Path("/tmp/proof/tftp"),
                               "a" * 32 + "/bootx64.efi")
        self.assertIn("dhcp-range=192.168.1.0,proxy,255.255.255.0", config)
        self.assertIn("dhcp-ignore=tag:!pocketinstall-target", config)
        self.assertIn("BC_EFI", config)
        self.assertIn("x86-64_EFI", config)
        self.assertNotIn("dhcp-authoritative", config)
        self.assertNotIn("dhcp-range=192.168.1.100", config)


if __name__ == "__main__":
    unittest.main()
