EFI_SDK ?= /usr
CC ?= gcc
LD ?= ld
OBJCOPY ?= objcopy
EFI_LIB := $(EFI_SDK)/lib
CFLAGS := -I$(EFI_SDK)/include/efi -I$(EFI_SDK)/include/efi/x86_64 \
          -DGNU_EFI_USE_MS_ABI -fpic -fshort-wchar -mno-red-zone \
          -fno-stack-protector -fno-strict-aliasing -fno-asynchronous-unwind-tables \
          -ffreestanding -Wall -Wextra -Werror -Os

.PHONY: all clean verify
all: build/bootx64.efi verify

build:
	mkdir -p build

build/main.o: src/main.c | build
	$(CC) $(CFLAGS) -c $< -o $@

build/pocketinstall.so: build/main.o
	$(LD) -nostdlib -znocombreloc -T $(EFI_LIB)/elf_x86_64_efi.lds \
	  -shared -Bsymbolic $(EFI_LIB)/crt0-efi-x86_64.o $< \
	  -L$(EFI_LIB) -lgnuefi -o $@

build/bootx64.efi: build/pocketinstall.so
	$(OBJCOPY) -j .text -j .sdata -j .data -j .dynamic -j .dynsym \
	  -j .rel -j .rela -j .reloc --target=efi-app-x86_64 $< $@

verify: build/bootx64.efi
	python3 ../scripts/verify_efi.py $<

clean:
	rm -rf build
