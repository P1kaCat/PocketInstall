# PXE feasibility on non-root Android

Historical research summary.

Initial investigation distinguished DHCP address allocation, proxy-DHCP, TFTP loader delivery and actual EFI execution. Android may not permit binding UDP 69, while PXE firmware expects that port. A router or external Linux relay is therefore needed when the phone falls back to 6969. Starting a second DHCP address server is not an acceptable default. iPXE handoff is now implemented; early research assumptions are not current feature claims.

## Original primary references

- https://www.rfc-editor.org/rfc/rfc1350
- https://www.rfc-editor.org/rfc/rfc2347
- https://www.rfc-editor.org/rfc/rfc2348
- https://www.rfc-editor.org/rfc/rfc2349
- https://www.rfc-editor.org/rfc/rfc4578
- https://docs.kernel.org/networking/ip-sysctl.html
- https://source.android.com/docs/core/ota/modular-system/tethering
- https://www.asus.com/me-en/motherboards-components/motherboards/prime/prime-b365m-k/techspec/
- https://thekelleys.org.uk/dnsmasq/docs/dnsmasq-man.html
- https://ipxe.org/howto/chainloading
- https://ipxe.org/secboot
