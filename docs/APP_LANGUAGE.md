# Logo, démarrage et langue de l’application

L’icône de lancement reprend le symbole PocketInstall approuvé : une poche
bleue avec une flèche vers le bas en espace négatif, sans texte. Le PNG original
est conservé dans les ressources ; une adaptation vectorielle sert à l’icône
Android adaptative et à l’en-tête. L’icône monochrome Android 13 est également
définie.

Au lancement, le logo apparaît par un fondu et un léger agrandissement pendant
550 ms. Les animations désactivées dans les paramètres Android suppriment cette
transition. Une rotation, une recréation de l’activité ou un changement de langue
ne rejoue pas l’animation si elle était déjà terminée.

Au premier lancement après installation (ou effacement des données), l’utilisateur
choisit explicitement l’une des six langues : français, anglais américain,
allemand, espagnol, japonais ou chinois simplifié. Les noms apparaissent dans
leur propre langue. La langue de l’appareil suggère le choix initial ; une langue
non prise en charge suggère l’anglais américain. La confirmation est enregistrée
avant l’application du choix pour résister à une recréation immédiate de l’activité.

AppCompat conserve la langue sur Android 8–12 et utilise la préférence système
par application à partir d’Android 13. Le manifeste déclare les six langues pour
les paramètres Android. L’onglet Aide permet de modifier la langue ultérieurement ;
ce bouton est désactivé pendant un import ou un téléchargement.

Les ressources traduisent la navigation, les commandes, les guides, la sélection
du stockage et des profils, ainsi que les étapes de chargement WinPE. Les journaux,
les erreurs détaillées issues du serveur partagé, les messages reçus du PC, la
licence personnelle embarquée et les pages Microsoft conservent leur langue
d’origine. Le choix de langue de l’application ne change pas celui de l’image
Windows téléchargée.

## Vérification

Depuis `android` :

```sh
./gradlew :app:testDebugUnitTest :server-core:test :app:assembleDebug :app:lintDebug
```

Les tests Android locaux vérifient l’absence de confirmation initiale, la
confirmation persistante, les six tags appliqués, les suggestions régionales et
les ressources traduites avec leurs arguments. Les tests du serveur existants
restent indépendants de la langue de l’interface.

Vérification manuelle à réaliser sur téléphone ou émulateur :

1. Ouvrir une installation neuve, constater l’animation puis les six langues.
2. Choisir une langue, confirmer, vérifier les onglets et les guides.
3. Fermer et rouvrir l’app : le choix de langue ne doit plus être demandé.
4. Tourner l’appareil puis changer de langue depuis Aide ; l’introduction ne
   doit pas être rejouée et le choix doit rester enregistré.
5. Tester écran étroit, paysage et grande taille de police : les options de
   langue doivent défiler et le bouton de confirmation doit rester accessible.
6. Désactiver les animations Android : l’écran de langue ou l’app doit apparaître
   directement.
7. Sur Android 13+, changer la langue depuis les paramètres Android de l’app.
