# PocketInstall avec Freebox Révolution

Pour Windows, le bouton **Télécharger WinPE depuis GitHub** de la 3.3.0 récupère et importe le ZIP automatiquement (connexion GitHub si nécessaire). Pour Debian, choisis Linux bureau ou serveur puis **Télécharger Debian** : WinPE est inutile. La configuration de la box décrite ci-dessous sert aux deux parcours.

## Première configuration

Télécharger dans la release privée : l'APK, `PocketInstall-WinPE-x64.zip` et **le nouveau** `snponly.efi`. L'ancien chargeur 0.2.0 ouvrait seulement une console ; remplacer ce fichier est indispensable. Le bundle de cette version inclut WinPE x64 avec PowerShell pour signaler son démarrage. Le ZIP est importable directement, aucun autre ordinateur Windows n'est nécessaire.

1. Téléphone sur le Wi-Fi LAN de la Freebox. Importer le ZIP dans PocketInstall. Attendre la validation des tailles, SHA-256, architecture EFI, WIM et routes HTTP. L'import ne démarre aucun serveur.
2. Appuyer sur **Démarrer · attendre le PC**. Le serveur teste de nouveau les routes sur la véritable interface LAN avant d'afficher « serveur démarré ».
3. Ouvrir **Configuration Freebox · une seule fois**. Dans Freebox OS, réserver l'IP indiquée pour le téléphone (par exemple `192.168.0.35`). La Freebox reste le DHCP ; ne pas changer ses plages/DNS pour ce test.
4. Enregistrer `pocketinstall.ipxe` depuis l'appli. Déposer ce fichier et le nouveau `snponly.efi` dans `/Disque dur/PocketInstall` via l'explorateur Freebox OS. Ils restent côte à côte, sans sous-dossier ni extension `.txt` ajoutée.
5. Freebox OS → Partage de fichiers → TFTP : activer, racine `/Disque dur/PocketInstall`. Réseau local → DHCP → Démarrage par TFTP : serveur = **IP Freebox** (`192.168.0.254` dans l'installation testée), fichier = `snponly.efi`. Appliquer.
6. PC connecté par Ethernet, démarrer UEFI PXE IPv4. Le chargeur iPXE non signé nécessite une politique Secure Boot compatible.

La box est configurée manuellement par l'utilisateur. PocketInstall ne l'administre pas et ne crée pas de DHCP.

## Démarrages suivants

Appuyer sur Démarrer dans PocketInstall puis démarrer le PC en PXE. Rien à saisir sur le PC. iPXE obtient une IP par DHCP, lit `pocketinstall.ipxe` depuis le TFTP Freebox, puis contacte `http://IP_TELEPHONE:8080/boot.ipxe`. Ce point d'entrée sert un script contenant automatiquement le token courant et les URLs de wimboot, bootmgfw.efi, BCD, boot.sdi, boot.wim et des fichiers de démarrage injectés par wimboot. Une session différente ne nécessite aucune modification Freebox.

Si l'IP du téléphone change malgré la réservation, exporter à nouveau `pocketinstall.ipxe` et remplacer ce fichier sur la Freebox. Le chargeur réessaie la connexion si le téléphone n'est pas encore prêt. La session expire au bout de 30 minutes.

## Progression et preuve de démarrage

L'application affiche : attente → PC détecté → iPXE connecté → chargement → image envoyée, démarrage à confirmer. Elle annonce un démarrage seulement après réception du signal émis **depuis WinPE**, après `wpeinit`, pour le PC ayant reçu les fichiers complets. Un HEAD, une requête partielle, une erreur ou le téléchargement du WIM ne suffisent pas.

Le signal est une notification de fonctionnement sur le LAN, pas une attestation cryptographique. La preuve matérielle reste le message `PocketInstall boot successful (WinPE)` et la console Windows PE sur l'écran du PC. Un pilote réseau absent dans WinPE peut empêcher la notification alors que la console fonctionne ; vérifier l'écran et les pilotes.

Les fichiers `winpeshl.ini` et `pocketinstall.cmd` sont générés par l'application et injectés par wimboot dans `X:\Windows\System32`. Ils initialisent le réseau et signalent le démarrage de WinPE, puis ouvrent l’installateur PocketInstall. Si une image Windows est activée, le choix du disque et la confirmation d’effacement restent sur le PC avant DiskPart et l’application de l’image. Le mode sans image ne lance pas d’installation. Pour Linux, Debian conserve ses propres questions de compte et de partitionnement, sans préconfirmation d’effacement.

## Diagnostic avancé

Les IP, URLs de session, requêtes et commandes sont disponibles dans l'application. En cas de dépannage seulement : Ctrl+B pendant le démarrage du nouveau chargeur, puis `dhcp` et `chain URL_EXACTE_DE_L_APPLICATION`. Une ancienne URL de session est volontairement refusée. `boot.ipxe` est servi par le téléphone ; il ne faut pas le déposer sur le TFTP. Seuls `snponly.efi` et `pocketinstall.ipxe` y sont déposés.

- Console iPXE sans automatisation : ancien snponly.efi encore utilisé.
- Message configuration absente : vérifier pocketinstall.ipxe et la racine TFTP.
- HTTP 404 : session périmée, fichier absent ou nom incorrect ; voir le nom de route dans les journaux.
- HTTP 500 : erreur d'ouverture d'une ressource ; réimporter et relancer. Une erreur interne n'est plus masquée en 404.
- Transfert interrompu : vérifier LAN, maintien du serveur Android et écran du téléphone ; redémarrer le PC en PXE.
- Windows Boot Manager échoue : contrôler mémoire, architecture x64, pilotes et Secure Boot. Fournir l'écran exact.

Le point d'entrée stable est accessible uniquement sur l'interface et le sous-réseau privés sélectionnés, pendant la session. Les fichiers restent derrière le token ; ce point d'entrée le distribue aux clients LAN. Ne pas rediriger 8080 vers Internet.

## Validation du projet

Tests Kotlin utilisant le même assemblage de routes qu'Android : script complet, fichiers, sondes HTTP, expiration des tokens, route stable, erreurs et progression. Workflow GitHub : vrai bundle Microsoft ADK, compilation/lint APK et VM OVMF sans disque. Le test VM nécessite le signal exécuté dans WinPE et conserve écran/journaux ; il ne passe pas sur une simple requête HTTP. Il ne remplace pas un test sur le PC physique.

Personnalisation facultative : `scripts/Build-WinPE.ps1 -WithPowerShell` sur Windows avec ADK/add-on compatibles, puis `scripts/Package-WinPE.ps1`. Les pilotes réseau propres au PC peuvent être ajoutés avec `-DriverDirectory`.

Références primaires iPXE : https://ipxe.org/embed ; https://ipxe.org/howto/winpe ; https://ipxe.org/wimboot (fichiers injectés). Microsoft ADK : https://learn.microsoft.com/windows-hardware/get-started/adk-install

