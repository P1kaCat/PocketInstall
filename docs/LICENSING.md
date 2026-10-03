# Licences et provenance

## Code original de PocketInstall

À partir de **0.1.1-poc**, les éléments originaux explicitement distribués sous
cette licence sont soumis à la [PocketInstall Personal Use License 1.0](../LICENSE),
identifiée par `LicenseRef-PocketInstall-Personal-1.0`.

Elle autorise l'installation, les sauvegardes privées et toutes les modifications
pour un usage personnel non commercial. Elle interdit la redistribution de
l'application, de ses sources et de versions modifiées sans accord écrit, même
gratuitement, sous un autre nom ou dans une boutique d'applications. Le code est
consultable ; ce projet n'est pas sous une licence open source.

L'APK embarque le texte dans `assets/licenses/PocketInstall-Personal.txt`.
Le bouton **Lire la licence** permet de consulter ce texte hors ligne.

## Versions historiques et limites du changement

Le POC 0.1.0 a été publié sous MIT. Son texte est conservé dans
[releases/0.1.0-poc/LICENSE-MIT.txt](../releases/0.1.0-poc/LICENSE-MIT.txt) et
dans l'historique Git. Le changement ne retire pas rétroactivement les
autorisations déjà accordées sur ces copies ou contributions. Il ne suffit
donc pas à interdire la réutilisation du code historique resté disponible sous
MIT. Le texte MIT autorise notamment la modification et la redistribution :
https://choosealicense.com/licenses/mit/

Les droits impératifs prévus par la loi et les autorisations minimales des
conditions de GitHub restent applicables. Un dépôt public sur GitHub permet
notamment la consultation et certains forks selon les conditions du service ;
une licence ne permet pas de promettre leur interdiction absolue.
https://choosealicense.com/no-permission/

Le dépôt reste privé : cette opération ne change pas sa visibilité.

## Composants tiers

La licence personnelle ne s'applique pas aux composants tiers pris séparément
et ne réduit pas les droits que leurs propres licences accordent.

Le petit binaire EFI utilise les headers et le startup/relocation GNU-EFI ;
ses notices BSD sont fournies dans `boot/THIRD_PARTY_NOTICES.txt`, dans les
assets Android et avec la release.

AndroidX/Compose (Google), Kotlin et kotlinx.coroutines (JetBrains), Gradle et son
wrapper sont sous Apache 2.0. La licence et les notices sont dans `android/`.
Le fichier de notices Apache conserve également les notices additionnelles de
dépendances qu'il contient ; les mentions MIT de ces composants ne désignent pas
la licence actuelle de PocketInstall.

Le code natif de chemins Skia inclus par AndroidX Graphics Path conserve sa
notice BSD dans les assets. JUnit est une dépendance de tests, absente de l'APK.

iPXE, wimboot, QEMU, OVMF et Microsoft ADK sont des dépendances **externes** des
étapes de laboratoire/de préparation. Aucun binaire Microsoft, Windows, WinPE,
iPXE ou wimboot n'est livré dans l'APK POC.

## Microsoft et vérification des images

La recherche n'a pas établi les droits permettant de redistribuer commercialement
une image WinPE personnalisée ou des images Windows. Obtenir les média officiels,
conserver les licences ADK/add-on/REDIST de la version exacte et faire vérifier ce
périmètre avant publication d'images. Le projet propose de construire le bundle
utilisateur et d'importer ses propres média.

Un SHA-256 calculé par PocketInstall n'est pas une signature Microsoft. Vérifier
le hash publié de l'ISO exacte puis tracer l'extraction ; les futurs manifests
et mises à jour devront avoir une racine de confiance indépendante de HTTP.
