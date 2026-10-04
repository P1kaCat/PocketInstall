# WinPE : étape suivante préparée

**Ces scripts n'ont pas été exécutés sous Windows dans cette livraison.** La chaîne
Le boot WinPE a été validé en VM et sur le PC de test. Le parcours d'installation Windows et ses limites sont décrits dans [le guide](../docs/WINDOWS_INSTALL.md). Les nouveaux états de premier boot ne doivent pas être déduits d'un téléchargement.

## Poste de préparation

1. Sur un Windows de préparation, installer les **Deployment Tools** de l'ADK et
   l'add-on WinPE correspondant, obtenus sur la [page officielle Microsoft](https://learn.microsoft.com/en-us/windows-hardware/get-started/adk-install).
2. Installer les correctifs recommandés sur cette page pour la version choisie.
3. Ouvrir PowerShell 64 bits en administrateur, depuis le dépôt. Les licences ADK
   et les éventuels droits de redistribution restent ceux de Microsoft.
4. Construire le shell de test et son bundle :

```powershell
.\scripts\Build-WinPE.ps1 -WorkDirectory C:\PocketInstall-WinPE -OutputDirectory C:\PocketInstall-Bundle
```

Options : `-DriverDirectory C:\Pilotes` pour des `.inf` réseau/stockage validés,
`-WithPowerShell` pour préparer le futur Recovery, `-UseBootEx` pour le chemin
ADK `/bootex` documenté. Les répertoires de sortie doivent être nouveaux ; le
script refuse de nettoyer des chemins existants. En échec, il tente de démonter
**son** image avec `/Discard`, jamais tous les mounts DISM du poste.

La création d'une ISO **WinPE seulement** permet un smoke test indépendant. Ce
n'est pas l'ISO Windows à installer ; le parcours réseau utilise les fichiers du
bundle, pas cette ISO. Vérifier ensuite la chaîne des signatures réellement
exportées (CA 2011/2023 et dbx) ; `Get-AuthenticodeSignature` sur le poste ne
certifie pas l'admission par le firmware du PC.

## Bundle

`boot.wim`, `boot.sdi`, `BCD`, `bootmgfw.efi`, manifeste de tailles/hashes. Le
gestionnaire exporté est le `EFI\Boot\bootx64.efi` sélectionné dans le média ADK,
renommé pour le contrat wimboot. Inspecter les fichiers après `/bootex` avec l'ADK
choisi : la procédure n'est pas validée ici. Ne pas mélanger deux générations de
boot managers/WIM/BCD en pensant que leurs noms prouvent leur compatibilité.

Créer le script via `scripts/prepare_ipxe_script.py`, ajouter un wimboot officiel
épinglé par version/hash et utiliser la voie iPXE de `boot/ipxe/README.md`. Un futur
import de bundle dans l'APK est nécessaire ; aucun import WinPE n'est encore livré.

L'environnement affichera `PocketInstall boot successful (WinPE)` après `wpeinit`,
puis attendra une touche et s'arrêtera. Pas de partitionnement ni de réparation.
**WinPE peut monter/initialiser du stockage : pour un test garantissant zéro accès
au SSD, utiliser une VM sans disque ou déconnecter les disques physiques.** Le
petit POC EFI ne possède pas cette limitation et reste le premier test recommandé.

## Image Windows séparée

Télécharger l'ISO voulue sur la [page Microsoft](https://www.microsoft.com/en-us/software-download/windows11),
copier son SHA-256 officiel exact et utiliser :

```powershell
.\scripts\Extract-InstallImage.ps1 -IsoPath C:\Images\Windows11.iso -ExpectedIsoSha256 HASH_OFFICIEL_64_HEX -OutputDirectory C:\PocketInstall-Image
```

Le script vérifie l'ISO, la monte en lecture seule, copie `install.wim` ou
`install.esd`, enregistre son hash et demande à DISM la liste d'images. Il ne
modifie pas le SSD à réinstaller. Le hash extrait est dérivé du média vérifié,
pas présenté comme un hash Microsoft directement publié pour le WIM.

## Installation ultérieure

`New-InstallPlan.ps1` produit seulement un JSON de plan (`executable=false`) après
inventaire disque et hash local. Les composants PowerShell/StorageWMI sont requis.
Aucune saisie ERASE n'autorise actuellement un formatage : l'exécuteur n'existe pas.
Le futur exécuteur devra revalider modèle/numéro de série/UniqueId, demander ERASE
sur le PC et gérer staging, erreurs, GPT, DISM, BCDBoot et WinRE.

`DISM /Apply-Image /ImageFile:<local.wim> /Index:<index> /ApplyDir:W:\ /CheckIntegrity`
est le principe d'application. Les images ESD doivent être inspectées et peuvent
être exportées en WIM avec l'ADK adapté. Le téléchargement HTTP direct n'est pas
une valeur valide d'`/ImageFile`. Ne pas cacher l'écriture du staging sur le disque
cible avant confirmation.

`BCDBoot W:\Windows /s S: /f UEFI` cible explicitement l'ESP mais modifie le
comportement de création NVRAM : tester les chemins de repli et la sélection du
firmware ; un exit code zéro n'est pas une preuve de redémarrage réussi.

`unattend.xml` peut être appliqué avec DISM pour les passes prises en charge et
placé dans `Windows\Panther` pour les passes suivantes. `autounattend.xml` est
recherché par Windows Setup dans son parcours ; son nom ne suffit pas pour le
flux DISM seul. Ne pas stocker de mot de passe, de clé produit ou de secret réseau
dans le dépôt.

Le réseau Wi-Fi préboot n'est pas hérité par WinPE. Microsoft ne prend pas en
charge son Wi-Fi général ; Ethernet PC est le prérequis de ce parcours initial.

