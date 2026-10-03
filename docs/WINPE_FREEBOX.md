# Charger Windows PE depuis PocketInstall et une Freebox

Cette étape ouvre une console Windows PE x64 en RAM. Elle ne contient pas
l'installateur complet Windows et ne lance aucune commande de partitionnement.
WinPE peut détecter et monter les disques présents. Le démarrage WinPE physique
reste à valider ; le succès du petit POC EFI ne valide pas cette nouvelle chaîne.

## Préparer le bundle sur un PC Windows disponible

Installer l'ADK Microsoft (Deployment Tools) et son add-on Windows PE de même
version. Dans PowerShell administrateur, depuis le dépôt :

```powershell
.\scripts\Build-WinPE.ps1 -WorkDirectory C:\PocketInstall-WinPE -OutputDirectory C:\PocketInstall-Bundle
```

Il faut une machine Windows de préparation ; la Freebox et Android ne fabriquent
pas eux-mêmes l'image Microsoft. Les scripts refusent les dossiers de sortie
existants. Les fichiers Microsoft ne sont pas inclus dans le dépôt.

Récupérer `wimboot` et un `snponly.efi` **x64 standard avec console iPXE** depuis
les sources officielles iPXE. Conserver leurs licences et vérifier les SHA-256
attendus via une source fiable ; un hash calculé sur le téléchargement seul
ne prouve pas son origine. Le parcours ci-dessous utilise un chargeur non signé
et ne met pas en place une chaîne Secure Boot signée.

```powershell
.\scripts\Package-WinPE.ps1 -BundleDirectory C:\PocketInstall-Bundle `
  -Wimboot C:\Loaders\wimboot -WimbootSha256 HASH_ATTENDU_WIMBOOT `
  -Ipxe C:\Loaders\snponly.efi -IpxeSha256 HASH_ATTENDU_IPXE `
  -OutputZip C:\PocketInstall-WinPE.zip
```

L'archive contient uniquement `boot.wim`, `boot.sdi`, `BCD`, `bootmgfw.efi`,
`wimboot`, `snponly.efi` et `manifest.json`, sans sous-dossier. Limite totale
décompressée : 2 Gio ; les fichiers autres que le WIM sont limités à 16 Mio.
Prévoir la place pour le ZIP et son contenu décompressé sur le téléphone.

## Téléphone et Freebox

1. Copier le ZIP sur le téléphone. Arrêter la session actuelle, choisir le réseau
   LAN, puis **Importer le bundle WinPE**. L'import vérifie les tailles, les
   SHA-256 et les en-têtes WIM/EFI x64. Le manifeste n'est pas une signature.
2. Appuyer sur **Démarrer WinPE · Freebox**. Garder le téléphone sur le même LAN.
3. Copier l'URL du chargeur affichée, la télécharger dans le navigateur, puis
   uploader le fichier sous le nom exact `snponly.efi` dans le dossier TFTP
   Freebox, par exemple `/Disque dur/PocketInstall`.
4. Dans Freebox OS, garder cette racine TFTP et régler DHCP :

| Champ | Valeur pour le réseau testé |
|---|---|
| Serveur TFTP | `192.168.0.254` (Freebox) |
| Fichier de démarrage | `snponly.efi` |
| Serveur HTTP WinPE | `192.168.0.35:8080` si le téléphone conserve cette IP |

Le serveur TFTP de la Freebox sert le chargeur ; le téléphone sert les fichiers
WinPE par HTTP. Aucune redirection de port Internet n'est nécessaire.

5. Démarrer le PC en Ethernet, **UEFI PXE IPv4**. Adapter Secure Boot au chargeur
   non signé. Dans iPXE, appuyer sur **Ctrl+B** puis saisir :

```text
dhcp
chain URL_DU_SCRIPT_AFFICHEE_DANS_L_APPLI
```

Utiliser l'URL exacte de la session active, terminée par `/winpe/boot.ipxe`.
Elle change à chaque session. Le script charge wimboot puis le gestionnaire EFI,
le BCD, le SDI et le WIM. En cas d'échec, il revient à une console iPXE.

6. Le critère de succès est la console **PocketInstall boot successful (WinPE)**
   visible sur le PC après `wpeinit`. Les journaux HTTP seuls ne prouvent pas
   l'exécution. La console reste ouverte : `wpeutil shutdown` éteint le PC.
   Si le réseau manque sous WinPE, préparer le bundle avec les pilotes NIC ADK.

La session HTTP s'arrête après 30 minutes ou perte du réseau. L'environnement
déjà chargé en RAM continue à fonctionner. Restaurer les paramètres DHCP de boot
Freebox après l'essai. Les bundles importés précédemment restent dans le stockage
privé pour éviter de modifier des fichiers servis ; effacer les données de
l'application les supprime tous.

## Sources et validation

- [iPXE : démarrer WinPE avec wimboot](https://ipxe.org/howto/winpe)
- [iPXE : téléchargement des chargeurs](https://ipxe.org/download)
- [Microsoft : créer un support Windows PE](https://learn.microsoft.com/windows-hardware/manufacture/desktop/winpe-create-usb-bootable-drive)
- [Microsoft : monter et personnaliser WinPE](https://learn.microsoft.com/windows-hardware/manufacture/desktop/winpe-mount-and-customize)

Les tests JVM couvrent l'import et le protocole HTTP/TFTP. La préparation ADK,
la signature firmware et l'exécution du WIM doivent être testées avec de vrais
fichiers Microsoft avant de déclarer cette étape prête pour une installation.
