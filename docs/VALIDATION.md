# Validation observée

Ces résultats décrivent cette livraison. Ils ne certifient pas un téléphone ou
un PC physique. Les procédures reproductibles sont dans [TESTING.md](TESTING.md).

## Version 0.1.3 : PXE IPv4 natif

Le 3 octobre 2026, le POC a démarré dans QEMU **8.2.2** (Ubuntu
`1:8.2.2+ds-0ubuntu1.18`), OVMF **2024.02-2ubuntu0.9**, q35/TCG, 512 Mio :

| Contrôle | Observation |
|---|---|
| Firmware PXE IPv4 | OVMF natif, virtio-net ; ROM NIC iPXE désactivée. |
| DHCP / TFTP | Réseau virtuel isolé SLIRP ; serveur de référence QEMU/libslirp. |
| RRQ | Nom de session / EFI exact dans la capture PCAP. |
| DATA | 4 blocs ; octets reconstitués identiques à l'EFI. |
| ACK | Tous les blocs de données acquittés. |
| Exécution | `PocketInstall boot successful` dans la console firmware. |
| Arrêt | Message d'arrêt puis sortie QEMU 0. |
| Disques invités | **0**. Variables OVMF jetables seulement. |
| Téléphone réel / ASUS PRIME B365M-K | **Non testé**. |

Preuves locales : [résultat](evidence/pxe-0.1.3/result.json),
[console](evidence/pxe-0.1.3/serial.log). La capture complète et les résultats
JUnit de la construction officielle sont joints à la release GitHub.

**Le TFTP de cette VM est celui de référence, pas le serveur Kotlin.** Le module
Kotlin partagé par l'APK est testé séparément par des échanges UDP réels. Les
contrôles d'options, contenu, EOF, pertes d'ACK, mauvais TID, session, refus WRQ,
expiration et bind occupé ne remplacent pas un boot Android physique.

Avant publication, le workflow exige la reconstruction EFI identique à l'asset,
les tests Python du relais et la syntaxe dnsmasq, le boot PXE sans disque, tous
les tests Gradle avec le Kotlin/JDK épinglé du projet, assembleDebug, lintDebug,
apksigner et la validation des assets/manifestes. La release contient ces preuves
et le SHA du commit réellement construit. Une publication n'est pas possible si
le test PXE ou les tests de serveur échouent.

Le POC de cette version est toujours **4 896 octets**, x64 EFI_APPLICATION,
non signé, sans imports OS ni accès disque. SHA256 actuel :

```text
79297e8a11747abce0c53f6a522ebfcd6cd36f6550b0a8042b558749a8fe186d
```

Les mesures HTTP et le hash ci-dessous décrivent le **POC historique 0.1.0**.
La modification du texte de console pour HTTP/TFTP explique le nouveau hash.

## Démarrage HTTP natif : réussi

Deux essais complets, QEMU **8.2.2**, x64/q35/TCG, 512 Mio de RAM, OVMF construit
depuis **edk2-stable202605**, `RELEASE_GCC` :

| Contrôle | Serveur Python | Serveur Kotlin de l'APK sur JVM |
|---|---|---|
| DHCP IPv4 ordinaire via SLIRP | 10.0.2.15 | 10.0.2.15 |
| URI courte configurée via BootNext | Oui | Oui |
| HEAD puis GET HTTP 200 | Oui | Oui |
| Image EFI | 4 896 octets | 4 896 octets, journal de fin de transfert |
| Message `PocketInstall boot successful` | Observé sur console série | Observé sur console série |
| Message d'arrêt, puis sortie QEMU 0 | Oui | Oui |
| Disques invités | **0** | **0** |
| Secure Boot | Désactivé | Désactivé |

La NIC est `virtio-net-pci`, son ROM optionnel est désactivé ; virtio-rng fournit
l'entropie requise par les bibliothèques réseau EDK II. Le premier client HTTP
est **celui d'OVMF**, pas iPXE. Les seules pflash sont le code firmware en lecture
seule et une copie jetable de ses variables. Aucun SSD virtuel, ISO, WIM, image
Windows ou programme installé sur un disque invité n'intervient.

Firmware : `NETWORK_HTTP_BOOT_ENABLE=TRUE`, `NETWORK_ALLOW_HTTP_CONNECTIONS=TRUE`,
`NETWORK_TLS_ENABLE=FALSE`, FD 4 Mio. Le tag choisi utilise `GCC` ; `GCC5` n'est
plus le toolchain de cette version.

Échecs identifiés avant succès : OVMF de distribution sans HTTP Boot ; carte
e1000 sans pilote natif quand son ROM est désactivé ; pile réseau EDK II non
activée sans protocole RNG. Ces essais ne sont pas comptés comme des réussites.
Ils ont conduit au laboratoire explicite HTTP + VirtioNetDxe + virtio-rng.

Preuves : [résultat Python](evidence/python-result.json),
[console Python](evidence/python-serial.txt), [requêtes Python](evidence/python-http.jsonl),
[résultat Kotlin](evidence/kotlin-result.json), [console Kotlin](evidence/kotlin-serial.txt),
[requêtes Kotlin](evidence/kotlin-http.log). Les tokens de session et chemins
hôte ont été masqués ; les assertions et les lignes de succès sont conservées.

## Serveur et build Android

**12 tests réussis, zéro échec et zéro test ignoré**, via de vrais sockets TCP :
GET exact, HEAD, Range/reprise/suffixe, invalides/multiples, offsets >4 Gio,
liste blanche/traversée/méthodes, en-têtes trop gros et corps interdit,
sous-réseau source, bind public interdit, expiration/nouveau token, logs masqués,
RFC1918 et requête envoyée lentement sous le timeout d'inactivité.

Le cas 5 Gio est synthétique : il valide tailles/offsets en `Long`, sans prétendre
mesurer un transfert d'image Windows de 5 Gio. Le budget total d'en-têtes empêche
un client d'occuper un worker indéfiniment en envoyant un octet à la fois.

Build : AGP 8.13.2, Gradle 8.13, Kotlin 2.2.21, JDK 17, SDK 36 / Build Tools
36.0.0. APK debug `app.pocketinstall`, `0.1.0-poc`, min SDK 26, target 36.
Android Lint : **aucun problème signalé**. La signature APK est vérifiée avec
`apksigner`, et son asset EFI est identique au binaire testé, sans compression.
Cette signature Android n'est **pas** une signature Secure Boot du programme EFI.

Preuves : [tests JUnit](evidence/server-tests.xml),
[build et hashes](evidence/android-build.json), [Lint](evidence/android-lint.txt).
Python compilé et script Bash vérifié syntaxiquement. Les scripts PowerShell
de préparation Windows sont fournis **sans validation d'exécution**.

## Identité du POC EFI

PE32+ AMD64, sous-système `EFI_APPLICATION`, relocalisations présentes, aucun
import OS, non signé. Taille : **4 896 octets**. SHA-256 :

```text
347838d868cd2b205102eb171ec24f8f53226a00cabc36e7b227645be6772937
```

Le source utilise uniquement console, watchdog, timer, attente clavier et arrêt.
Il n'ouvre aucun protocole disque/fichier et n'écrit aucune variable UEFI.

## Ce qui reste à valider

- **Téléphone réel non rooté** : bind LAN, requêtes entrantes, notification,
  permissions, service au premier plan, changement d'IP et arrêt automatique.
- **PC réel** : présence d'un menu HTTP Boot, saisie d'URL/port/session,
  NIC Ethernet ou Wi-Fi préboot, politique Secure Boot, message et arrêt.
- Doze, politiques batterie OEM, écran éteint, hotspot et restrictions LAN opt-in.
- WinPE construit avec l'ADK, iPXE/wimboot, pilotes propres, handoff réseau et
  chaîne de signatures/révocations. Le serveur APK n'importe pas encore ce bundle.
- Transfert d'images Windows réelles, partitionnement, DISM, BCDBoot, WinRE,
  installation, réparation et profils : **non implémentés ou non testés**.

Le critère téléphone Android + PC physique est donc **préparé mais pas encore
observé**. Le prochain jalon est le test EFI physique sans écriture SSD décrit
dans TESTING.md ; WinPE vient après ce jalon.
