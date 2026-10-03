# PocketInstall 0.1.3-poc — UEFI PXE IPv4

- Mode **PXE IPv4 · Ethernet · expérimental** dans l'application Android.
- TFTP en lecture seule : chargement direct du POC EFI depuis PXE, sans HTTP Boot
  dans le BIOS et sans iPXE. Options blksize/tsize/timeout, retransmissions, logs.
- Essai UDP 69 puis repli 6969 avec diagnostic explicite. Un port haut n'est pas
  joignable par PXE standard sans équipement relais / redirection configuré.
- Configuration DHCP de boot requise ; l'application ne configure pas la box et
  ne remplace pas son DHCP. Pas de serveur DHCP ou proxy-DHCP Android ajouté.
- Relais Linux optionnel : récupère uniquement le POC depuis le téléphone,
  vérifie SHA256, prépare dnsmasq proxy-DHCP/TFTP ciblé sur une seule MAC.
- Test QEMU/OVMF PXE natif sans disque, PCAP vérifiant RRQ/contenu/ACK,
  message exécuté et arrêt. Le serveur VM est celui de référence QEMU/libslirp ;
  le TFTP Kotlin de l'APK est vérifié séparément par les tests UDP.

## Conditions de test

PC UEFI **x64 PXE IPv4** en Ethernet. Téléphone sur le même LAN. **DHCP configurable
ou relais externe indispensable**. Le relais nécessite un appareil supplémentaire :
ce n'est pas un PXE universel autonome depuis un seul téléphone Android non rooté.
Suivre [docs/PXE.md](https://github.com/P1kaCat/PocketInstall/blob/main/docs/PXE.md).

Le POC affiche `PocketInstall boot successful`, puis éteint le PC. Il n'accède
à aucun disque. Aucune installation / réparation Windows dans cette version.
EFI **non signé** ; ne pas confondre signature APK et Secure Boot. Aucun couple
PC/téléphone réel n'est certifié, y compris ASUS PRIME B365M-K. USB PXE non pris
en charge ; le mode USB HTTP existant conserve ses limites expérimentales.

APK **debug**, Android 8+. Les clés debug générées sur des runners différents
peuvent empêcher une mise à jour directe ; désinstaller l'ancien POC si Android
signale une signature différente. Pas de compte, root ou données utilisateur.

Licence PocketInstall Personal Use License 1.0 : modifications privées personnelles
autorisées, redistribution soumise à accord écrit. Notices tierces conservées.
Aucun binaire Microsoft/iPXE fourni.
