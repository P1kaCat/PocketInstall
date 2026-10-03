# Roadmap

Les cases ne sont cochées qu'après validation du critère indiqué.

## Proof of Concept

- [x] Recherche des protocoles, firmware, Android, WinPE et des limites sans fil.
- [x] Architecture et contrat HTTP de fichiers statiques.
- [x] Compiler un EFI x64 autonome, message de succès, arrêt, aucun accès disque.
- [x] Test de ce binaire sous QEMU/OVMF avec **HTTP**, sans boot PXE/iPXE implicite.
- [x] Tester le serveur Kotlin de l'APK avec HTTP Boot natif en VM.
- [x] Compiler l'APK Android POC et vérifier son asset EFI et sa signature APK.
- [ ] Servir le même binaire depuis Android non rooté ; journaliser le transfert.
- [ ] Constater le message et l'arrêt sur un PC physique, firmware/version tracés.

## MVP

- [ ] Valider iPXE + wimboot + WinPE de test, d'abord en Ethernet PC.
- [ ] Import SAF d'un bundle WinPE utilisateur, stockage privé et manifeste.
- [ ] Authentifier les hashes attendus indépendamment du transport HTTP.
- [ ] Télécharger une image Windows en VM et valider intégrité/reprise > 4 Gio.
- [ ] Inventaire disques fiable et plan affiché ; confirmation locale `ERASE`.
- [ ] Installer Windows 11 dans une VM jetable, boot et WinRE vérifiés.

## Alpha

- [ ] Home/Pro par index réel, langue et architecture vérifiées ; x64 d'abord.
- [ ] Intégration officielle des média ; revue des licences avant redistribution.
- [ ] Recovery : sauvegarde, terminal local, disques en lecture seule par défaut.
- [ ] Réparation EFI/BCD avec sauvegarde et confirmation des écritures.
- [ ] Diagnostic SSD sans promesse de récupération de données impossible.
- [ ] Mesures OEM, pertes Wi-Fi, batterie, chauffe, permissions SDK 37.

## Beta

- [ ] Chemin Secure Boot complet, signatures, db/dbx et CA 2023 testés.
- [ ] Profil Clean puis Gaming/Dev/Custom explicites, audités et réversibles.
- [ ] Essais matériels indépendants et matrice de compatibilité publiée.
- [ ] Étude du sans-fil après le firmware : iPXE/SNP, WinPE limité, alternative Linux.

## v1

- [ ] Installation et Recovery reproductibles sur le matériel officiellement listé.
- [ ] Provenance des média, confiance réseau, mises à jour et rollback documentés.
- [ ] Parcours incompatibilité utile, aucune prétention de support universel.

Les fonctionnalités Dev (Git, VS Code, runtimes, WSL) seront des choix post-install.
WSL et certains composants nécessitent Windows, virtualisation et parfois un
redémarrage ; ils ne sont pas « préinstallés » simplement en retirant des Appx.
