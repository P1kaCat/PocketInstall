# PocketInstall 3.1.1 — Windows et fichiers séparés

- Correction de l’erreur `No MSFT_Partition ... DriveLetter S` : validation par identifiant de volume et partition GPT, sans dépendre de la lettre EFI exposée par PowerShell.
- Choix « Windows + Mes fichiers » : C: Windows et logiciels (96/128/160/256/512 Gio), D: reste du disque. Le disque doit garder au moins 16 Gio pour D: ; toute insuffisance est détectée avant effacement.
- EFI, MSR et récupération restent masquées. La récupération est placée juste après Windows, avant D:.
- Option pour masquer C: dans l’Explorateur, sans bloquer son accès. `D:\Afficher Windows.cmd` permet de l’afficher à nouveau pour le compte courant après fermeture/réouverture de session.
- Les dossiers personnels et fichiers temporaires restent sur C:. Enregistre tes fichiers et jeux sur D: pour utiliser l’espace séparé.
- Checkpoint de reprise avant application de Windows : `REPRENDRE N` au choix du disque reprend un transfert interrompu, sans clean ni format. Nécessite un checkpoint créé par cette version, la même image et le même agencement. Ne reprend pas une application DISM déjà commencée.

## Mise à jour

Installer `PocketInstall-3.1.1.apk`. Le ZIP WinPE et la configuration Freebox restent identiques à 3.1.0. Arrêter le serveur, régler l’agencement, activer l’installation et démarrer le serveur, puis redémarrer le PC en PXE pour charger le nouvel installateur.

Pour créer D: sur un disque déjà partitionné par une ancienne version, une nouvelle installation avec effacement confirmé sur le PC est nécessaire. Un ancien WinPE déjà chargé en mémoire conserve l’ancien script.

## Validation

Contrôles ciblés des correspondances EFI/GPT, rejets de mauvais disque/volume/système de fichiers, génération des deux agencements et absence de clean/format dans la reprise. Compilation APK et contrôle syntaxique de l’installateur et du script de premier démarrage.

Le démarrage WinPE a été observé sur le PC physique. L’installation complète et le nouveau masquage dans Windows Home/Pro restent à confirmer sur le PC ; la publication ne prétend pas valider un premier démarrage Windows réel.
