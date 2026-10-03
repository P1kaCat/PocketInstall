# PXE depuis Android non rooté : faisabilité de la version 0.1.3

Recherche du 3 octobre 2026. Ce document complète FEASIBILITY.md : le premier
POC ciblait HTTP Boot natif. Le nouveau parcours charge **le même EFI x64 via
TFTP**, depuis un firmware UEFI PXE IPv4. Aucun installateur Windows n'est lancé.

## Conclusion

Un PC **UEFI PXE sans HTTP Boot** peut exécuter le POC EFI. iPXE n'est pas
nécessaire pour cette preuve. En revanche, **téléphone Android non rooté + box
ordinaire non configurable ne constitue pas un serveur PXE autonome garanti**.
Il faut communiquer au firmware le serveur et le fichier, puis fournir TFTP
sur le port attendu. PocketInstall ne peut pas imposer ces fonctionnalités à
Android, à la box ou à la carte mère.

Deux parcours sont préparés :

1. DHCP du réseau configurable + TFTP Android UDP 69, si le téléphone l'autorise.
   Une redirection UDP correctement configurée peut remplacer le port 69 local.
2. Téléphone servant l'EFI en HTTP + **relais Linux externe**, avec dnsmasq en
   proxy-DHCP et TFTP. Le routeur continue à attribuer les adresses. Le relais
   récupère et met en cache uniquement le POC vérifié par SHA256.

Le deuxième parcours assouplit la contrainte « seulement un téléphone ». Il est
présenté comme un laboratoire / une alternative avec équipement supplémentaire,
pas comme une réussite du critère original. Le relais n'est pas une application
à préinstaller sur le PC à récupérer.

## Normes et protocoles

| Élément | Ce qui est établi | Ce qui n'est pas promis |
|---|---|---|
| TFTP, RFC 1350 | RRQ au port UDP 69 ; transfert depuis un port choisi par le serveur ; DATA/ACK ; fin par bloc court. | Saisir un port 6969 dans un menu PXE standard. |
| Options TFTP | RFC 2347 : OACK ; RFC 2348 : blksize ; RFC 2349 : tsize/timeout. | windowsize ou les extensions de tous les firmwares. |
| PXE DHCP | Les options d'architecture et informations de boot permettent de sélectionner le NBP adapté. | Tous les routeurs exposent next-server / options 66/67 dans leur interface. |
| UEFI | Le NBP doit correspondre au format et à l'architecture du firmware. Le POC est un EFI_APPLICATION PE32+ AMD64. | Un EFI x64 exécutable par un PXE Legacy BIOS ou UEFI IA32/ARM64. |
| Secure Boot | Le transport réseau ne donne pas de confiance à un binaire non signé. | PXE contourne Secure Boot ou signe automatiquement PocketInstall. |

Les RFC sont [1350](https://www.rfc-editor.org/rfc/rfc1350),
[2347](https://www.rfc-editor.org/rfc/rfc2347),
[2348](https://www.rfc-editor.org/rfc/rfc2348),
[2349](https://www.rfc-editor.org/rfc/rfc2349) et
[4578](https://www.rfc-editor.org/rfc/rfc4578).
Pour les codes d'architecture, ne pas confondre une étiquette historique de
dnsmasq, la table originale RFC 4578 et les registres / errata actuels : la
configuration de test accepte les deux valeurs 7/9 pour les firmwares x64
connus dans ce parcours, sans annoncer de service Legacy, IA32 ou ARM64.

## Android : limite importante

La documentation [Linux IP sysctl](https://docs.kernel.org/networking/ip-sysctl.html)
définit `ip_unprivileged_port_start`, par défaut 1024 ; les ports inférieurs
demandent root ou `CAP_NET_BIND_SERVICE` si cette politique reste active. Android
utilise des noyaux et politiques constructeur : **ne pas conclure que chaque
Android autorise ou refuse exactement les mêmes ports**. Le seul résultat
fourni par l'application est son essai réel de bind UDP 69 sur l'IP sélectionnée.

Si cet essai échoue, elle essaye **6969** et affiche que le démarrage PXE direct
est bloqué tant qu'un relais / une redirection n'est pas configuré. Un bind réussi
ne prouve pas non plus que le pare-feu, le Wi-Fi ou le firmware permettent le flux.
L'application ne demande pas de root et ne change pas les sysctl/iptables.

Le [module AOSP Tethering](https://source.android.com/docs/core/ota/modular-system/tethering)
fournit un DHCP système pour attribuer les IP de partage IPv4. Cette fonctionnalité
ne constitue pas une API de configuration PXE pour une application ordinaire.
Le mode PXE livré **ne modifie pas ce DHCP** et n'ouvre aucun serveur DHCP Android.
Le transfert USB demande en plus un pilote RNDIS/NCM dans l'UEFI ; il n'est pas
pris en charge dans le mode PXE 0.1.3.

## Fabricants et réseau

La [fiche ASUS PRIME B365M-K](https://www.asus.com/me-en/motherboards-components/motherboards/prime/prime-b365m-k/techspec/)
annonce le LAN Realtek RTL8111H et PXE, sans Wi-Fi intégré ni HTTP Boot annoncé.
L'utilisateur observe une entrée PXE ; **aucun boot PocketInstall physique sur
cette carte n'a encore été mesuré**. Une entrée Legacy PXE ne suffit pas : il
faut UEFI PXE IPv4 pour ce binaire.

Téléphone en Wi-Fi et PC en Ethernet peuvent communiquer sur le même LAN si
le réseau autorise Wi-Fi ↔ Ethernet. AP isolation, VLAN, blocage UDP et DHCP
indisponible empêchent le parcours. Le relais proxy-DHCP doit recevoir les
broadcasts PXE sur le segment Ethernet ; un poste sur Wi-Fi n'est pas garanti
pour ce rôle. Ne pas configurer un second pool DHCP sur le réseau domestique.

La documentation primaire [dnsmasq](https://thekelleys.org.uk/dnsmasq/docs/dnsmasq-man.html)
décrit `dhcp-range=...,proxy`, `pxe-service`, les tags et le serveur TFTP externe.
Le mode proxy fournit les informations de boot tandis qu'un autre DHCP attribue
les IP. Son comportement reste à tester sur la box et le firmware exacts.

## Pourquoi pas iPXE maintenant ?

[iPXE chainloading](https://ipxe.org/howto/chainloading) décrit PXE/TFTP → iPXE →
HTTP et le risque de boucle DHCP. Cette chaîne deviendra utile pour WinPE et les
fichiers lourds. Ajouter iPXE maintenant multiplierait les binaires, licences,
signatures et problèmes de pilote sans aider le premier message de succès.
Les distributions [iPXE Secure Boot](https://ipxe.org/secboot) peuvent comporter
des shims signés ; elles ne rendent pas notre EFI arbitraire digne de confiance.
Aucun binaire iPXE, Windows ou Microsoft n'est ajouté à cette release.

## Validation séparée des hypothèses

- Tests UDP du serveur Kotlin identique à celui de l'APK : octets, options,
  retransmission, mauvais ACK/TID, fichiers multiples exacts, refus des écritures,
  token absent/ancien, expiration, sous-réseau et port occupé.
- VM QEMU/OVMF sans disque : PXE natif → TFTP de référence libslirp → EFI → arrêt.
  Capture PCAP vérifiant RRQ, contenu et ACK. Pas de ROM iPXE substituée au firmware.
- Le test de référence VM **n'utilise pas le TFTP Android/Kotlin** ; cette preuve
  et les tests de protocole ne remplacent pas le test téléphone + firmware physique.
- Relais : génération limitée à une MAC, pas d'attribution IP, hash du POC exigé.
  Le script prépare la configuration ; l'opérateur lance explicitement dnsmasq.

Les observations effectivement obtenues sont consignées dans
[docs/VALIDATION.md](../docs/VALIDATION.md), pas déduites de la présence d'un menu.
