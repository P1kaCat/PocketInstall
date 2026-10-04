# PocketInstall 3.1.3 — correction de la version DISM

Corrige l’arrêt « The property Build cannot be found » après DiskPart. Dans WinPE, Get-WindowsImage peut renvoyer Version sous forme de texte : le script la convertit explicitement en System.Version avant de vérifier Windows 10/11. Les contrôles d’édition et d’architecture restent actifs ; une version absente ou invalide arrête l’installation avant application de l’image.

Installer PocketInstall-3.1.3.apk, conserver la même image et la même sélection Windows/édition/taille, redémarrer le serveur et le PC en PXE pour charger le nouveau script. Aucun changement Freebox ni nouveau ZIP WinPE.

Pour l’erreur survenue avant application de Windows, saisir REPRENDRE N au choix du disque (N est le numéro du disque, par exemple REPRENDRE 0). Répéter cette phrase à la confirmation. La reprise vérifie le checkpoint, le disque, les GUID et tailles des partitions et l’identité de l’image. Elle monte les partitions existantes sans les effacer ni les formater. Si le checkpoint est refusé, ne pas lancer un nouvel effacement pour contourner l’erreur.

La taille automatique et Windows + Mes fichiers sont conservés. Contrôles ciblés de la version DISM, des partitions, de la reprise et de la livraison HTTP du script ; compilation APK. L’installation physique complète reste à confirmer sur le PC.
