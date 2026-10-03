# Binaires historiques du POC 0.1.0

Cette version reste sous [MIT](LICENSE-MIT.txt). La nouvelle licence personnelle
ne retire pas les droits qu'elle accordait. Utiliser la
[release actuelle](https://github.com/P1kaCat/PocketInstall/releases/tag/v0.1.1-poc)
pour l'APK contenant la nouvelle licence.

Ce dossier fournit les binaires déjà construits du prototype :

| Fichier | Utilisation |
|---|---|
| [PocketInstall-0.1.0-poc-debug.apk](PocketInstall-0.1.0-poc-debug.apk) | Application Android 8+ ; installer sur le téléphone. APK debug signé avec une clé de développement. |
| [bootx64.efi](bootx64.efi) | Application EFI x64 minimale, non signée, également embarquée dans l'APK. |
| [SHA256SUMS](SHA256SUMS) | Vérification SHA-256 des binaires et du bundle. |
| [release.json](release.json) | Versions, tailles, hashes et état de validation. |
| [PocketInstall.repository.bundle](PocketInstall.repository.bundle) | Historique Git du code ayant servi à construire les binaires. |

Sur GitHub, ouvrir le fichier puis utiliser le bouton de téléchargement. Un
compte autorisé est nécessaire tant que le dépôt est privé.

Pour vérifier les fichiers téléchargés sous Linux, les placer dans le
même dossier que `SHA256SUMS`, puis :

```sh
sha256sum -c SHA256SUMS
```

Sur macOS, utiliser `shasum -a 256 -c SHA256SUMS`.

Sur Windows, comparer la valeur de `Get-FileHash -Algorithm SHA256` à celle
indiquée dans `SHA256SUMS`.

Le commit source du build est
`e9b25ed19364732123132fb0f32416b0f11fe4ba`. Il est conservé dans le bundle ; la
publication GitHub ajoute les instructions de clonage et ce dossier de binaires.
Pour consulter ce checkout exact hors ligne :

```sh
git clone PocketInstall.repository.bundle PocketInstall-build-source
```

Le boot HTTP natif a réussi dans QEMU/OVMF, avec le serveur Python puis le serveur
Kotlin utilisé dans l'application. Aucun téléphone réel ni PC physique n'a
encore été validé. Aucun installeur Windows ou binaire Microsoft n'est inclus.

Suivre [la procédure de test](../../docs/TESTING.md) : premier essai sans disque
en VM, puis démarrage EFI sur matériel compatible, message de succès et arrêt.
Le firmware doit accepter le binaire EFI non signé dans la configuration de test.
