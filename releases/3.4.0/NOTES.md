# PocketInstall 3.4.0 — Linux bureau et serveur

- Nouveau choix : Windows, Debian 13 bureau Xfce, Debian 13 serveur sans interface graphique avec SSH.
- Bouton Télécharger Debian : téléchargement officiel du noyau et de l’initrd (environ 55 Mio), vérification SHA-256 et vérification réelle des routes HTTP avant activation.
- Démarrage PXE automatique avec la configuration Freebox existante : aucun token à recopier, aucune commande à taper sur le PC.
- Progression distincte : iPXE connecté, chargement Debian, installateur démarré, installation terminée. Les deux derniers états demandent un signal de l’installateur, pas une simple requête de téléchargement.
- Compte utilisateur et partitionnement choisis dans l’installateur sur le PC. Les options Windows de partitionnement et débloat ne s’appliquent pas à Linux.

- Bibliothèque : liste des images et environnements conservés sur le téléphone, avec suppression des copies devenues inutiles.
- WinPE : téléchargement et import automatiques depuis GitHub ; connexion GitHub si le dépôt privé exige une authentification.
- Page GitHub repensée, retrait des anciens workflows de publication et licence restrictive avec exception de contribution.

## Utilisation

Installe `PocketInstall-3.4.0.apk`, choisis Linux bureau ou Linux serveur dans Préparer, puis Télécharger Debian. Dans Installer, démarre le serveur et le PC en UEFI PXE IPv4. Le PC doit avoir accès à Internet pour télécharger les paquets Debian. Termine la création du compte et le choix du disque sur le PC.

Si ta Freebox démarre déjà PocketInstall automatiquement, conserve sa configuration. Pour une première configuration : `snponly.efi` et l’export `pocketinstall.ipxe` de l’application dans le dossier TFTP de la Freebox ; serveur TFTP = IP Freebox, fichier de démarrage = snponly.efi. Réserve une IP au téléphone.

Le profil serveur est léger car il n’installe aucun bureau graphique ; il n’existe pas de distribution universellement la plus optimisée pour tous les usages. SSH utilise le compte créé sur le PC, sans mot de passe prédéfini.

Windows continue à utiliser son bundle [PocketInstall-WinPE-x64.zip de la 3.2.0](https://github.com/P1kaCat/PocketInstall/releases/download/v3.2.0/PocketInstall-WinPE-x64.zip). Ce ZIP est inutile pour Linux.

La validation Linux couvre le téléchargement officiel, les routes des deux profils et le démarrage du véritable installateur Debian en VM sans disque. Elle ne prouve pas l’installation complète ni le démarrage sur chaque PC physique. Le premier démarrage doit être constaté sur le PC. La session du téléphone dure 30 minutes : si elle expire après le chargement, Debian peut continuer mais les signaux de progression ne parviendront plus à l’application.

Le logo, l’animation d’accueil et le choix des six langues de la 3.3.0 sont conservés. Les nouvelles sections Linux/bibliothèque et leurs diagnostics restent en français.
