# PocketInstall

Faire démarrer un PC inutilisable depuis un téléphone Android non rooté sur le
même LAN, via **UEFI HTTP Boot**, sans clé USB, câble téléphone–PC ou application
préinstallée sur le PC.

Cette livraison est le **POC 0.1.0**. Elle fournit un environnement EFI minimal,
une application Android Kotlin/Compose, les tests réseau et la préparation de
la future chaîne WinPE. Elle n'installe ni ne répare encore Windows.

## Ce qui fonctionne déjà

Le boot natif HTTP a été exécuté dans QEMU/OVMF, sans disque invité : DHCP IPv4 →
HEAD/GET HTTP → EFI en RAM → `PocketInstall boot successful` → arrêt après 30 s.
L'essai a réussi avec le serveur Python de développement, puis avec **le serveur
Kotlin partagé par l'application Android**. Le ROM iPXE de la carte virtuelle est
désactivé : le premier téléchargement est effectué par OVMF.

| Élément | État de cette livraison |
|---|---|
| Recherche sourcée et architecture | Terminées ; verdict conditionnel. |
| EFI x64 autonome | Compilé, 4 896 octets, exécuté en VM par HTTP Boot. |
| Serveur Kotlin | 12 tests TCP réussis et boot HTTP natif réussi en VM. |
| Android / Compose | APK debug compilé ; signature et binaire embarqué vérifiés. |
| Téléphone réel + PC physique | **Pas encore testé**. Aucun fabricant certifié. |
| WinPE / installation Windows | Scripts et architecture préparés ; pas exécutés sous Windows. |

Les preuves et versions exactes sont dans [docs/VALIDATION.md](docs/VALIDATION.md).
Un GET reçu par le téléphone ne suffit pas : le succès doit être visible sur le PC.

## Faisabilité et limites

**MVP viable sur matériel compatible**, sans promesse universelle : UEFI x64 avec
HTTP Boot, URI saisissable ou DHCP configurable, pilote réseau préboot et politique
Secure Boot adaptée. PXE seul ne suffit pas. La présence du Wi-Fi dans Windows ne
prouve pas son support dans le firmware. Le téléphone n'ajoute aucun de ces
composants au PC et ne remplace pas le DHCP du routeur.

Le POC EFI est **non signé** : Secure Boot doit l'accepter selon la configuration
de test. Il n'utilise aucun protocole disque et ne modifie aucune variable UEFI.
Configurer une entrée de boot dans le firmware peut modifier la NVRAM séparément.

Pour la future installation Windows, le premier parcours sera **PC Ethernet,
téléphone Wi-Fi**. Microsoft ne prend pas en charge le Wi-Fi général dans WinPE.
Le boot EFI sans fil et la connexion réseau après WinPE sont deux problèmes
distincts. Voir [research/FEASIBILITY.md](research/FEASIBILITY.md) et
[COMPATIBILITY.md](COMPATIBILITY.md).

## Essayer l'application

Le dossier [releases/0.1.0-poc/](releases/0.1.0-poc/README.md) contient
[PocketInstall-0.1.0-poc-debug.apk](releases/0.1.0-poc/PocketInstall-0.1.0-poc-debug.apk),
le binaire EFI et leurs hashes. C'est un APK de développement pour Android 8+ ;
aucun root, aucune connexion USB nécessaire.

1. Installer l'APK sur le téléphone et rejoindre un LAN Wi-Fi privé normal.
2. Ouvrir PocketInstall, choisir le réseau et démarrer le test EFI.
3. Noter l'IP et **l'URL exacte**, comprenant la session temporaire :
   `http://192.168.1.42:8080/<session>/bootx64.efi`.
4. Sur un PC de test compatible, sélectionner HTTP Boot IPv4 et saisir cette URL.
5. Constater `PocketInstall boot successful`, puis laisser le PC s'arrêter.

Ce test ne lance aucun formatage. Commencer par la procédure VM, puis suivre
[docs/TESTING.md](docs/TESTING.md) pour le test physique et le relevé du firmware.
Les contraintes du téléphone (service, écran éteint, économie d'énergie OEM)
doivent être validées sur l'appareil réel. Garder l'application visible pour le
premier essai.

Le serveur est désactivé par défaut, lié à une IPv4 LAN privée, limité au
sous-réseau, en lecture seule et fermé après 30 min. Le journal indique les fichiers
demandés et les octets envoyés. HTTP clair et token temporaire ne protègent pas
contre un attaquant actif du LAN : [SECURITY.md](SECURITY.md).

## Construire

Linux x64, GCC/binutils et GNU-EFI :

```sh
make -C boot
python3 scripts/sync_android_boot.py
```

Le binaire EFI est déjà inclus dans `android/app/src/main/assets/boot/` pour
permettre un build Android sans poste Linux.

Android Studio, JDK 17 complet, SDK 36 et Build Tools 36.0.0 : ouvrir `android/`,
laisser l'IDE configurer le SDK, puis :

```sh
cd android
./gradlew :server-core:test :app:assembleDebug :app:lintDebug
```

L'APK est dans `android/app/build/outputs/apk/debug/app-debug.apk`.
AGP 8.13.2, Gradle 8.13 et Kotlin 2.2.21 sont épinglés. Voir
[android/README.md](android/README.md) pour le service et les permissions LAN.

## Laboratoire sans disque

Installer QEMU, un OVMF **avec HTTP Boot** et `virt-firmware`, puis :

```sh
python3 scripts/qemu_http_boot.py --code CHEMIN_OVMF_CODE --vars CHEMIN_OVMF_VARS
```

Le script contrôle téléchargement, message et arrêt ; zéro disque invité.
Un OVMF de distribution peut omettre HTTP Boot. Le build du firmware de test,
la source d'aléa virtio-rng et l'essai du serveur Kotlin sont documentés dans
[docs/TESTING.md](docs/TESTING.md). Ce firmware n'est jamais à flasher sur un PC.

## WinPE et suite du projet

[winpe/README.md](winpe/README.md) décrit l'installation ADK/add-on, le script
`Build-WinPE.ps1`, les bundles WIM/SDI/BCD/EFI, la chaîne iPXE/wimboot et
l'extraction vérifiée d'une image Windows officielle. Ces étapes sont préparées
pour un poste Windows de construction ; aucun binaire Microsoft n'est distribué.

Le futur déploiement sera un plan local : image vérifiée → inventaire/confirmation
`ERASE` sur le PC → GPT → DISM depuis un fichier local → BCDBoot → WinRE →
redémarrage vérifié. Aucun exécuteur destructif n'est livré aujourd'hui.

Recovery et profils Clean/Gaming/Dev/Custom viendront ensuite. La conception évite
la suppression aveugle de composants critiques ; [ARCHITECTURE.md](ARCHITECTURE.md)
et [ROADMAP.md](ROADMAP.md) définissent ces étapes.

## Organisation et licences

`android/` : application et serveur partagé ; `boot/` : EFI et préparation iPXE ;
`winpe/` : shell/plan ; `scripts/` : build et tests ; `docs/` : procédures et preuves ;
`research/` : faisabilité et sources ; `releases/` : binaires du prototype.

Cloner le dépôt puis ouvrir le dossier `android/` dans Android Studio :

```sh
git clone https://github.com/P1kaCat/PocketInstall.git
cd PocketInstall
```

Le dépôt est actuellement privé : utiliser un compte GitHub autorisé pour le
clonage et les téléchargements. Le bundle Git conservé dans `releases/0.1.0-poc/`
identifie le commit source ayant servi à construire l'APK. Les caches de build,
SDK et images Microsoft sont exclus du dépôt.

Code PocketInstall : MIT. Notices GNU-EFI et dépendances Android incluses. Les
droits de redistribution WinPE/Windows doivent être examinés avant publication
d'images : [docs/LICENSING.md](docs/LICENSING.md).
