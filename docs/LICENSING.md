# Licences et provenance

Depuis la version 3.4.0, les éléments originaux expressément placés sous [LICENSE](../LICENSE) utilisent **PocketInstall Contribution License 2.0**, `LicenseRef-PocketInstall-Contribution-2.0` : utilisation personnelle de l’application officielle, aucune republication ou version dérivée distribuée sans accord écrit. Les modifications sont permises uniquement pour préparer, tester en privé et soumettre une contribution au dépôt officiel. Voir [CONTRIBUTING.md](../CONTRIBUTING.md).

Le même texte est embarqué dans `assets/licenses/PocketInstall-Personal.txt`, consultable hors ligne depuis Aide. Le nom historique de cet asset est conservé pour compatibilité.

## Droits antérieurs et plateformes

La nouvelle licence ne révoque pas les autorisations accordées sur les copies antérieures. Le POC 0.1.0 et ses contributions sous MIT gardent leurs droits, avec [le texte historique](../releases/0.1.0-poc/LICENSE-MIT.txt). Les versions sous licence personnelle 1.0 gardent les permissions accordées par cette version de la licence. Les droits légaux impératifs et les autorisations minimales liées aux conditions de GitHub restent applicables. La visibilité du dépôt reste inchangée.

## Composants tiers

La licence PocketInstall ne remplace ni ne restreint les licences tierces. GNU-EFI conserve ses notices BSD. AndroidX/Compose, Kotlin, kotlinx.coroutines et Gradle conservent leurs licences et notices, notamment Apache 2.0. Les ressources Skia conservent leurs notices BSD. JUnit est réservé aux tests.

`snponly.efi` (iPXE) est un programme séparé fourni dans l’APK et la release, sous sa licence GPL propre. L’archive `iPXE-source.tar.gz` correspondant au chargeur accompagne la release ; les notices se trouvent dans les sources et les assets. wimboot et les composants Microsoft restent dans le bundle WinPE, avec leurs propres conditions. Aucun Windows complet ou image Microsoft n’est incorporé dans l’APK.

Debian est téléchargé séparément depuis son miroir officiel. Son noyau, son initrd et ses paquets restent soumis à leurs licences respectives. Les images Windows doivent provenir des médias officiels correspondant aux droits de l’utilisateur. Un hash d’intégrité PocketInstall ne remplace pas une signature de l’éditeur.

## Références

- [Licence d’un dépôt et droits GitHub](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository)
- [Licences GNU et programmes agrégés](https://www.gnu.org/licenses/gpl-faq.html#MereAggregation)
