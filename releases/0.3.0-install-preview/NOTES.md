# PocketInstall 0.3.0 — Windows installation preview

Adds official x64 Windows image import, Windows 10/11 Home/Pro selection, optional debloat and a WinPE deployment plan. Disk selection and erasure confirmation remain local to the PC. Deployment uses GPT, DISM, BCDBoot and WinRE registration.

This is a destructive clean install. Image transfer happens after partitioning; a failed network can leave the target disk erased. Boot delivery, WinPE startup, Windows image application and installed Windows startup are distinct states. At this release's publication, full physical deployment/OOBE and all editions were not confirmed.
