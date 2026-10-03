# PocketInstall 0.1.2-poc

## Nouveauté : câble USB expérimental

- Choix **LAN · Wi-Fi / Ethernet** ou **Câble USB · expérimental**.
- Instructions et bouton pour ouvrir les paramètres réseau Android.
- Détection prudente des interfaces USB IPv4 privées visibles.
- Serveur lié à l'IP USB sélectionnée, token et durée limitée.
- Arrêt si l'interface USB disparaît ou si son adresse/préfixe change.
- Tests de filtrage des interfaces et sous-réseaux, en plus des tests TCP.

Le partage de connexion USB doit être activé **manuellement** dans Android.
Le PC doit reconnaître le réseau USB du téléphone dans son UEFI, obtenir une
IPv4 et proposer HTTP Boot dessus. La détection du téléphone dans Windows ou
l'option USB Boot de stockage ne suffisent pas. Le téléphone ne devient pas
une clé USB bootable.

**Aucun téléphone/PC physique n'est encore validé pour le mode USB.**
Le POC affiche un message EFI puis arrête le PC, sans écriture disque.
Ni WinPE ni l'installation Windows par câble ne sont implémentés.

Voir [la procédure USB](https://github.com/P1kaCat/PocketInstall/blob/main/docs/USB_CABLE.md).

L'APK debug et les hashes SHA-256 sont joints à cette release. Sa clé debug peut
différer de celle du build précédent : si Android refuse la mise à jour pour
signature différente, désinstaller l'ancienne application avant l'installation.

La licence personnelle autorise les modifications privées pour usage personnel
non commercial et soumet la redistribution à un accord écrit. Les licences
tierces et les droits accordés au POC historique sous MIT sont conservés.
