# PocketInstall 0.2.1 · WinPE automatique avec Freebox

- Script boot.ipxe généré avant publication de son URL ; routes et fichiers vérifiés par HTTP à l'import et au démarrage.
- Point d'entrée LAN stable /boot.ipxe : le token de la session reste transparent.
- Nouveau snponly.efi : DHCP automatique, lecture de pocketinstall.ipxe sur le TFTP Freebox, puis chargement WinPE sans commandes sur le PC.
- Export de pocketinstall.ipxe depuis Android ; configuration Freebox manuelle une fois, avec réservation de l'IP du téléphone.
- Progression par PC et transferts ; le téléchargement seul n'est pas une preuve de boot. Le nouveau bundle PowerShell envoie un signal depuis WinPE après wpeinit.
- L'import ne démarre pas le serveur. L'environnement n'est déclaré validé qu'après validation des fichiers et des routes. Les commandes et journaux sont dans les sections de diagnostic.
- Validation en VM sans disque : OVMF PXE → iPXE → serveur Kotlin → wimboot → signal exécuté dans WinPE. Voir l'artefact winpe-vm-evidence du workflow ; la publication dépend de ce test.

Installer le nouvel APK, importer le nouveau PocketInstall-WinPE-x64.zip, remplacer snponly.efi sur la Freebox et y ajouter pocketinstall.ipxe exporté par l'application. Garder le serveur TFTP DHCP sur l'IP Freebox, fichier snponly.efi. Détails : WINPE-GUIDE.md.

WinPE fournit une console. Windows Setup et l'image d'installation Windows ne sont pas inclus. Aucun formatage ni installation automatique. Secure Boot doit accepter le chargeur iPXE non signé. Le matériel réel et ses pilotes réseau restent à valider sur le PC.
