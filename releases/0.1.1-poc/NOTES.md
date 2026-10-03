# PocketInstall 0.1.1-poc

Prototype Android non rooté pour servir un EFI minimal via UEFI HTTP Boot.

## Changements

- Licence PocketInstall Personal Use License 1.0 : utilisation et modifications
  privées pour usage personnel non commercial ; redistribution et publication
  de l'application ou de ses versions modifiées soumises à accord écrit.
- Licence consultable hors ligne via le bouton « Lire la licence ».
- Licences et notices des composants tiers conservées.
- Publication automatisée avec tests TCP, lint Android, vérification de signature,
  contrôle des assets et hashes SHA-256.

## Télécharger et tester

Installer **PocketInstall-0.1.1-poc-debug.apk** sur Android 8+.
Les fichiers EFI, licence, notices et hashes sont joints à cette release.

Il s'agit d'un APK **debug**, signé par une clé de développement générée lors du
build GitHub. Si Android refuse la mise à jour du POC précédent pour signature
différente, désinstaller l'ancienne application avant l'installation.
Ce mécanisme n'est pas encore une signature de production stable.

Le boot HTTP natif a été validé en QEMU/OVMF pour le POC initial, avec le serveur
Python puis le serveur Kotlin de l'application. Aucun test téléphone + PC
physique n'est encore validé. Le firmware doit prendre en charge HTTP Boot et
accepter le petit EFI non signé selon sa politique Secure Boot.

L'EFI affiche « PocketInstall boot successful » puis arrête le PC ; aucune
écriture disque, installation Windows ou fonction de réparation n'est exécutée.

Les versions précédemment publiées sous MIT conservent les autorisations déjà
accordées. La nouvelle licence ne s'applique pas rétroactivement à ces copies.
