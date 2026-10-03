# Publier une release APK

Le workflow `.github/workflows/release.yml` crée une vraie release GitHub, distincte
du dossier de binaires historiques versionné dans le dépôt.

## Déclenchement

Sur `main`, une modification du numéro de version Android, du workflow ou des
notes de release déclenche le build. Un lancement manuel est également possible
depuis l'onglet Actions. Le workflow n'est pas exécuté sur une pull request ni
dans un autre dépôt.

1. Augmenter `versionCode` et `versionName` dans `android/app/build.gradle.kts`.
2. Créer `releases/<versionName>/NOTES.md`.
3. Vérifier les changements et pousser sur `main`.
4. Attendre la fin du workflow **Release APK**.
5. Contrôler les assets de `releases/tag/v<versionName>`.

Le runner utilise JDK 17, SDK 36 et Build Tools 36.0.0. Il exécute les tests
`server-core`, assemble l'APK debug et lance lint. Avant publication, il vérifie
la signature de l'APK, l'identité/version Android, le binaire EFI embarqué, la
présence de la licence personnelle identique à `LICENSE` et les notices tierces.
`scripts/verify_release.py` produit le manifeste et les hashes.

La release est marquée **prerelease** tant que PocketInstall reste un POC.
Elle contient l'APK, `bootx64.efi`, `LICENSE.txt`, les notices tierces,
`release.json` et `SHA256SUMS`. Le tag pointe vers le commit réellement construit.

À partir de 0.1.3, la CI reconstruit aussi l'EFI et compare les octets à l'asset,
vérifie le relais/dnsmasq et exécute PXE natif dans QEMU/OVMF sans disque. Le
préparateur de release exige un résultat réussi pour le hash EFI courant et
au moins 30 tests de serveur, sans échec/erreur/skip. Les tests TFTP Kotlin et
la VM TFTP QEMU/libslirp sont deux validations distinctes.

Assets supplémentaires : `PXE-QEMU-result.json`, `PXE-QEMU-serial.txt`,
`PXE-QEMU-network.pcap`, `SERVER-TESTS.xml` et `PXE-relay-tools.zip`. Le ZIP
contient les sources du relais, son guide, la licence et le manifeste de hash,
sans binaire Microsoft/iPXE. Décompresser puis exécuter la préparation comme
dans `docs/PXE.md` ; elle récupère l'EFI depuis le téléphone.

Une version déjà publiée n'est jamais remplacée. Une relance du même commit
conserve la release existante ; un autre commit doit augmenter la version.

## Signature Android

Ces APK utilisent une clé **debug**, générée sur le runner. Les clés peuvent
différer entre builds et avec le POC historique. Android peut alors exiger de
désinstaller l'ancienne application ; PocketInstall n'y conserve aujourd'hui
aucune donnée utilisateur persistante.

Avant une version de production, créer une clé de signature durable, la conserver
hors Git dans un stockage protégé et configurer les secrets de signature.
Ne jamais publier de keystore privé ni le mot de passe associé. Le workflow
actuel ne prétend pas fournir cette signature de production.

## Permissions

Le workflow utilise le `GITHUB_TOKEN` temporaire fourni par GitHub, avec
`contents: write` uniquement pour ce job de publication sur `main`.
Il ne demande ni PAT personnel ni accès à d'autres dépôts. Les actions utilisées
sont épinglées à des commits. Aucun binaire Microsoft n'est inclus.

Références :
- https://docs.github.com/en/actions/tutorials/authenticate-with-github_token
- https://cli.github.com/manual/gh_release_create
