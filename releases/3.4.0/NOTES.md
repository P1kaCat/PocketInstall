# PocketInstall 3.4.0 — Linux desktop and server

Adds Debian 13 Xfce desktop and standard + SSH server profiles, official boot-file download with SHA-256 verification, automatic PXE using the existing router setup and runtime-based installer progress.

Adds the local Library for removing downloaded copies, automatic GitHub WinPE ZIP import, a refreshed repository and the restrictive contribution license. Preserves the 3.3.0 logo and six app languages; new Linux/library diagnostics are partly French.

Linux does not require WinPE. The PC downloads Debian packages online and chooses account/disk interactively. A server profile without a graphical desktop is lightweight, not universally optimal.

Validation covered both profiles' routes and actual Debian installer startup in a diskless VM. A full physical Linux installation was not tested. Existing Freebox settings remain valid; Windows continues to use the 3.2.0 WinPE bundle.
