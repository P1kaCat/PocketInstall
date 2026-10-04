# PocketInstall 3.2.0 — interface simplifiée

Trois onglets : Préparer, Installer et Aide. Les choix Windows, édition, langue, stockage et personnalisation gardent leur fonctionnement. Les explications longues se trouvent dans les boutons « ? Aide » ; URL, commandes et journaux restent dans Diagnostic. Les erreurs et la nécessité de confirmer l’effacement restent visibles.

Thèmes clair/sombre, sections compactes, choix qui passent à la ligne, boutons accessibles et aide refermable. La progression distingue un transfert terminé d’un démarrage réellement confirmé par le PC. Les contrôles d’intégrité et les confirmations de disque sont conservés.

WinPE affiche les étapes de l’installation. Les sorties DiskPart sont consignées dans X:\PocketInstall-native.log puis copiées vers W:\PocketInstall\native.log dès que ce dossier est disponible. DISM conserve sa progression réelle ; la console et les journaux restent disponibles en cas d’erreur.

Installer PocketInstall-3.2.0.apk et redémarrer le serveur puis le PC en PXE. Le ZIP WinPE et snponly.efi restent les mêmes. Pour remplacer l’ancienne attente iPXE répétitive, exporter pocketinstall.ipxe depuis Aide > Configuration Freebox et remplacer ce seul fichier dans le dossier TFTP. Aucun autre réglage Freebox à changer. Sans cet export, l’ancien écran d’attente reste présent mais le démarrage fonctionne.

Validation : contrôles ciblés des routes WinPE et de la sécurité du stockage, compilation/lint Android, ouverture native sur émulateur, navigation/aide, thèmes clair et sombre, texte à 200 % et paysage. Les captures natives sont jointes. Le nouveau rendu du terminal et l’installation physique de cette version restent à confirmer sur le PC. La version 3.1.3 a atteint le premier démarrage Windows sur le PC de l’utilisateur ; cela ne certifie pas toutes les machines ni la fin de la configuration initiale.
