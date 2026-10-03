#!/usr/bin/env python3
"""Check the actual distributed PE, without third-party Python dependencies."""
import argparse
import hashlib
import json
import struct
from pathlib import Path


def verify(path):
    data = Path(path).read_bytes()
    if len(data) < 256 or data[:2] != b'MZ':
        raise ValueError('Missing DOS/PE header')
    pe = struct.unpack_from('<I', data, 0x3C)[0]
    if data[pe:pe + 4] != b'PE\0\0':
        raise ValueError('Missing PE signature')
    machine, sections = struct.unpack_from('<HH', data, pe + 4)
    size_optional = struct.unpack_from('<H', data, pe + 20)[0]
    opt = pe + 24
    magic = struct.unpack_from('<H', data, opt)[0]
    subsystem = struct.unpack_from('<H', data, opt + 68)[0]
    entry = struct.unpack_from('<I', data, opt + 16)[0]
    if (machine, magic, subsystem) != (0x8664, 0x20B, 10):
        raise ValueError('Expected PE32+ AMD64 EFI_APPLICATION')
    table = opt + size_optional
    names = []
    entry_executable = False
    for n in range(sections):
        at = table + n * 40
        name = data[at:at + 8].rstrip(b'\0').decode('ascii')
        virtual_size, rva, raw_size, offset = struct.unpack_from('<IIII', data, at + 8)
        flags = struct.unpack_from('<I', data, at + 36)[0]
        if offset + raw_size > len(data):
            raise ValueError('Section outside file')
        if rva <= entry < rva + max(virtual_size, raw_size) and flags & 0x20000000:
            entry_executable = True
        names.append(name)
    if not entry_executable or '.reloc' not in names:
        raise ValueError('Missing executable entry or relocation section')
    imports_rva, imports_size = struct.unpack_from('<II', data, opt + 112 + 8)
    if imports_rva or imports_size:
        raise ValueError('EFI proof must have no OS DLL imports')
    cert_offset, cert_size = struct.unpack_from('<II', data, opt + 112 + 4 * 8)
    if 'PocketInstall boot successful'.encode('utf-16le') not in data:
        raise ValueError('Missing proof message')
    return {'file': str(path), 'bytes': len(data), 'machine': 'x64',
            'subsystem': 'EFI_APPLICATION', 'entry_rva': entry, 'sections': names,
            'signed': bool(cert_offset and cert_size),
            'sha256': hashlib.sha256(data).hexdigest()}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('file')
    print(json.dumps(verify(parser.parse_args().file), indent=2))
