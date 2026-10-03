# Compatibilité

**Aucun modèle physique n'est certifié à ce stade.** Lire `docs/VALIDATION.md`.

## Prérequis du POC

| Situation | Résultat / diagnostic |
|---|---|
| UEFI x64 + HTTP Boot + URL manuelle + réseau IPv4 | Candidat au test EFI. |
| PC sans SSD / Windows cassé | Aucun problème pour le POC EFI. |
| UEFI avec PXE uniquement | Incompatible avec ce MVP HTTP seul. |
| URL manuelle absente, DHCP configurable | Extension labo possible ; pas le parcours Android autonome. |
| URL manuelle absente, DHCP non configurable | Impossible dans les contraintes actuelles. |
| Wi-Fi fonctionnel sous Windows mais pas dans l'UEFI | « Ce PC n'est probablement pas compatible avec Wireless PocketInstall ». |
| HTTP Boot Ethernet seulement | Téléphone Wi-Fi possible ; Ethernet PC→routeur nécessaire. |
| Firmware imposant HTTPS | POC HTTP incompatible ; besoin de confiance TLS firmware à étudier. |
| Secure Boot actif | Binaire POC non signé bloqué, sauf politique personnalisée autorisant le binaire. |
| BIOS Legacy / UEFI IA32 / ARM64 | Hors périmètre du binaire x64. |
| Deux VLAN / réseau invité / AP isolation | Trafic potentiellement bloqué ; aucun contournement automatique. |
| DHCP absent | Bloqué, sauf configuration IP statique proposée par le fabricant. |
| VPN / pare-feu téléphone / restrictions LAN Android | Vérifier un GET depuis un autre appareil ; désactiver la session en cas de réseau inadapté. |
| Téléphone en hotspot | Non validé ; ne pas confondre avec le LAN Wi-Fi normal. |

Wi-Fi firmware, réseau disponible dans iPXE et réseau disponible dans WinPE sont
**trois validations séparées**. Un succès EFI ne certifie pas une installation Wi-Fi.

## Ce que l'application peut détecter

Réseau Android privé disponible, IP/prefixe, permission réseau, erreurs de bind,
perte du réseau, fichiers demandés, téléchargements et expiration. Elle ne peut
pas examiner le BIOS d'un PC qui n'a encore établi aucune connexion.

Les problèmes firmware, Secure Boot, Wi-Fi UEFI et DHCP du PC sont donc expliqués
par un guide de diagnostic, pas présentés comme une détection automatique certaine.
Un GET reçu ne signifie pas « PC compatible » et une absence de GET ne suffit pas
à distinguer un pare-feu, une mauvaise URL, l'isolation ou un boot désactivé.

## Registre de tests

| Modèle / version firmware | HTTP manuel | Réseau préboot | EFI POC | WinPE Ethernet | Secure Boot |
|---|---|---|---|---|---|
| QEMU 8.2.2 / OVMF edk2-stable202605 HTTP | URI BootNext, pas menu OEM | Virtio + SLIRP IPv4 DHCP | Réussi, Python et Kotlin JVM | Non testé | Désactivé |
| Dell, documentation HTTPs Boot | Documenté | Wired/wireless selon système | Non testé | Non testé | Non testé |
| Autres cartes grand public et portables | À vérifier dans le BIOS exact | À vérifier | Non testé | Non testé | Non testé |

Un manuel général Dell n'est pas une certification de tous les Dell. Pour chaque
test, noter SKU, version BIOS, NIC, méthode réseau, URL/port, état Secure Boot,
révocations et traces. Ne pas inscrire « testé » sans exécution constatée.

## Câble USB direct (POC 0.1.2, expérimental)

Le partage USB d'Android expose un réseau RNDIS/NCM selon le téléphone ; un
pilote compatible doit exister **dans le firmware** du PC, qui doit aussi
disposer de DHCP IPv4 et HTTP Boot sur cette interface. Aucun couple réel
n'est validé. MTP, recharge, détection sous Windows ou USB Boot stockage ne
suffisent pas. Voir [docs/USB_CABLE.md](docs/USB_CABLE.md).

La détection dans PocketInstall est une heuristique de noms d'interfaces USB
avec IPv4 privée, pas une détection du firmware. Un modèle OEM non reconnu peut
ne pas apparaître. Un adaptateur USB-C/Ethernet reconnu par Android utilise le
mode LAN ; c'est un parcours différent du partage USB direct.
