# Procédures de test

Toutes les commandes partent de la racine `PocketInstall`. Les résultats observés
sont dans `VALIDATION.md`, distincts des procédures ci-dessous.

## 1. Build du POC EFI

Linux x64 :

```sh
sudo apt install build-essential binutils gnu-efi python3
make -C boot
python3 scripts/sync_android_boot.py
```

Le `.efi` préparé dans les assets peut être utilisé sans reconstruire GNU-EFI.
Sa taille et son hash sont dans `android/app/src/main/assets/boot/manifest.json`.

## 2. VM avec HTTP Boot natif, zéro disque invité

```sh
sudo apt install qemu-system-x86 ovmf python3-venv
python3 -m venv .venv-lab
. .venv-lab/bin/activate
pip install virt-firmware==26.9
python3 scripts/qemu_http_boot.py
```

Le script configure une **copie** des variables OVMF avec BootNext/URI, démarre un
serveur sur loopback et donne au guest une NIC virtio via SLIRP. Le guest obtient
son IPv4 via DHCP virtuel ordinaire ; l'URI est préconfigurée. Aucun DHCP domestique,
TAP, root, USB ou disque cible n'est utilisé. Le firmware variables writable est
le seul backing file modifiable de la VM, ce n'est pas un SSD invité.

Une NIC virtio est utilisée car OVMF fournit `VirtioNetDxe` sans ROM externe.
Le script ajoute aussi virtio-rng : les bibliothèques réseau de cette version
d'EDK II exigent `EFI_RNG_PROTOCOL`. Une VM sans source d'aléa peut avoir les
drivers HTTP dans le firmware sans que sa pile réseau soit activée.

Le ROM iPXE de la NIC est désactivé (`romfile=`). Le test exige :

1. GET HTTP du binaire, pas TFTP.
2. `PocketInstall boot successful` dans la console UEFI série.
3. Message d'arrêt et fin de la VM après le timeout du programme EFI.

Un simple GET ne fait pas passer le test. Les traces sont `lab-output/http.jsonl`,
`serial.log`, `qemu.log`, `result.json`. L'URL de session apparaît dans la console
firmware brute ; masquer les sessions avant de publier les traces d'un réseau réel.

### Si l'OVMF de la distribution n'a pas HTTP Boot

Le résultat peut être `Not Found` puis un shell EFI sans aucune requête HTTP. Ce
n'est pas une preuve que le serveur est faux. Ne pas remplacer silencieusement
le boot par le ROM iPXE de QEMU : ce serait une autre chaîne.

Construire un OVMF de laboratoire :

```sh
sudo apt install git build-essential uuid-dev nasm acpica-tools
bash scripts/build_ovmf_http.sh
python3 scripts/qemu_http_boot.py \
  --code lab-output/edk2/Build/OvmfX64/RELEASE_GCC/FV/OVMF_CODE.fd \
  --vars lab-output/edk2/Build/OvmfX64/RELEASE_GCC/FV/OVMF_VARS.fd
```

Ce firmware de test autorise explicitement HTTP clair :
`NETWORK_HTTP_BOOT_ENABLE=TRUE`, `NETWORK_ALLOW_HTTP_CONNECTIONS=TRUE`,
`NETWORK_TLS_ENABLE=FALSE`, x64/GCC/RELEASE, FD 4 MiB. Il n'est jamais destiné à
flasher un vrai PC. L'EDK II choisi est épinglé (`edk2-stable202605`) et utilise le
toolchain `GCC`, plus `GCC5` supprimé dans cette version.

## 3. Le même serveur Kotlin que l'APK

JDK 17 complet (incluant `javac`), Android SDK 36 et Build Tools 36.0.0 :

```sh
cd android
./gradlew :server-core:test :app:assembleDebug
./gradlew :server-core:installDist
```

Dans un premier terminal à la racine :

```sh
android/server-core/build/install/server-core/bin/server-core boot/build/bootx64.efi 127.0.0.1 8 8080
```

Il affiche `BOOT_URL=http://127.0.0.1:8080/<session>/bootx64.efi`. Pour le guest
SLIRP, remplacer **seulement** l'adresse par `10.0.2.2`, garder port/session/path et
exécuter dans un deuxième terminal :

```sh
python3 scripts/qemu_http_boot.py --external-url http://10.0.2.2:8080/SESSION/bootx64.efi --code CHEMIN_CODE --vars CHEMIN_VARS
```

Le programme est le module `server-core` effectivement lié à l'APK. Les tests
JVM font de vrais échanges TCP : GET/HEAD/Range, taille >4 Gio synthétique,
méthodes d'écriture, traversée de chemin, en-têtes malformés, sous-réseau, expiry
et token périmé. Le test de 5 Gio n'écrit pas un fichier de 5 Gio : il vérifie
les offsets Long et les headers avec un InputStream synthétique.

Version automatisée du même essai, après `:server-core:installDist` :

```sh
python3 scripts/test_kotlin_http_boot.py \
  --code lab-output/edk2/Build/OvmfX64/RELEASE_GCC/FV/OVMF_CODE.fd \
  --vars lab-output/edk2/Build/OvmfX64/RELEASE_GCC/FV/OVMF_VARS.fd
```

Elle lance le serveur sur un port libre, capture sa session, démarre QEMU,
vérifie le journal Kotlin et la console EFI, puis ferme le serveur. Les contrôles
Android (permissions, service, batterie, réseau réel) restent des tests séparés.

## 4. Téléphone Android réel, puis VM

Installer l'APK sur le téléphone (autorisation d'installation de la source locale
si Android la demande). Aucune liaison USB et aucun root ne sont requis. Cette
installation de l'application Android est distincte d'une application sur le PC.

1. Téléphone sur un Wi-Fi privé normal, avec une IPv4 10/172.16–31/192.168.
2. Ouvrir PocketInstall, choisir le réseau puis « Démarrer le test EFI ».
3. Copier l'URL exacte ; l'écran reste allumé quand l'application est visible.
4. Depuis le poste de laboratoire sur le même LAN, vérifier GET et HEAD via curl :

```sh
curl -I http://IP_TELEPHONE:8080/SESSION/bootx64.efi
curl -o /tmp/pocketinstall.efi http://IP_TELEPHONE:8080/SESSION/bootx64.efi
python3 scripts/verify_efi.py /tmp/pocketinstall.efi
```

5. Démarrer la VM avec `--external-url` égal à l'URL Android, et le firmware HTTP
   validé. Vérifier les logs Android **et** message/arrêt VM. Le téléphone verra
   généralement l'IP du poste hôte à cause du NAT SLIRP, pas l'IP privée du guest.
6. Arrêter la session. La précédente URL ne doit plus répondre. Redémarrer et
   vérifier qu'une nouvelle session est affichée.

Essais supplémentaires après le premier succès : écran éteint, application en
arrière-plan, sortie/reconnexion Wi-Fi, IP changée, port occupé, requêtes invalides,
notifications refusées, Android 16 restrictions LAN opt-in, puis migration target
37 avec permission LAN. Un foreground service ne garantit pas tous les comportements
de Doze et des constructeurs ; garder l'écran allumé pour le premier boot physique.

## 5. Vrai PC : premier essai sans écriture disque

Le **POC EFI fourni** n'utilise aucun disque. Il peut fonctionner avec le SSD
absent, vide ou contenant un Windows cassé. Pour la preuve matérielle la plus
simple à auditer, déconnecter le SSD si cela est facilement possible ; ce n'est
pas un prérequis technique de l'application EFI.

1. Noter modèle/version BIOS et NIC, et conserver la clé BitLocker si le PC est
   chiffré avant toute modification de Secure Boot.
2. Consulter le manuel **du modèle exact** : HTTP Boot, mode manuel et Wi-Fi préboot.
3. Relier le PC au LAN par l'interface réellement disponible dans le firmware.
   Ethernet vers le routeur est compatible avec le téléphone en Wi-Fi. Un PC
   entièrement sans fil exige un support Wi-Fi UEFI explicite.
4. Démarrer la session Android, vérifier IP/URL et la portée depuis un autre appareil.
5. Dans l'UEFI activer la pile réseau et sélectionner **HTTP Boot IPv4**. Saisir
   l'URL exacte. Une entrée « PXE IPv4 » ne suffit pas.
6. Adapter la politique Secure Boot au binaire non signé sur la machine de test,
   sans modifier les clés de confiance. Le logiciel ne le fait jamais à ta place.
7. Photographier `PocketInstall boot successful`. Il attend une touche/30 secondes
   puis éteint le PC. Ne lancer aucune autre image d'installation.
8. Arrêter le serveur et restaurer les paramètres de boot modifiés.

La saisie d'une option de boot dans le firmware peut écrire sa NVRAM. Le POC
n'écrit pas au SSD, ne change pas BCD/ESP et ne fait pas de test de partitionnement.
Réussite = **log de transfert + message exécuté + arrêt**, pas « client connecté ».

## 6. WinPE, seulement après

Suivre `winpe/README.md`. Première VM **sans disque** puis Ethernet PC. Test physique
WinPE avec tous les SSD déconnectés : le système Windows PE peut énumérer/mounter
des volumes même si notre startnet n'exécute aucun installateur. Ne pas appliquer
la garantie « aucun accès disque du code EFI » à un OS complet sans ce contrôle.

## Fiche de résultat matériel

```text
Date / testeur :
Modèle / SKU / BIOS :
NIC / Wi-Fi chipset :
HTTP manuel / auto :
HTTP port 8080 autorisé :
Secure Boot / dbx / CA :
Type de LAN / DHCP / isolation :
SHA256 EFI :
GET reçu / message affiché / arrêt :
Disques présents / aucune commande disque :
WinPE (test séparé) / Ethernet / erreurs :
```
