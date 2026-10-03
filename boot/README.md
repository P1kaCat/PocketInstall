# EFI minimal

Linux x86_64, GCC/binutils, GNU-EFI et Python 3 suffisent :

```sh
sudo apt install build-essential binutils gnu-efi python3
make -C boot
```

Pour un SDK GNU-EFI extrait sans installation système :

```sh
make -C boot EFI_SDK=/chemin/sdk/usr
```

Sur une distribution plaçant les bibliothèques ailleurs, ajuster `EFI_LIB`.
Le résultat est `boot/build/bootx64.efi`. `scripts/verify_efi.py` contrôle le PE x64,
le subsystem EFI_APPLICATION, les sections, les imports et le message embarqué.
Les bibliothèques liées sont seulement crt0 et la relocation GNU-EFI, sans libefi.
Les appels firmware utilisent l'ABI Microsoft x64 via les headers GNU-EFI.

Le programme affiche le succès, attend une touche/30 s puis appelle ResetSystem
avec EfiResetShutdown. Il n'ouvre ni protocole BlockIO ni SimpleFileSystem, ne
charge pas Windows, ne modifie pas BCD/NVRAM et ne possède aucune pile réseau.
L'UEFI réalise le transfert HTTP avant de lui donner la main.

Le binaire est **non signé**. `bootx64.efi` est un nom conventionnel, pas le boot
manager Microsoft. Ne pas remplacer ce fichier par un boot manager Microsoft
isolé et supposer que BCD/WIM seront ensuite trouvés sur HTTP.

Inclure `THIRD_PARTY_NOTICES.txt` lors d'une redistribution du binaire. La signature
Secure Boot et ARM64/IA32 sont hors périmètre de ce build.
