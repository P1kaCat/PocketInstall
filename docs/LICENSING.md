# Provenance et distribution

Le code PocketInstall est sous MIT. Le petit binaire EFI est construit avec des
headers et le startup/relocation GNU-EFI ; ses notices BSD sont fournies dans
`boot/THIRD_PARTY_NOTICES.txt` et dans les assets Android.

AndroidX/Compose (Google), Kotlin et kotlinx.coroutines (JetBrains), Gradle et son
wrapper sont sous Apache 2.0. La licence et les notices sont dans `android/`.
Le code natif de chemins Skia inclus par AndroidX Graphics Path a aussi sa
notice BSD dans les assets.
JUnit est une dépendance de tests et ne fait pas partie de l'APK.

iPXE, wimboot, QEMU, OVMF et Microsoft ADK sont des dépendances **externes** des
étapes de laboratoire/de préparation, pas intégrées automatiquement à une licence
MIT globale. Aucun binaire Microsoft, Windows, WinPE, iPXE ou wimboot n'est livré
dans cette archive ou dans l'APK POC. Les ressources GNU-EFI du seul EFI fourni
sont couvertes par les notices indiquées ci-dessus.

La recherche n'a pas établi les droits permettant de redistribuer commercialement
une image WinPE personnalisée ou des images Windows. Obtenir les média officiels,
conserver les licences ADK/add-on/REDIST de la version exacte et faire vérifier ce
périmètre avant publication d'images. Le projet propose de construire le bundle
utilisateur en amont et d'importer ses propres média, pas de télécharger un WIM
communautaire prétendument officiel.

Un SHA-256 calculé par PocketInstall n'est pas une signature Microsoft. Vérifier
le hash publié de l'ISO exacte puis tracer l'extraction ; les futurs manifests
et mises à jour devront avoir une racine de confiance indépendante de HTTP.
