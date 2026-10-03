# PocketInstall 0.2.0 — WinPE preview

Cette version ajoute le chargement de Windows PE x64 depuis le téléphone,
avec un bundle prêt à importer construit sur GitHub. Aucun autre PC Windows
n'est nécessaire pour préparer le bundle fourni dans cette release privée.

## Fichiers à télécharger sur le téléphone

- `PocketInstall-0.2.0-winpe-preview-debug.apk` : application Android.
- `PocketInstall-WinPE-x64.zip` : bundle à importer dans l'application.
- `snponly.efi` : chargeur à uploader dans le dossier TFTP Freebox.
- `WINPE-GUIDE.md` : procédure Freebox et iPXE.

Conserver le serveur TFTP Freebox (`192.168.0.254` sur le réseau testé) et choisir
`snponly.efi` comme fichier de démarrage DHCP. Lancer la session WinPE dans
l'application, démarrer le PC en UEFI PXE IPv4, puis saisir `dhcp` et la commande
`chain` avec l'URL du script affichée par le téléphone.

WinPE ouvre une console. Aucune installation, réparation ou commande de
partitionnement n'est lancée. WinPE peut détecter et monter les disques.
Le chargement physique WinPE reste à valider. L'installateur complet et l'image
Windows ne sont pas encore inclus ; cette release prépare cette prochaine étape.

Chargeur iPXE non signé : adapter la politique Secure Boot au test.
SHA-256, rapports de tests et sources iPXE/wimboot joints. L'APK utilise une
signature debug ; Android peut refuser sa mise à jour si la signature diffère.
