# PocketInstall 0.2.0 — WinPE preview

Adds import of a separately prepared PocketInstall-WinPE-x64.zip and HTTP delivery of an iPXE/wimboot WinPE environment. Import validates required files and generated routes before readiness. WinPE runs in RAM; the proof workflow does not request installation or formatting.

Configure router DHCP/TFTP manually and keep the phone server reachable on the same LAN. A WIM download alone is not a boot confirmation. Microsoft components remain subject to their own terms.
