# Publication et validation

La version 3.4.0 utilise `.github/workflows/release-linux.yml`. Une branche `codex/linux-install` construit l’APK, vérifie les routes Linux et WinPE et conserve la preuve déjà obtenue de démarrage Debian dans une VM sans disque après vérification que le code Linux est identique. Le job conserve les rapports et journaux, puis l’APK, le chargeur iPXE et sa source dans un artifact.

Après validation, le merge sur `main` portant `[publish-linux]` publie cet APK sans le recompiler. Le workflow exige le même arbre Git que celui du build réussi, vérifie les hashes et crée la prerelease. Le bundle WinPE existant reste disponible dans la 3.2.0 et peut être importé automatiquement par l’application ; il n’est pas nécessaire pour Linux.

Les anciens workflows propres à une version ont été retirés. `build-winpe.yml`, `validate.yml` et `release.yml` conservent le parcours de reconstruction et de validation Windows pour les futures modifications qui le nécessitent. Le marqueur de publication Linux évite de relancer ces laboratoires pour la présente mise à jour.

## Préparer une nouvelle version

1. Augmente `versionCode` et `versionName` dans `android/app/build.gradle.kts`.
2. Écris `releases/<version>/NOTES.md` et adapte le workflow de publication si son numéro est explicite.
3. Compile avec JDK 17, SDK Android 36 et Build Tools 36.0.0. Vérifie les comportements modifiés et distingue transfert, démarrage et installation complète.
4. Publie les fichiers correspondant au code validé, leurs SHA-256 et les sources/notices exigées par les composants tiers.

Le téléchargement Linux vérifie les SHA-256 du miroir HTTPS Debian. Le ZIP WinPE téléchargé automatiquement est épinglé au SHA-256 de la release officielle 3.2.0 ; si ce bundle change dans une future version, il faut actualiser ensemble son URL et son empreinte.

## Signature et données

Les APK actuels utilisent une signature debug de CI, sans promesse de clé identique entre les builds. Une installation Android peut donc refuser une mise à jour pour signature différente. Les images Windows, WinPE et Debian sont conservées dans les fichiers privés de l’application : une désinstallation les supprime. La bibliothèque permet de les gérer sans désinstaller l’application.

Une distribution de production doit utiliser une clé de signature durable conservée hors Git. Le workflow de publication emploie le token GitHub temporaire et `contents: write` uniquement dans le job de publication. Les actions sont épinglées. Aucune image Windows complète n’est intégrée à l’APK.
