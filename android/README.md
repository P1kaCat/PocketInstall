# Application Android POC

Kotlin/Compose, Android 8+ (API 26), compile/target 36, JDK 17 complet,
Gradle 8.13 et AGP 8.13.2. Les versions sont épinglées et le wrapper vérifie le
SHA-256 de la distribution Gradle.

Ouvrir **ce dossier** dans Android Studio, installer SDK 36 / Build Tools 36.0.0
et accepter les licences SDK dans l'IDE. Laisser Android Studio définir
`local.properties` ; ce fichier local n'est pas versionné.

```sh
./gradlew :server-core:test :app:assembleDebug
```

APK : `app/build/outputs/apk/debug/app-debug.apk`. Le `.efi` déjà préparé est dans
les assets ; il peut être régénéré depuis la racine avec `make -C boot` puis
`python3 scripts/sync_android_boot.py`.

`MainActivity` choisit une interface LAN privée et affiche IP, URL avec token,
requêtes et IP clientes observées récemment. Ces IP ne sont pas des sessions PC
authentifiées et ne prouvent pas l'exécution EFI.

`PocketInstallService` démarre à une action utilisateur, passe au premier plan
`connectedDevice`, garde un wake lock borné, ferme à 30 minutes et arrête à perte
du réseau ou changement d'IP. Il n'est ni exporté ni sticky et ne possède pas
de receiver au boot du téléphone. Les logs sont bornés à 50 lignes en mémoire,
et les URL/tokens n'y sont pas enregistrés. L'URL est affichée/copier dans l'UI
car elle doit être saisie dans l'UEFI ; ne pas la publier pendant une session.

Le même serveur Kotlin/JVM est testé sans Android et lié à l'APK. Il n'expose que
`bootx64.efi` dans cette version. Pas d'import WinPE, d'image Windows, de debloat ou
de partitionnement implémentés dans l'application.

`usesCleartextTraffic=false` concerne les clients HTTP Android qui respectent
cette politique. Il ne bloque pas ce serveur entrant basé sur ServerSocket ;
le transport firmware du POC reste HTTP clair. Aucune permission root, USB,
localisation, accès global au stockage ou Bluetooth n'est demandée.

Target 36 : les docs Android attribuent encore l'accès LAN via INTERNET.
Pour target 37, ajouter `ACCESS_LOCAL_NETWORK`, le consentement runtime, la gestion
du refus/révocation et les tests d'inbound socket. Ne pas augmenter targetSdk sans
implémenter ce changement. Tests Android 16 LAN opt-in distincts du parcours normal.

Le maintien écran éteint dépend de Doze et des politiques OEM ; tester sur le
téléphone réel. La fermeture doit échouer de manière sûre après expiration et
les requêtes vérifient aussi la deadline avec elapsedRealtime, qui inclut la veille.

## Licence et publication

Le bouton **Lire la licence** ouvre le texte embarqué de la licence personnelle.
Les modifications privées pour usage personnel sont autorisées ; la
redistribution nécessite un accord écrit. Les licences des composants tiers sont
conservées dans les assets et ne sont pas remplacées.

Le workflow [Release APK](../.github/workflows/release.yml) construit les versions,
exécute les tests TCP et lint, vérifie la signature et les licences embarquées,
puis crée une vraie release GitHub avec l'APK. Les détails de signature debug et
les règles de publication sont dans [docs/RELEASING.md](../docs/RELEASING.md).

## Câble USB expérimental

Sélectionner le mode USB, ouvrir les paramètres réseau et activer le partage USB
du téléphone, puis revenir et actualiser. Une interface privée compatible doit
être visible ; les noms OEM non reconnus ne sont pas sélectionnés.
Le serveur est lié à son IP et fermé si elle disparaît/change. Aucune API cachée,
émulation mass-storage ou permission root n'est ajoutée.

Le choix USB sert seulement le petit EFI : il ne certifie pas la reconnaissance
RNDIS/NCM ni HTTP Boot dans l'UEFI et ne valide pas WinPE par USB.
Voir [docs/USB_CABLE.md](../docs/USB_CABLE.md).
