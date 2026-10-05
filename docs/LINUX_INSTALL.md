# Installer Debian

Dans Préparer, choisis **Linux bureau** (Debian 13 avec Xfce) ou **Linux serveur** (Debian 13 avec SSH, sans interface graphique), puis **Télécharger Debian**. Le téléphone récupère le noyau et l’initrd officiels sur `deb.debian.org`, vérifie les SHA-256 et les routes HTTP des deux profils avant de déclarer l’environnement prêt.

Les deux profils partagent les mêmes fichiers. La bibliothèque permet de supprimer cette copie commune. Aucun ZIP WinPE ni ISO Linux n’est nécessaire.

Démarre le serveur dans Installer, puis le PC en UEFI PXE IPv4. La configuration Freebox déjà utilisée pour Windows reste valable. Le script public `/boot.ipxe` sélectionne le profil actif et récupère les ressources avec le token de la session, sans saisie sur le PC.

Debian initialise son propre réseau et récupère la configuration, puis les paquets sur Internet. Crée ton compte et choisis le disque et le partitionnement dans son installateur. PocketInstall ne présélectionne aucun disque et ne préconfirme aucun effacement pour Linux. Le profil serveur installe SSH avec le compte choisi, sans mot de passe prédéfini et sans connexion root par mot de passe.

Les signaux « installateur démarré » et « installation terminée » viennent des commandes early/late de Debian. Une simple requête pour le script ou l’initrd ne suffit pas. Le premier démarrage reste à vérifier sur le PC. Une session expire après 30 minutes ; l’installation peut continuer après réception des fichiers, mais ses derniers signaux ne seront plus reçus si la session est fermée.

## Sources

- [Images Debian amd64 officielles](https://deb.debian.org/debian/dists/trixie/main/installer-amd64/current/images/)
- [Options de préconfiguration Debian](https://www.debian.org/releases/trixie/amd64/apbs04.en.html)
- [Signaux early/late de l’installateur](https://www.debian.org/releases/trixie/amd64/apbs05.en.html)
