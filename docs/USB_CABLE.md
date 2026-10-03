# PocketInstall par câble USB — expérimental

Ce mode transporte le même HTTP Boot sur un **réseau USB**. Le téléphone reste
non rooté, sert les fichiers et ne devient pas une clé USB de stockage.

## Conditions indispensables

- Un câble USB **de données**, téléphone relié au PC.
- Le partage de connexion USB activé manuellement dans les paramètres Android.
- Une IPv4 privée visible sur une interface USB du téléphone.
- Un pilote UEFI reconnaissant **le réseau USB réellement exposé par ce téléphone**
  (RNDIS ou NCM selon l'appareil), DHCP IPv4 et HTTP Boot sur cette interface.
- Une URL HTTP saisissable dans le firmware et une politique Secure Boot acceptant
  le petit EFI non signé du POC.

La présence de « USB Boot » ou du téléphone dans Windows n'établit pas cette
compatibilité. Un pilote Windows RNDIS/NCM n'est pas un pilote UEFI. PXE seul ne
suffit pas à ce POC, qui ne sert ni TFTP ni options de boot DHCP.

Aucun couple téléphone/firmware n'a encore été validé pour le démarrage USB.
Si l'UEFI ne reconnaît pas ce réseau, ce PC n'est probablement pas compatible
avec PocketInstall par câble USB. Utiliser le LAN avec Ethernet sur le PC.

## Dans l'application

1. Sélectionner **Câble USB · expérimental**.
2. Brancher le téléphone au PC puis activer **Partage de connexion USB** dans
   les paramètres Android. Le bouton de l'application ouvre les paramètres
   réseau généraux ; l'emplacement de l'option dépend du fabricant.
3. Revenir et toucher **Actualiser les réseaux**.
4. Sélectionner l'interface USB privée détectée puis démarrer le test EFI.
5. Saisir l'URL exacte, token compris, dans l'entrée HTTP Boot de l'UEFI.

Le POC détecte les noms `rndis<number>`, `usb<number>` et `ncm<number>`
avec une IPv4 et un sous-réseau entièrement privés. Ces noms sont une
**heuristique prudente**, pas une norme ni une preuve que le partage est actif.
Un autre nom OEM, une restriction Android ou une absence d'IPv4 peut empêcher la
détection. Aucun choix arbitraire d'IP ni fallback vers une interface mobile,
VPN ou toutes les interfaces n'est utilisé.

La détection du côté Android ne certifie pas la compatibilité du PC. Seul
`PocketInstall boot successful` affiché sur le PC prouve le démarrage EFI.

Le serveur écoute uniquement sur l'IP sélectionnée, conserve ses contrôles
sous-réseau/token/expiration et surveille toutes les secondes la présence de
l'interface, de son IP et de son préfixe. Il ferme la session si cette vérification
échoue. L'application ne désactive pas le partage système en quittant :
désactiver le partage USB dans Android une fois le test terminé.

## Test sans écrire sur le disque

Commencer par un PC de test compatible et ne lancer aucune fonction d'installation :

1. Relever modèle du téléphone, Android, firmware/PC, câble et protocole USB.
2. Vérifier dans l'UEFI que l'interface réseau USB est reconnue avant Windows.
3. Vérifier que l'UEFI obtient une IP sur le sous-réseau USB.
4. Démarrer HTTP Boot sur l'URL du téléphone.
5. Vérifier HEAD/GET côté application, puis le message de succès côté PC.
6. Laisser l'EFI arrêter le PC. Aucun disque n'est ouvert par ce POC.
7. Tester séparément le débranchement et l'arrêt de la session Android.

Si aucune interface réseau USB n'apparaît dans l'UEFI, arrêter cet essai : le
téléchargement d'un pilote Windows, MTP, la recharge ou une option « USB Boot »
ne corrigent pas le firmware. Le téléphone peut couper le partage lorsque le
contrôleur USB est réinitialisé au redémarrage ; vérifier son état à nouveau.

## Ethernet avec câble

Le mode **LAN · Wi-Fi / Ethernet** conserve la prise en charge des réseaux
Ethernet exposés à Android, y compris via un adaptateur USB-C/Ethernet compatible.
Il s'agit d'un réseau Ethernet : cette compatibilité ne prouve pas celle du
partage USB direct téléphone–PC.

## Limites futures

Ce changement sert uniquement le petit EFI du POC. Il ne valide ni WinPE, ni ses
pilotes RNDIS/NCM, ni une installation Windows par USB. Ces étapes devront être
testées séparément. Aucun root, pilote noyau injecté, API Android cachée ou
émulation USB mass-storage n'est utilisé.

Sources et analyse : [research/USB_CABLE.md](../research/USB_CABLE.md).
