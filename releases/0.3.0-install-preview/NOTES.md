# PocketInstall 0.3.0 — installation Windows (preview)

- Réseau WinPE initialisé et confirmation au téléphone automatique avec tentatives bornées : plus de commande PowerShell finale à recopier.
- Menu Windows 10 / Windows 11 → Home / Pro ; index réel détecté dans l'image importée.
- Import d'ISO9660/UDF standard ou WIM/ESD x64 officiel, validation des métadonnées, empreinte et routes HTTP.
- Inventaire du PC, débloat aucun/léger/personnalisé/auto selon RAM et cœurs.
- Installation neuve avec choix du disque et confirmation d'effacement sur le PC, transfert vérifié, DISM, BCDBoot et WinRE.
- Signal séparé au premier démarrage du Windows installé ; OOBE reste à terminer.

Le ZIP WinPE seul ne contient pas Windows à installer : importe ton ISO officielle Microsoft. La configuration Freebox existante reste valable. Aucun choix Home/Pro n'est effectué à partir des composants ; choisis l'édition correspondant à ta licence.

**Installation neuve uniquement. Le disque choisi est entièrement effacé avant le transfert de l'image.** Une interruption après confirmation peut laisser ce disque sans système. Sauvegarde tes fichiers et garde le téléphone alimenté et le serveur ouvert. Session réseau limitée à 30 minutes.

Le profil léger retire seulement Clipchamp, Solitaire, Actualités et Météo présents dans l'image. Defender, Update, Store et pilotes restent actifs. Le mode automatique ne certifie pas la compatibilité complète Windows 11.

Validation effectuée : 48 tests serveur, compilation/lint Android et démarrage PXE automatique jusqu'à WinPE avec inventaire matériel. Le test d'installation complète de Windows n'est pas terminé au moment de cette publication en preview. L'installation physique, le premier démarrage Windows et la fin d'OOBE ne sont pas confirmés pour cette version ; les quatre éditions ne sont pas présentées comme testées de bout en bout.

Réimporte le nouveau ZIP WinPE de cette release : il ajoute le pilote/fournisseur TPM et les commandes Secure Boot. La RAM installée est détectée depuis SMBIOS.

Guide : [installation Windows](../../docs/WINDOWS_INSTALL.md). L'APK reste une build de développement ; selon la signature de ta version précédente, Android peut demander une désinstallation puis un nouvel import.
