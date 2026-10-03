# Tester PocketInstall par UEFI PXE IPv4

Version 0.1.3 : **preuve EFI uniquement**, message de succès puis arrêt. Aucun
formatage, accès disque ou installation Windows dans notre programme EFI.
HTTP Boot n'est pas nécessaire dans le BIOS pour ce nouveau parcours.

## Avant de commencer

- PC **UEFI x64**, entrée **UEFI PXE IPv4**, pilote Ethernet préboot.
- PC en Ethernet vers le même LAN que le téléphone Android.
- Téléphone non rooté avec PocketInstall ; Wi-Fi ordinaire sans isolation invité.
- Un DHCP fonctionnel pour l'IP du PC.
- **DHCP avec paramètres de boot configurables**, ou **un relais Linux externe**.
- Le POC est **non signé** : la politique Secure Boot du PC de test doit l'accepter.
  Conserver la clé de récupération BitLocker avant de changer des réglages firmware.

Le mode USB existant n'est pas une clé bootable et n'est pas activé pour PXE.
Ne pas chercher à saisir l'URL HTTP dans une entrée « PXE IPv4 » : elle attend
ses instructions du réseau. Legacy PXE ne peut pas exécuter notre EFI x64.

## Dans l'application

1. Choisir **LAN · Wi-Fi / Ethernet**.
2. Choisir **PXE IPv4 · Ethernet · expérimental**.
3. Sélectionner l'IPv4 privée et démarrer la session.
4. Lire le résultat du port TFTP. « Serveurs prêts » ne signifie pas « DHCP configuré ».
5. Suivre l'une des deux configurations ci-dessous.

Le serveur HTTP reste disponible pour récupérer le POC vers un relais. Le TFTP
sert uniquement `SESSION/bootx64.efi`, avec la même session temporaire de 128 bits.
Le nom de fichier change après chaque session ; les réglages doivent suivre.
La session ferme HTTP et TFTP après 30 minutes, arrêt manuel ou perte de réseau.

## A. Routeur / DHCP configurable : TFTP sur le téléphone

Si l'application indique **TFTP UDP 69 ouvert**, configurer le serveur DHCP du
réseau pour **le PC cible UEFI x64 uniquement** :

| Paramètre | Valeur |
|---|---|
| Serveur de boot / next-server | IPv4 du téléphone affichée par l'application |
| Option 66 si l'interface l'utilise | IPv4 du téléphone, sans `http://` et sans port |
| Fichier de boot / option 67 | `SESSION/bootx64.efi` affiché par l'application |

Certains firmwares utilisent le champ BOOTP `siaddr` / `next-server` plutôt que
seulement l'option 66. Les intitulés et fonctionnalités dépendent du routeur.
Réserver l'IP du téléphone. Ne pas envoyer ce fichier à des clients Legacy/IA32.
Ne pas remplacer le DHCP du routeur par un deuxième pool sur le même LAN.

Si l'application affiche **6969**, le PXE standard ne peut pas le joindre
directement. Il faut un équipement capable de rediriger **UDP 69** vers ce port
en conservant correctement les flux TFTP, puis annoncer cet équipement au DHCP.
L'application ne crée pas cette redirection. Beaucoup de box ne la proposent pas.
Une simple redirection Internet/NAT de la box n'est pas un relais TFTP LAN :
**ne jamais exposer les ports PocketInstall sur Internet**.

Si la box n'expose aucun réglage de boot, utiliser B ou arrêter ici. Il n'existe
pas de réglage dans l'application qui puisse ajouter ces options à une box fermée.

## B. Relais Linux sur le LAN

Ce parcours nécessite **un autre appareil Linux**, relié en Ethernet au même
segment que le PC : autre PC, machine virtuelle avec interface bridgée adaptée,
ou équipement réseau capable de lancer dnsmasq. Aucune application n'est installée
sur le PC à récupérer. Le téléphone héberge le POC ; le relais en garde une copie
vérifiée et fournit proxy-DHCP/TFTP. Cette copie est annoncée dans les logs et la
documentation : ce n'est pas un démarrage autonome avec seulement un téléphone.

Sur Debian/Ubuntu du relais :

```sh
sudo apt install python3 dnsmasq-base
git clone https://github.com/P1kaCat/PocketInstall.git
cd PocketInstall
ip -4 address
```

Ne pas installer/activer un service DHCP général. `dnsmasq-base` apporte le
binaire ; nous lançons une instance limitée explicitement. Les ports UDP 67,
69, 4011 et les ports de transfert TFTP doivent être disponibles sur cette interface.
Un DHCP déjà exécuté sur **le relais** peut entrer en conflit ; ne pas l'arrêter
aveuglément. Le DHCP de la box reste en service.

Relever la MAC **Ethernet** du PC cible dans le BIOS ou sa fiche matériel, ainsi
que l'IP/interface du relais. Dans l'app, copier la commande du relais et remplacer
les trois valeurs indiquées. Exemple **à adapter** :

```sh
python3 scripts/prepare_pxe_relay.py \
  --boot-url http://192.168.1.42:8080/SESSION/bootx64.efi \
  --relay-ip 192.168.1.10 --interface enp3s0 \
  --target-mac 52:54:00:12:34:56 --prefix 24 --output pxe-relay
dnsmasq --test --conf-file="$PWD/pxe-relay/dnsmasq.conf"
sudo timeout 30m dnsmasq --no-daemon --user=root --conf-file="$PWD/pxe-relay/dnsmasq.conf"
```

Remplacer **SESSION** par les 32 caractères de l'URL réelle. Le script exige
le hash EFI de la version du dépôt : mettre le dépôt et l'APK à la même version.
Il refuse les URLs externes, redirections, autres fichiers, mauvaises MAC et
configurations débordant le sous-réseau privé. Le dossier de sortie doit être
nouveau ou vide. Il ne lance aucun service et ne télécharge aucune image Windows.

La configuration proxy n'attribue aucune IP et ignore les MAC autres que celle
choisie. Une MAC n'est pas une authentification forte. Le TFTP ne contient que
le petit fichier EFI ; conserver le test sur un LAN autorisé et de confiance.
La racine TFTP privée reste lisible par le processus lancé en root. Le téléphone
reste non rooté. Le relais expire par `timeout` ou s'arrête par Ctrl+C.

Un firmware peut ignorer un proxy-DHCP ou choisir une autre source de boot.
Dans ce cas, relever les messages et envisager la configuration du DHCP principal.
Ne pas transformer le relais en second DHCP complet pour « forcer » le résultat.

## Premier essai sur ton PC ASUS

La PRIME B365M-K annonce PXE ; son démarrage PocketInstall n'est pas encore
validé sur matériel. Garder l'Ethernet, activer la pile réseau du firmware et
sélectionner **UEFI PXE IPv4**. L'emplacement exact dépend de la version du BIOS.
Ne pas utiliser l'option Legacy dans le menu CSM pour le fichier `.efi`.

1. Démarrer les serveurs Android et la configuration DHCP / le relais.
2. Lancer le boot PXE IPv4 du PC.
3. Vérifier le téléchargement TFTP dans le journal Android **ou du relais**.
4. Vérifier **PocketInstall boot successful** sur le PC.
5. Laisser l'arrêt après 30 secondes ou appuyer sur une touche.
6. Arrêter les serveurs ; supprimer le cache du relais et restaurer les réglages
   DHCP / boot modifiés pour le test.

Le relais continue à servir sa copie jusqu'à son propre arrêt/timeout, même si
le téléphone est arrêté entre-temps. Arrêter **les deux** en fin de test. Le
firmware peut modifier sa NVRAM lors des réglages ; notre EFI ne le fait pas.

## Diagnostic

| Symptôme | Vérification |
|---|---|
| Pas d'adresse IP / DHCP timeout | Ethernet, DHCP de la box, VLAN, isolation. |
| IP reçue mais « no boot filename » | next-server/fichier ou proxy-DHCP manquant/ignoré. |
| TFTP timeout | Port 69, IP correcte, pare-feu UDP, ports de transfert, Wi-Fi ↔ LAN. |
| App sur 6969, aucun client | Le firmware vise normalement 69 ; utiliser un relais. |
| « File not found » | Mauvaise session, ancien nom, arrêt de l'application. |
| Security violation | EFI non signé / politique Secure Boot. |
| Fichier reçu puis unsupported image | Legacy/IA32 au lieu d'UEFI x64, ou binaire différent. |
| Téléchargement sans message | Échec du boot : ne pas considérer le POC validé. |

Sans DHCP configurable ni relais disponible, **PXE PocketInstall n'est pas
utilisable dans cette configuration réseau**, même avec Ethernet et l'APK.

## Test VM de référence

```sh
sudo apt install qemu-system-x86 ovmf
python3 scripts/qemu_pxe_boot.py
```

Aucun disque invité, TAP, bridge ou serveur DHCP sur le LAN domestique. La NIC
virtio utilise le driver OVMF, **ROM iPXE désactivée**. La capture PCAP vérifie
RRQ, octets EFI et ACK ; console + arrêt sont également exigés. Ce test utilise
le TFTP **QEMU/libslirp de référence**. Les tests UDP du serveur Kotlin de l'APK
sont exécutés séparément avec `:server-core:test`.

Résultats actuels : [VALIDATION.md](VALIDATION.md).
Analyse et sources : [research/PXE.md](../research/PXE.md).
