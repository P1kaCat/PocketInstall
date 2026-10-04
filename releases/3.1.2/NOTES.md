# PocketInstall 3.1.2 — taille Windows automatique

Le mode « Windows + Mes fichiers » utilise désormais **Dimensionner automatiquement Windows** par défaut. Les 128 Gio fixes ne sont plus la valeur automatique.

La capacité de C: est estimée à partir des métadonnées de l’édition réellement sélectionnée :

- Taille de l’édition + 10 Gio de marge pour les temporaires + 16 Gio de marge pour les mises à jour.
- Espace de pointe du transfert : édition + fichier WIM/ESD temporaire + 2 Gio de travail.
- Plancher de 64 Gio pour Windows 11, 32 Gio pour Windows 10.
- Maximum des trois, arrondi au multiple de 4 Gio supérieur.

D: reçoit le reste du disque, avec au moins 16 Gio. Il s’agit d’une estimation de capacité, pas d’un quota sur les fichiers temporaires ni d’une garantie d’espace libre après installation de logiciels. Les fichiers d’échange, la veille prolongée et les mises à jour peuvent faire évoluer l’espace occupé.

La taille est affichée avant démarrage. WinPE recalcule le plan avant effacement, puis vérifie la taille signalée par DISM avant d’appliquer Windows. Si les métadonnées sont absentes, le mode automatique ne prétend pas fonctionner : il demande un choix manuel. Les tailles manuelles restent disponibles et sont refusées si trop petites.

Installer `PocketInstall-3.1.2.apk`. Aucun changement Freebox ou ZIP WinPE. Redémarrer le serveur puis le PC en PXE pour charger le nouvel installateur. Une nouvelle répartition du disque demande toujours la confirmation d’effacement sur le PC.

Validation ciblée des tailles, éditions, métadonnées invalides, limites et agencement, et compilation APK. Installation physique complète et espace final occupé à confirmer sur le PC.
