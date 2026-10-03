# Faisabilité du câble USB direct — 3 octobre 2026

## Conclusion

**Possible comme transport réseau sous conditions, expérimental dans PocketInstall.**
Le partage USB Android peut fournir un réseau local. PocketInstall peut y servir
son petit EFI par HTTP avec les API réseau normales. Cela ne crée pas un démarrage
USB mass-storage et ne fournit pas au firmware un pilote manquant.

Aucun matériel réel n'est validé. La compatibilité avec Android ne garantit pas
la compatibilité préboot ; la reconnaissance RNDIS/NCM dans Windows ne suffit pas.

## Éléments établis et dépendants du fabricant

| Élément | Statut |
|---|---|
| Android dispose du partage USB | Fonction du module système AOSP, configuration/OEM/opérateur à vérifier. |
| Le système choisit RNDIS ou NCM | Établi dans le code AOSP ; dépend de la configuration du téléphone. |
| Les interfaces IPv4 sont consultables sans root | API publique NetworkInterface ; informations restreintes pour les applications ordinaires. |
| Le partage USB se démarre automatiquement par cette app | Non implémenté ; activation manuelle dans Android, sans API cachée. |
| UEFI sait faire HTTP Boot sur un périphérique réseau pris en charge | Protocole décrit par UEFI ; disponibilité et pilote sont propres au firmware. |
| Tout firmware reconnaît le réseau USB d'un téléphone | Non garanti ; aucun modèle validé par PocketInstall. |
| USB Boot stockage permet de charger l'EFI via MTP | Hypothèse non retenue ; MTP, stockage bloc et réseau USB sont des fonctions différentes. |
| Le mode câble démarre WinPE et installe Windows | Non testé/non implémenté. |

## Sources primaires

1. AOSP, module Tethering : Wi-Fi, USB, Bluetooth et Ethernet.
   https://source.android.com/docs/core/ota/modular-system/tethering
2. AOSP, Tethering.java, `setUsbTethering` choisit `FUNCTION_NCM` ou
   `FUNCTION_RNDIS` selon `mConfig.isUsingNcm()`.
   https://android.googlesource.com/platform/packages/modules/Connectivity/+/refs/heads/main/Tethering/src/com/android/networkstack/tethering/Tethering.java
3. AOSP, configuration USB historique : expressions d'interfaces personnalisables,
   notamment `usb\\d` et `rndis\\d`. Une heuristique d'application n'est pas universelle.
   https://android.googlesource.com/platform/frameworks/base/+/2fffbcb7dfafdb61e2f0265e8265f66985c63147/packages/Tethering/res/values/config.xml
4. Android Developers, NetworkInterface : les applications non système n'ont accès
   qu'aux interfaces associées à une InetAddress. Des limitations/exceptions existent.
   https://developer.android.com/reference/java/net/NetworkInterface
5. Android Developers, TetheringManager : les API publiques récentes ne justifient
   pas l'emploi des constantes internes USB ni d'API cachées pour Android 8+.
   https://developer.android.com/reference/android/net/TetheringManager
6. UEFI 2.11, chapitre 24, protocoles réseau et HTTP Boot.
   https://uefi.org/specs/UEFI/2.11/24_Network_Protocols_SNP_PXE_BIS.html
7. Microsoft Learn, adaptateurs Ethernet et Surface : même le PXE Ethernet USB
   nécessite un adaptateur reconnu par le firmware. Cet exemple ne valide pas
   HTTP Boot ni un téléphone RNDIS/NCM.
   https://learn.microsoft.com/en-us/surface/ethernet-adapters-and-surface-device-deployment

## Architecture retenue

Le choix du mode câble est explicite. L'application découvre seulement les
interfaces USB présumées, actives, non loopback, avec un sous-réseau IPv4 privé.
La sélection contient interface/IP/préfixe ; elle est revalidée dans le service.
Le serveur HTTP reste lié à cette IP. Le système Android, activé par l'utilisateur,
gère la fonction USB et son DHCP ; PocketInstall ne configure pas ces services.

Les interfaces downstream ne sont pas nécessairement des Network de
ConnectivityManager : leur découverte et leur surveillance utilisent
NetworkInterface. Le mode LAN continue d'utiliser ConnectivityManager pour
Wi-Fi/Ethernet et exclut les VPN.

Ce mode doit être présenté comme un essai de compatibilité, jamais comme une
alternative universelle à une clé USB bootable.
