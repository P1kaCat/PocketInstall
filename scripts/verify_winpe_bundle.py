#!/usr/bin/env python3
"""Check release ZIP against Android's bundle contract, without executing payloads."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile

NAMES = {'boot.wim', 'boot.sdi', 'BCD', 'bootmgfw.efi', 'wimboot', 'snponly.efi'}


def efi(data):
    if len(data) < 64 or data[:2] != b'MZ':
        raise ValueError('Not a PE EFI image')
    offset = struct.unpack_from('<I', data, 60)[0]
    if offset < 64 or offset + 94 > len(data) or data[offset:offset + 4] != b'PE\0\0':
        raise ValueError('Invalid PE header')
    if struct.unpack_from('<H', data, offset + 4)[0] != 0x8664 or struct.unpack_from('<H', data, offset + 24)[0] != 0x20b or struct.unpack_from('<H', data, offset + 92)[0] != 10:
        raise ValueError('Expected x64 EFI application')


def verify(path):
    with zipfile.ZipFile(path) as archive:
        entries = archive.infolist()
        if len(entries) != 7 or {e.filename for e in entries} != NAMES | {'manifest.json'}:
            raise ValueError('Expected seven unique flat entries')
        if any(e.is_dir() for e in entries) or sum(e.file_size for e in entries) > 2 * 1024**3:
            raise ValueError('Invalid bundle size')
        if archive.getinfo('manifest.json').file_size > 65536:
            raise ValueError('Oversized manifest')
        manifest = json.loads(archive.read('manifest.json').decode('utf-8-sig'))
        resources = manifest['resources']
        if manifest['kind'] != 'pocketinstall-winpe-bundle-v1' or manifest['architecture'] != 'x64' or manifest['installsWindows'] is not False:
            raise ValueError('Invalid bundle kind')
        if len(resources) != 6 or {r['name'] for r in resources} != NAMES:
            raise ValueError('Invalid manifest resources')
        for resource in resources:
            name = resource['name']
            size = archive.getinfo(name).file_size
            limit = 2 * 1024**3 - 64 * 1024**2 if name == 'boot.wim' else 16 * 1024**2
            if size != resource['bytes'] or not 0 < size <= limit:
                raise ValueError(f'Invalid size: {name}')
            digest = hashlib.sha256()
            with archive.open(name) as stream:
                while chunk := stream.read(65536):
                    digest.update(chunk)
            if digest.hexdigest() != resource['sha256']:
                raise ValueError(f'Invalid hash: {name}')
        with archive.open('boot.wim') as stream:
            if stream.read(8) != b'MSWIM\0\0\0':
                raise ValueError('Invalid WIM header')
        for name in ('snponly.efi', 'bootmgfw.efi'):
            efi(archive.read(name))
        print(json.dumps({'validated': True, 'bytes': sum(e.file_size for e in entries), 'resources': resources}, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('zip', type=Path, nargs='?')
    parser.add_argument('--loader', type=Path)
    args = parser.parse_args()
    if args.loader:
        efi(args.loader.read_bytes())
        print('Validated x64 EFI loader')
    elif args.zip:
        verify(args.zip)
    else:
        parser.error('ZIP or --loader required')
