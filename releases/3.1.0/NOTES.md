PocketInstall 3.1.0 — téléchargement Windows depuis le téléphone

- Nouveau bouton « Télécharger et préparer Windows » : récupération d’un lien temporaire sur le site officiel Microsoft, téléchargement de l’ISO x64, extraction et import automatiques.
- Choix Windows 10 / Windows 11, Home / Pro et langue français / anglais US.
- Progression, annulation et notification permettant de garder le téléchargement actif en arrière-plan.
- Validation de la taille téléchargée, du format et de l’édition avant de déclarer Windows prêt. L’image précédente reste disponible en cas d’échec.
- Import manuel disponible en secours si Microsoft demande une interaction ou refuse le téléchargement. En cas de coupure, relancer le bouton ; le téléchargement recommence.

Utilisation : installer PocketInstall-3.1.0.apk, arrêter le serveur, choisir Windows / édition / langue, puis « Télécharger et préparer Windows ». Prévoir environ 15 à 20 Go libres. Une fois l’image prête, activer « Préparer l’installation au prochain démarrage PXE », démarrer le serveur, puis démarrer le PC en PXE. Le choix du disque et son effacement restent confirmés sur le PC.

La configuration Freebox et le ZIP WinPE sont inchangés depuis 0.3.0-install-preview. Aucun nouveau réglage de la box si le démarrage WinPE fonctionne déjà.

Validation de cette version : compilation APK et contrôles ciblés des liens / transferts HTTP. Le téléchargement Microsoft sur un téléphone physique n’est pas encore confirmé ; la page Microsoft reste disponible pour intervenir si nécessaire. Cette préversion ne certifie pas une installation Windows complète sur le PC physique.
