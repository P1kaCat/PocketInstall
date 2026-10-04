# Installer Windows depuis PocketInstall

Le parcours Freebox → iPXE → WinPE reste automatique. La Freebox se configure manuellement une seule fois ; aucun changement supplémentaire n'est nécessaire pour passer du test WinPE à l'installation.

## Sur le téléphone

1. Importe `PocketInstall-WinPE-x64.zip` si ce n'est pas déjà fait.
2. Dans **Installer Windows**, choisis **Windows 10 / Windows 11**, puis **Home / Pro**, en fonction de ta licence.
3. Télécharge ton ISO officielle avec le bouton Microsoft, puis importe l'ISO. L'application extrait `sources/install.wim` ou `install.esd` sans ordinateur Windows de préparation. Elle accepte les ISO9660 et les UDF à partition physique simple. Pour un format non pris en charge, extrais ce fichier sur le téléphone et importe directement le WIM/ESD. Les SWM séparés et ARM64 sont refusés.
4. Le SHA-256 officiel du fichier source peut être saisi avant l'import. Sans ce hash, l'empreinte enregistrée protège l'intégrité du transfert ; elle n'authentifie pas indépendamment l'éditeur. Utilise exclusivement ton téléchargement Microsoft officiel.
5. Choisis le débloat, active **Préparer l'installation au prochain démarrage PXE**, puis démarre le serveur WinPE.
6. Démarre le PC en Ethernet / UEFI PXE IPv4. Le réseau, le signal WinPE et l'inventaire matériel sont automatiques.

L'import utilise une copie privée. L'extraction ISO nécessite temporairement de l'espace pour l'ISO et l'image extraite ; l'ISO temporaire est supprimée après extraction. L'image privée est vérifiée à nouveau avant le démarrage du serveur. Les éditions et index proviennent des métadonnées du fichier, puis sont revalidés avec DISM sur le PC. Un choix absent du fichier n'est jamais remplacé silencieusement.

## Sur le PC

L'installation actuelle est une **installation neuve avec effacement complet du disque choisi**, pas une mise à niveau ni une conservation de fichiers. Sauvegarde auparavant ce qui doit être conservé.

WinPE affiche les disques internes, leurs modèles, numéros de série et tailles. Choisis le numéro, vérifie le récapitulatif puis tape `EFFACER <numéro>` pour confirmer. L'application n'envoie jamais cette confirmation à distance. Une saisie différente annule l'opération. L'identité du disque est relue avant la première écriture ; les autres disques ne sont pas sélectionnés par défaut.

**Le transfert de l'image commence après l'effacement et le partitionnement. Une panne de réseau ou un échec d'image à ce stade laisse le disque effacé.** Garde le téléphone alimenté, le serveur actif et le réseau stable. La session HTTP dure 30 minutes ; la console reste disponible en cas d'échec.

Le programme crée GPT / EFI FAT32 300 Mio / MSR 16 Mio / Windows NTFS / Recovery 2 Gio. Il transfère l'image sur le volume Windows avec reprises par blocs HTTP, vérifie son SHA-256 et son index/édition/architecture, applique Windows avec DISM, journalise le débloat, prépare le boot UEFI avec BCDBoot et enregistre WinRE avec REAgentC.

Les disques internes accessibles doivent avoir au moins 64 Gio et assez de place pour Windows, l'image temporaire et la récupération. Les supports USB/SD, disques hors ligne/lecture seule, identités inconnues et lettres S/W/R déjà occupées sont refusés. Aucune conversion d'un système existant, aucun déverrouillage BitLocker ni restauration de données n'est effectué.

Une fois prêt, redémarre sur le disque interne. Termine OOBE, installe les pilotes éventuellement nécessaires et active Windows avec ta licence. Le premier démarrage envoie un signal pendant `specialize` si le serveur est encore joignable. La fin d'OOBE reste à vérifier sur le PC.

## Débloat et mode automatique

- **Aucun** : aucun retrait d'application.
- **Léger** : retire uniquement Clipchamp, Solitaire, Actualités et Météo, s'ils sont provisionnés dans l'image.
- **Personnalisé** : choisis séparément ces quatre applications.
- **Auto** : profil léger avec moins de 8 Gio de RAM ou au maximum deux cœurs physiques détectés ; sinon aucun retrait. L'édition reste un choix de licence.

Windows Update, Defender, Microsoft Store, les pilotes et les services ne sont pas désactivés. Les changements sont enregistrés dans `C:\PocketInstall\debloat.json`. Les applications retirées peuvent être réinstallées depuis Microsoft Store.

Le nouveau ZIP WinPE inclut le pilote et le fournisseur TPM ainsi que les commandes Secure Boot. Réimporte le ZIP de cette release pour bénéficier de cette détection ; un ancien environnement sans ces composants indique « inconnu », jamais « absent » sur cette seule base. La RAM installée est lue dans les données SMBIOS, avec repli sur la RAM utilisable si ces données manquent.

Windows 11 refuse les échecs connus sur RAM, nombre de cœurs et TPM 2.0. Si une information manque, elle reste inconnue ; aucune compatibilité complète n'est annoncée. Vérifie aussi le modèle du processeur dans les listes Microsoft et la capacité Secure Boot. Le chargeur PXE actuel est non signé ; aucun contournement des exigences Windows n'est appliqué.

Windows 10 reste proposé pour les licences et usages correspondants, avec l'indication de fin du support standard. Le mode automatique ne le choisit pas à cause d'une faible RAM.

## États et diagnostic

Le transfert WinPE, son démarrage, l'application de Windows et son premier démarrage sont des états distincts. `Windows appliqué` ne signifie jamais `Windows démarré`. Le dernier état exige un signal exécuté par le Windows installé ; une requête d'image ne suffit pas.

Les commandes manuelles restent dans le diagnostic avancé. En cas d'échec, les journaux DISM sont dans `X:\Windows\Logs\DISM`, et ceux de l'installation dans `W:\PocketInstall` si ce volume a été créé. Après démarrage normal, ce dossier est `C:\PocketInstall`. Un token expiré nécessite une nouvelle session et un nouveau boot PXE ; il n'est pas remplacé à la main.

## Validation de publication

La publication exige des tests Android/serveur, un boot automatique WinPE sans disque et un test distinct appliquant une image Microsoft Windows 11 Pro x64 sur un disque QCOW2 jetable, puis démarrant réellement ce disque jusqu'au signal `specialize`. Ni l'ISO ni le système Windows de test ne sont redistribués. Les captures, empreintes et journaux accompagnent la release. Le test d'installation en VM ne prouve pas une installation sur chaque PC physique, ni la fin d'OOBE, ni une validation complète des quatre éditions.
