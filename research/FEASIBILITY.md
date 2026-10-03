# PocketInstall — faisabilité technique

Recherche du **3 octobre 2026**. Les sources sont des normes, documents des éditeurs
et documentation/source des projets concernés. Une possibilité documentée n'est
pas un résultat de test : voir `docs/VALIDATION.md` pour les mesures du prototype.

**Complément 0.1.3 :** un parcours UEFI PXE IPv4 / TFTP a été ajouté après cette
étude du MVP HTTP. Il exige un DHCP de boot configurable ou un relais externe,
sans promesse de serveur PXE autonome Android non rooté sur une box ordinaire.
Voir [PXE.md](PXE.md) pour la recherche complémentaire.

## Verdict avant implémentation

**GO pour un POC Android non rooté → HTTP Boot UEFI → application EFI x64 autonome.**
Conditions : firmware avec HTTP Boot, URL manuelle utilisable (ou DHCP configurable),
interface réseau prise en charge avant le démarrage, connectivité LAN et politique
Secure Boot acceptant le programme. Un SSD, un Windows fonctionnel et une application
sur le PC ne sont pas nécessaires. Un serveur TCP sur le port 8080 ne nécessite pas
de root Android.

**GO conditionnel pour WinPE via iPXE + wimboot, d'abord en Ethernet côté PC.**
Le téléphone peut rester en Wi-Fi. Cela respecte l'absence de câble entre téléphone
et PC, mais n'offre pas un PC entièrement sans fil.

**NO GO pour une promesse universelle « tous les PC, tout en Wi-Fi, Secure Boot
activé, sans préparation ».** Le firmware ne peut pas recevoir un pilote absent
avant d'avoir atteint un premier chargeur. Un téléphone ne peut pas ajouter HTTP
Boot à un UEFI ni configurer le DHCP d'un routeur à sa place.

Point majeur : **le Wi-Fi général n'est pas pris en charge par WinPE selon
Microsoft [S8]**. Même si HTTP Boot Wi-Fi fonctionne, le réseau UEFI ne reste pas
automatiquement disponible après la prise de contrôle par Windows. La voie WinPE
sans fil est une recherche distincte, pas une fonction annoncée du MVP.

## Niveau de certitude

| Sujet | Statut | Conséquence pour PocketInstall |
|---|---|---|
| HTTP Boot IPv4/IPv6, URI et chargement d'une image UEFI | Défini par la norme [S1] | Un serveur HTTP suffit pour livrer la première image à un client compatible. |
| Chaque PC UEFI possède HTTP Boot | Faux [S2] | UEFI et HTTP Boot sont deux exigences différentes. |
| Menu permettant de saisir une URL | Fabricant [S3] | Dell décrit un mode manuel ; vérifier modèle et BIOS. |
| HTTP Boot Wi-Fi | Fabricant [S3] | Possible sur certains systèmes ; pas déductible du Wi-Fi sous Windows. |
| Démarrage du `.efi` fourni ici | À tester par firmware | x64 uniquement ; aucune compatibilité physique encore certifiée. |
| Android sert un fichier sur TCP/8080 | API publique Android [S9–S11] | Pas de privilège root, de raw socket, ni de serveur DHCP nécessaire. |
| `bootmgfw.efi` isolé télécharge automatiquement ses voisins en HTTP | Non démontré | Ne pas proposer cette chaîne comme solution. |
| iPXE + wimboot charge WinPE via HTTP | Documenté par iPXE [S6–S7] | Méthode retenue pour l'étape WinPE Ethernet, à valider localement. |
| iPXE conserve le Wi-Fi du firmware | Expérimental | Un chemin HTTP firmware ne garantit pas un SNP exploitable par iPXE. |
| WinPE stock continue en Wi-Fi | Non pris en charge [S8] | Ethernet pour la première chaîne d'installation Windows. |
| Une ISO/IMG WinPE en RAM depuis HTTP Boot | Norme + implémentation [S1, S4] | Alternative à mesurer ; certains menus imposent `.efi`, RAM importante. |

## HTTP Boot, PXE, iPXE : des choses différentes

| Terme | Premier transfert | Où est le client ? | Besoin côté réseau |
|---|---|---|---|
| UEFI Network Boot | Terme générique | Firmware | Dépend de PXE, HTTP, pilotes et options OEM. |
| PXE classique | DHCP puis généralement TFTP/UDP | Firmware/NIC | Informations de boot DHCP ou proxyDHCP et service TFTP. |
| UEFI HTTP Boot | Téléchargement HTTP/TCP | Firmware | Adresse IP ; URI via configuration ou découverte DHCP. |
| iPXE | HTTP et autres protocoles | Programme de boot séparé ou firmware | Il doit d'abord être chargé ; ne remplace pas magiquement le premier boot. |

La norme définit une image de démarrage appropriée à l'architecture du client.
Un binaire PE/COFF EFI x64 est donc un bon premier objet. Un script `.ipxe`, une
page HTML ou un `boot.wim` seuls ne sont pas des applications EFI.

L'URL numérique `http://192.168.1.42:8080/<session>/bootx64.efi` évite DNS et mDNS.
Le port non standard est à valider sur le firmware. HTTP sans TLS doit également
être autorisé : un menu nommé « HTTPs Boot » ne garantit pas toutes les politiques.

### Adresse IPv4 et DHCP

Le téléphone fournit **HTTP uniquement**, pas DHCP. Le routeur fournit les adresses.
L'URL manuelle évite de modifier l'annonce du fichier de boot ; elle ne supprime
pas le besoin d'obtenir une adresse IP pour le PC. L'implémentation EDK II accepte
une offre DHCP ordinaire lorsqu'une URI est déjà configurée [S5] ; cela reste un
résultat propre à cette implémentation.

En découverte automatique, l'identification `HTTPClient` et les options DHCP de
boot doivent être traitées correctement [S1, S6]. Le POC ne suppose pas qu'une box
grand public les envoie. Une IP statique n'est un repli que si le menu OEM le permet.
Sans URL manuelle **et** sans accès à la configuration DHCP, le périmètre imposé
est bloqué. Aucun serveur DHCP parallèle ne doit être lancé sur le LAN domestique.

### LAN avec un téléphone

La localisation du serveur sur Android est sans importance pour le protocole HTTP.
Le chemin peut être téléphone Wi-Fi → point d'accès → Ethernet PC, ou entièrement
Wi-Fi si le firmware du PC sait s'associer au réseau. Même SSID ne signifie pas
connectivité : réseau invité, AP isolation, VLAN, VPN et filtrage peuvent la bloquer.
Le hotspot du téléphone est une variante OEM à tester séparément, pas le repli par
défaut du POC. Le prototype utilise un LAN IPv4 privé et une interface explicitement
choisie, jamais le réseau mobile comme adresse de boot.

## Environnement minimal retenu

Le premier environnement est **une application UEFI**, pas Windows et pas un OS
complet. Elle fonctionne dans les Boot Services, affiche :

`PocketInstall boot successful`

puis demande l'arrêt via Runtime Services après une touche ou 30 secondes. Elle
n'ouvre aucun protocole disque/fichier et n'écrit aucune variable UEFI. La création
d'une entrée HTTP dans le menu du firmware peut, elle, modifier la NVRAM du PC.
On distingue cette configuration du comportement du programme.

Le journal HTTP prouve un transfert, **pas l'exécution du programme**. La preuve
de boot est le message sur le PC, ou sa sortie console en VM, suivi de l'arrêt.

Le laboratoire valide cette chaîne sous QEMU/OVMF avec une URI BootNext, DHCP
ordinaire et les deux serveurs (Python puis Kotlin). Cela ne valide pas un menu
OEM ni un téléphone réel. L'OVMF du système et une première VM mal équipée n'ont
pas démarré : HTTP Boot doit être compilé, une NIC avec pilote natif doit être
présente et la pile EDK II retenue exige `EFI_RNG_PROTOCOL` [S24]. Le script de test
ajoute virtio-rng ; il ne remplace pas le client HTTP du firmware par un ROM iPXE.

## Secure Boot et signatures

HTTP et Secure Boot traitent deux problèmes différents : transport et admission
du code. Un HTTPS valide ne rend pas un `.efi` digne de confiance. Un certificat
autogénéré n'est pas spontanément présent dans la base de confiance du firmware.
Un programme accepté peut être bloqué par une révocation dans `dbx` [S12].

Le POC PocketInstall est **non signé**. Il nécessite Secure Boot désactivé sur une
machine de test, ou une signature avec une clé déjà approuvée par sa configuration.
Il ne change pas ces paramètres. Sur un PC chiffré, conserver préalablement la clé
de récupération BitLocker avant de changer la politique de boot.

iPXE documente actuellement des distributions signées et des shims [S13]. Ce n'est
plus exact de dire « iPXE ne peut jamais fonctionner avec Secure Boot ». Cependant,
un iPXE reconstruit avec un script embarqué n'hérite pas de cette signature. La
chaîne shim/iPXE/wimboot/Microsoft, le chargement des fichiers auxiliaires, les CA
2011/2023 et les révocations doivent être testés ensemble. Le prototype ne livre
pas de garantie Secure Boot pour cette chaîne.

## Pourquoi `bootmgfw.efi` ne suffit pas

| Fichier | Rôle | Est-il un premier programme EFI autonome ? |
|---|---|---|
| `bootx64.efi` | Nom de repli UEFI x64 ; ici, application PocketInstall | Oui pour le binaire fourni. Ce nom ne décrit pas son contenu. |
| `bootmgfw.efi` | Gestionnaire de démarrage Microsoft en mode UEFI | EFI exécutable, mais nécessite son environnement et sa configuration. |
| `BCD` | Base décrivant l'image à démarrer et le RAM disk | Non. |
| `boot.sdi` | Support de RAM disk utilisé au démarrage Windows | Non. |
| `boot.wim` | Image de WinPE, noyau, pilotes, outils et shell | Non. |
| `winload.efi` | Chargeur Windows appelé par le boot manager | Dans le WIM ; pas le point d'entrée HTTP retenu. |
| `install.wim` / `install.esd` | Images de l'OS à appliquer sur le SSD | Non ; pas à télécharger au stade firmware. |

La procédure Microsoft PXE [S14] décrit une infrastructure et des mécanismes
réseau propres ; elle ne démontre pas que copier les mêmes fichiers dans un
répertoire HTTP suffit. wimboot apporte un système de fichiers virtuel et remet
les fichiers attendus au gestionnaire Microsoft [S7]. Le réseau sera réinitialisé
par WinPE avec ses **propres** pilotes après le handoff.

Chaîne proposée pour la deuxième étape : HTTP Boot → iPXE EFI → téléchargement
HTTP de wimboot, BCD, SDI et WIM → WinPE en RAM. Aucun ISO Windows complet n'est
envoyé à l'UEFI. Une ISO/IMG contenant seulement WinPE pourra être comparée sur les
firmwares qui savent la monter ; ce n'est pas le POC principal.

## WinPE : préparation et taille

WinPE vient de l'ADK et de son add-on séparé [S15]. Le build de cet environnement
nécessite un poste de préparation Windows ; il peut être effectué en amont et ne
constitue pas une application préinstallée sur le PC à réparer. Mais **la création
initiale de WinPE avec seulement un téléphone n'est pas résolue par ce prototype**.

Les scripts préparent `copype`, montent le WIM avec DISM, injectent un shell de test
et exportent les fichiers. Le POC EFI tient en quelques kilo-octets ; un WinPE est
habituellement beaucoup plus gros et doit tenir en RAM [S16]. Les tailles réelles
seront enregistrées au build. Le WinPE de test n'effectue ni partitionnement ni
installation. Pour les tests disque intacts, aucun disque n'est attaché à la VM.
La procédure physique WinPE impose de déconnecter les SSD : Windows peut initialiser
ou monter des volumes, contrairement au programme EFI autonome.

Au 03/10/2026, la page Microsoft présente l'ADK **10.1.26100.9457 (septembre 2026)**
pour les versions x64 Windows concernées [S15]. Utiliser la version et les correctifs
adaptés au Windows ciblé, ainsi que des composants optionnels de la même version.
Les média signés Windows UEFI 2023 CA nécessitent le choix adéquat du boot manager
et, pour la production de média, le chemin `/bootex` documenté [S17].

## Android

Choix : Kotlin, Android Studio, Compose, service au premier plan `connectedDevice`,
notification avec arrêt, verrou CPU borné et serveur en streaming. Ce type de
service couvre une interaction avec un appareil externe sur le réseau [S9].
Le serveur ne démarre qu'à une action utilisateur et expire au bout de 30 minutes.
Pas de démarrage automatique au boot du téléphone ni de redémarrage silencieux.

Le projet épingle AGP 8.13.2 / Gradle 8.13 / Kotlin 2.2.21, JDK 17, compile/target
SDK 36 [S18]. Ce sont des versions stables explicites, pas une affirmation que ce
sont les plus récentes. La migration target 37 exige le consentement réseau local
`ACCESS_LOCAL_NETWORK` et son traitement à l'exécution [S10]. La documentation
demande de ne pas déclarer cette permission pour target ≤ 36. Les restrictions
locales Android 16 activées volontairement doivent aussi être testées séparément.
Ne pas supposer qu'un service au premier plan neutralise toute économie d'énergie
OEM [S11]. L'écran peut rester allumé pendant la première validation.

## Images officielles et droits

Pour Windows : téléchargement utilisateur sur la page Microsoft, vérification du
SHA-256 officiel de **l'ISO exacte, langue et version**, puis extraction locale de
`sources/install.wim` ou `.esd` [S19]. Pas de miroir communautaire ni de lien CDN
supposé permanent. Les liens de la page expirent ; aucun catalogue de hashes
universels n'est inventé. Un hash d'image extrait calculé localement trace cette
extraction, sans constituer une signature indépendante de Microsoft.

Pour WinPE : ADK/add-on officiels, licences acceptées par l'utilisateur qui prépare
le média. WinPE est destiné au déploiement et à la récupération, pas à devenir un
OS d'usage général [S16]. La documentation technique n'est pas une licence de
redistribution : les EULA/REDIST de la version obtenue restent à examiner avant de
publier une image. **Les droits de redistribution commerciale n'ont pas été établis
par cette recherche.** Le dépôt et l'APK ne contiennent aucun binaire Microsoft.
Ils livrent uniquement du code PocketInstall et des scripts de construction.
L'activation/licence de Windows reste nécessaire ; le projet n'en fournit pas.

## Installation ultérieure : architecture, pas fonction du POC

DISM sait appliquer et exporter les images Windows ; BCDBoot installe les fichiers
de boot [S20–S21]. Télécharger d'abord avec vérification, choisir l'index réel du
WIM/ESD (Home/Pro n'ont pas un index fixe), présenter le modèle, numéro de série et
taille du disque, puis exiger **ERASE** sur le PC. L'API HTTP reste en lecture seule
et ne déclenche jamais ces commandes.

Prévoir une zone de staging : DISM `/Apply-Image` n'accepte pas une URL HTTP comme
fichier image. Sur une machine à un seul SSD, conserver l'image en RAM peut demander
beaucoup de mémoire ; un staging SSD est déjà une écriture destructive à autoriser.
Une solution future peut réserver une partition de staging après confirmation et
la supprimer à la fin. Ne pas annoncer « streaming direct HTTP vers DISM ».

GPT : ESP FAT32, MSR, partition Windows NTFS et Recovery, dimensions adaptées aux
secteurs et à `winre.wim` [S22]. `BCDBoot /s` ne crée pas l'entrée NVRAM de la même
façon que sans `/s` : validation du boot fallback et de la sélection du bon disque
obligatoire [S21]. `unattend.xml` sert la configuration/OOBE ; le nom
`autounattend.xml` n'est pas un mécanisme automatique du flux DISM seul [S23].

Recovery et profils viendront après validation du boot et de l'installation en VM.
Pas de retrait du magasin de composants, des drivers, de Defender, Windows Update,
WinRE ou des dépendances système. Les options seront explicites et documentées.

## Sources consultées

Les références sont des liens canoniques. Quelques pages UEFI 2.11 renvoient 403
dans le lecteur web ; les sections correspondantes 2.10, l'index et les sources
EDK II permettent de contrôler les points concernés. Aucun test fabricant n'est
déduit d'un manuel.

- **S1** [UEFI 2.11, chapitre 24, HTTP Boot](https://uefi.org/specs/UEFI/2.11/24_Network_Protocols_SNP_PXE_BIS.html) ; [édition 2.10](https://uefi.org/specs/UEFI/2.10/24_Network_Protocols_SNP_PXE_BIS.html).
- **S2** [UEFI 2.10, Overview, conditional protocol requirements](https://uefi.org/specs/UEFI/2.10/02_Overview.html).
- **S3** [Dell — Introduction to HTTPs Boot, modes manuel/auto et wired/wireless](https://www.dell.com/support/manuals/en-us/bios-connect/https_ug/introduction-to-https-boot?guid=guid-dbc85161-a46f-4c9f-97bd-2134b37c0dce&lang=en-us).
- **S4** [UEFI 2.11, Load File / RAM disk](https://uefi.org/specs/UEFI/2.11/13_Protocols_Media_Access.html).
- **S5** [TianoCore EDK II — HttpBootSelectDhcpOffer, URI préconfigurée](https://github.com/tianocore/edk2/blob/edk2-stable202605/NetworkPkg/HttpBootDxe/HttpBootDhcp4.c).
- **S6** [iPXE project — UEFI HTTP chainloading](https://ipxe.org/appnote/uefihttp).
- **S7** [iPXE project — wimboot architecture](https://ipxe.org/appnote/wimboot_architecture) et [WinPE over HTTP](https://ipxe.org/howto/winpe). Attribution : iPXE, https://ipxe.org.
- **S8** [Microsoft — WinPE network drivers, limitations](https://learn.microsoft.com/en-us/windows-hardware/manufacture/desktop/winpe-network-drivers-initializing-and-adding-drivers?view=windows-11).
- **S9** [Android — Foreground service types, connectedDevice](https://developer.android.com/develop/background-work/services/fgs/service-types).
- **S10** [Android — Local network permission, target 36/37](https://developer.android.com/privacy-and-security/local-network-permission).
- **S11** [Android — Optimize for Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby).
- **S12** [UEFI — specifications / Secure Boot](https://uefi.org/specsandtesttools) ; [Microsoft — boot media and PCA2023](https://support.microsoft.com/en-us/servicing/os/windows/2025/02/updating-windows-bootable-media-to-use-the-pca2023-signed-boot-manager).
- **S13** [iPXE — Secure Boot releases and chain](https://ipxe.org/secboot).
- **S14** [Microsoft — PXE server loading WinPE](https://learn.microsoft.com/en-us/windows/deployment/configure-a-pxe-server-to-load-windows-pe).
- **S15** [Microsoft — Download and install Windows ADK](https://learn.microsoft.com/en-us/windows-hardware/get-started/adk-install).
- **S16** [Microsoft — Windows PE, RAM and permitted purposes](https://learn.microsoft.com/en-us/windows-hardware/manufacture/desktop/winpe-intro?view=windows-11).
- **S17** [Microsoft — Create WinPE media, /bootex](https://learn.microsoft.com/en-us/windows-hardware/manufacture/desktop/winpe-create-usb-bootable-drive?view=windows-11).
- **S18** [Android — AGP 8.13 compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes) ; [Kotlin Gradle compatibility](https://kotlinlang.org/docs/gradle-configure-project.html).
- **S19** [Microsoft — Windows 11 official downloads and SHA-256](https://www.microsoft.com/en-us/software-download/windows11).
- **S20** [Microsoft — DISM image management](https://learn.microsoft.com/en-us/windows-hardware/manufacture/desktop/dism-image-management-command-line-options-s14?view=windows-11).
- **S21** [Microsoft — BCDBoot, /s and firmware selection](https://learn.microsoft.com/en-us/windows-hardware/manufacture/desktop/bcdboot-command-line-options-techref-di?view=windows-11).
- **S22** [Microsoft — UEFI/GPT partition requirements](https://learn.microsoft.com/en-us/windows-hardware/manufacture/desktop/configure-uefigpt-based-hard-drive-partitions?view=windows-11).
- **S23** [Microsoft — DISM unattended servicing / offlineServicing](https://learn.microsoft.com/en-us/windows-hardware/manufacture/desktop/dism-unattended-servicing-command-line-options?view=windows-11).
- **S24** [TianoCore EDK II — dépendances de DxeNetLib](https://github.com/tianocore/edk2/blob/edk2-stable202605/NetworkPkg/Library/DxeNetLib/DxeNetLib.inf).
