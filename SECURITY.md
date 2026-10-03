# Sécurité du prototype

Le premier programme EFI n'utilise aucun protocole d'accès disque. Le serveur réseau est
en lecture seule et arrêté par défaut. Le programme de test s'arrête après une
touche ou 30 secondes. Il ne modifie ni Secure Boot ni les entrées de boot.

## Protections implémentées dans le serveur

- Écoute sur une IPv4 LAN explicitement choisie ; Android refuse mobile/VPN/public.
- Clients dans le sous-réseau configuré seulement (loopback en test local).
- Nouvelle session aléatoire à chaque démarrage ; URL sous `/<session>/`.
- GET/HEAD uniquement, fichiers en liste blanche, aucune résolution de chemin client.
- Refus des query strings, encodages `%`, segments relatifs et méthodes d'écriture.
- En-têtes et durée de lecture bornés, nombre de connexions simultanées borné.
- Transfert par morceaux, taille exacte, une seule Range HTTP, pas de listing.
- Expiration absolue 30 minutes, arrêt manuel et arrêt à perte/changement du réseau.
- Journal local borné ; token et URL complète masqués dans les logs de requêtes.
- Aucun shell, upload, accès arbitraire au stockage, endpoint de partitionnement,
  auto-start ou redémarrage automatique du service.

Un LAN privé n'est pas une frontière de sécurité absolue. Un client routé/NAT peut
sembler local ; HTTP n'empêche pas une interception, un ARP spoofing ou une
modification des octets. Le token n'authentifie ni le firmware ni l'image. Il est
visible sur le réseau. Le serveur POC n'effectue ni UPnP ni ouverture de routeur.
HTTPS avec confiance préboot et une chaîne de boot signée constituent des étapes
futures, pas une protection prétendue du POC non signé.

## Disques et installation future

Recovery devra afficher modèle, capacité, numéro de série et identifiant stable du
disque, puis exiger la saisie exacte `ERASE` sur le PC. Revalider l'identité juste
avant l'écriture ; le numéro « Disk 0 » seul ne suffit pas. Annulation ou erreur de
hash empêche toute écriture. Aucun bouton sur le téléphone ne remplace ce contrôle.

Le POC ne contient pas d'exécuteur d'installation. Les commandes DISM/diskpart
de la documentation ne sont pas lancées automatiquement. Le futur staging sur
un SSD, les changements EFI/BCD et les réparations sont également des écritures
à annoncer et confirmer. L'exploration/sauvegarde précède toute réinstallation.

## Secure Boot et BitLocker

Le `.efi` du POC est non signé. Tester d'abord en VM. Pour un essai physique où il
faut changer Secure Boot, conserver la clé BitLocker, noter la configuration et
la restaurer ensuite. Le logiciel ne force aucun changement de confiance. Éviter
d'enrôler des clés de test sur le PC principal.

## Partage USB optionnel

Le mode USB ne lie pas le serveur à toutes les interfaces. Il accepte uniquement
un candidat USB présumé, actif et IPv4, dont tout le sous-réseau est privé ; la
sélection est revalidée au démarrage. Les interfaces VPN, Wi-Fi et mobiles ne
sont pas des candidats USB. Les contrôles HTTP existants restent appliqués.

La présence, l'IP et le préfixe sont vérifiés chaque seconde ; une perte ferme
la session. Cette heuristique n'authentifie pas le PC ni ne vérifie l'UEFI.
Android peut partager également Internet via son service système : l'utilisateur
doit désactiver le partage USB dans les paramètres après usage. Arrêter le serveur
PocketInstall n'arrête pas ce service Android indépendant.

## PXE / TFTP et relais

TFTP est lié uniquement à l'IP privée sélectionnée, limité au même sous-réseau
et à la liste de ressources internes. RRQ `octet` uniquement, chemin exact
`SESSION/bootx64.efi`, jamais de WRQ, résolution de chemin, upload ou commande.
Requêtes bornées à 1 024 octets, options validées, blocs plafonnés à 1 428 octets,
quatre workers maximum, essais / timeout bornés et session de 30 minutes.
Les réponses/logs de rejet sont limités en fréquence. Les ACK d'une autre
adresse/port ne font pas avancer le transfert. Les logs affichent le nom court,
sans le token, et les octets **acquittés**, pas une prétendue exécution sur le PC.

TFTP et DHCP sont en clair ; le token est visible dans les annonces DHCP et le
RRQ. Ils ne protègent pas contre un attaquant actif du LAN. Aucun sysctl, règle
iptables, root, UPnP, ouverture Internet ou DHCP Android n'est ajouté. Le repli
6969 est déclaré non utilisable par PXE standard sans un autre équipement.

Le relais Linux prépare seulement une configuration puis nécessite un lancement
explicite. Proxy-DHCP **sans pool d'adresses**, interface sélectionnée, une MAC
cible ; celle-ci n'est pas une authentification. Le téléchargement HTTP refuse
les redirections/proxies externes et exige le SHA256 du POC fourni par le dépôt.
Seul ce binaire vérifié est mis en cache, pas une ISO ou une commande arbitraire.
L'instance dnsmasq privilégiée tourne au maximum 30 minutes avec la commande
documentée ; aucun service permanent n'est installé/configuré par le script.

Le relais conserve une copie après fermeture du téléphone : **arrêter les deux**
et supprimer le cache. Le token Android ne révoque pas à distance cette copie.
Limiter le test à un LAN autorisé et restaurer les annonces DHCP après le test.
