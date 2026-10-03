# Étape iPXE (après le POC EFI)

iPXE est un composant du [projet iPXE](https://ipxe.org), avec sa propre licence.
Il n'est pas embarqué dans l'APK POC. Ne pas confondre sa chaîne HTTP avec le
démarrage HTTP natif du petit binaire PocketInstall.

Sur un poste de build Linux, après validation EFI, compiler un iPXE x64 avec un
script réseau embarqué généré par `scripts/prepare_ipxe_script.py` :

```sh
python3 scripts/prepare_ipxe_script.py --base-url http://192.168.1.42:8080/SESSION --output winpe-output
git clone https://github.com/ipxe/ipxe.git vendor/ipxe
make -C vendor/ipxe/src bin-x86_64-efi/snponly.efi EMBED="$PWD/winpe-output/embedded.ipxe"
```

Conserver le commit exact d'iPXE choisi et les notices avant redistribution. Le
programme reconstruit est **non signé**. `snponly.efi` réutilise les protocoles SNP
firmware, ce qui doit être vérifié sur le matériel ; ce n'est pas une promesse de
réutiliser le Wi-Fi UEFI. `ipxe.efi` avec ses pilotes peut servir d'alternative en VM.

L'URI embarquée est valable pour cette session uniquement. Une application Android
ne doit pas dépendre d'une recompilation d'iPXE à chaque session : pour son futur
bundle, valider un loader stock et le chargement d'`autoexec.ipxe` depuis le chemin
de boot (documenté par [iPXE Secure Boot](https://ipxe.org/secboot)), ou demander
l'URL du script dans le shell iPXE. Ce mécanisme n'est pas implémenté dans l'APK.

Alternative de test : charger un iPXE stock, Ctrl+B puis :

```text
dhcp
chain http://192.168.1.42:8080/SESSION/winpe/boot.ipxe
```

Le script télécharge wimboot puis les fichiers WinPE en RAM. Le fichier **wimboot**
doit provenir du projet officiel et être épinglé par version/hash ; il n'est pas
fourni par le dépôt. Suivre `winpe/README.md`. La chaîne n'a pas encore été validée
en exécution et n'est pas une fonctionnalité de l'application POC.
