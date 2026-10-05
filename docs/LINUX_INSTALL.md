# Install Debian

Choose **Linux desktop** for Debian 13 Xfce, or **Linux server** for Debian 13 standard tools and SSH without a graphical desktop. No distribution is universally the most optimized for every server workload.

1. Stop any active server/transfer and choose your profile in Prepare.
2. Tap Download Debian. PocketInstall checks the exact sizes of the kernel, initrd and SHA256SUMS without downloading their bodies.
3. Review the total and official source, then confirm. The files are verified before the environment becomes ready.
4. Start the server and boot the PC in UEFI PXE IPv4 using the existing router setup.
5. Finish account, disk and installation choices on the PC. Internet access is needed to fetch packages.

The displayed boot download is roughly 55 MiB; it is not the full installation size. Package size depends on installer choices. Neither a disk nor a default password is preselected. Windows storage/debloat options do not apply to Linux.

Progress uses actual installer early/late callbacks. A downloaded kernel/initrd is not proof of startup or completion. A completed physical installation and first boot remain to be verified. After phone-session expiry, Debian may continue but callbacks cannot update the app.

Source: [Debian amd64 installer images](https://deb.debian.org/debian/dists/trixie/main/installer-amd64/current/images/). SHA256SUMS verifies transferred files; upstream licensing still applies.
