#!/usr/bin/env python3
"""Read-only development equivalent of the Android HTTP contract.

Default: loopback only. For a physical LAN test specify both --bind and --subnet.
This is test infrastructure, not a production deployment server.
"""
import argparse
import ipaddress
import json
import re
import secrets
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


def byte_range(value, size):
    if not value:
        return 0, size - 1, False
    match = re.fullmatch(r'bytes=(\d*)-(\d*)', value)
    if not match or not any(match.groups()) or size == 0:
        raise ValueError('Invalid range')
    left, right = match.groups()
    if not left:
        suffix = int(right)
        if suffix < 1:
            raise ValueError('Invalid suffix')
        return max(0, size - suffix), size - 1, True
    start = int(left)
    end = min(size - 1, int(right)) if right else size - 1
    if start >= size or end < start:
        raise ValueError('Unsatisfiable range')
    return start, end, True


def make_server(bind, port, subnet, files, log=None):
    address = ipaddress.ip_address(bind)
    private = any(address in ipaddress.ip_network(n) for n in
                  ('10.0.0.0/8', '172.16.0.0/12', '192.168.0.0/16'))
    if address.version != 4 or not (address.is_loopback or private):
        raise ValueError('Bind must be a private IPv4 or loopback test address')
    allowed = ipaddress.ip_network(subnet, strict=False)
    if address not in allowed:
        raise ValueError('Bind address must be in allowed subnet')
    if any(not p.is_file() for p in files.values()):
        raise ValueError('Boot resource missing; build the EFI first')
    session = secrets.token_hex(16)
    records = []
    records_lock = threading.Lock()
    capacity = threading.BoundedSemaphore(8)

    class Handler(BaseHTTPRequestHandler):
        protocol_version = 'HTTP/1.1'
        server_version = 'PocketInstall/0.1'

        def setup(self):
            super().setup()
            self.connection.settimeout(5)

        def log_message(self, *_):
            pass  # Never let BaseHTTPRequestHandler log the session token.

        def do_GET(self):
            self.deliver()

        def do_HEAD(self):
            self.deliver()

        def do_POST(self):
            self.respond(405, 'rejected')

        do_PUT = do_DELETE = do_PATCH = do_OPTIONS = do_POST

        def respond(self, status, name, length=0, extra=None):
            self.send_response(status)
            self.send_header('Content-Length', str(length))
            self.send_header('Content-Type', 'application/efi' if name.endswith('.efi')
                             else 'application/octet-stream')
            self.send_header('Connection', 'close')
            self.send_header('Cache-Control', 'no-store')
            self.send_header('X-Content-Type-Options', 'nosniff')
            if status == 405:
                self.send_header('Allow', 'GET, HEAD')
            for key, value in (extra or {}).items():
                self.send_header(key, value)
            self.end_headers()
            self.close_connection = True
            event = {'time': round(time.time(), 3), 'client': self.client_address[0],
                     'method': self.command, 'file': name, 'status': status,
                     'planned_bytes': length}
            with records_lock:
                records.append(event)
                del records[:-200]
                if log:
                    with Path(log).open('a', encoding='utf-8') as stream:
                        stream.write(json.dumps(event) + '\n')
            print(json.dumps(event), flush=True)

        def deliver(self):
            if not capacity.acquire(blocking=False):
                self.respond(503, 'rejected')
                return
            try:
                if ipaddress.ip_address(self.client_address[0]) not in allowed:
                    self.respond(403, 'rejected')
                    return
                if len(self.path) > 2048 or sum(len(k) + len(v) for k, v in self.headers.items()) > 16384:
                    self.respond(431, 'rejected')
                    return
                prefix = f'/{session}/'
                if any(c in self.path for c in ('%', '?', '#', '\\')) or '..' in self.path:
                    self.respond(400, 'rejected')
                    return
                name = self.path[len(prefix):] if self.path.startswith(prefix) else ''
                if name not in files:
                    self.respond(404, 'rejected')
                    return
                size = files[name].stat().st_size
                if len(self.headers.get_all('Range', [])) > 1:
                    self.respond(400, name)
                    return
                try:
                    start, end, partial = byte_range(self.headers.get('Range'), size)
                except ValueError:
                    self.respond(416, name, extra={'Content-Range': f'bytes */{size}'})
                    return
                length = max(0, end - start + 1)
                extra = {'Accept-Ranges': 'bytes'}
                if partial:
                    extra['Content-Range'] = f'bytes {start}-{end}/{size}'
                self.respond(206 if partial else 200, name, length, extra)
                if self.command == 'GET':
                    with files[name].open('rb') as stream:
                        stream.seek(start)
                        remaining = length
                        while remaining:
                            block = stream.read(min(65536, remaining))
                            if not block:
                                raise OSError('Resource changed during transfer')
                            self.wfile.write(block)
                            remaining -= len(block)
            except (OSError, TimeoutError):
                pass
            finally:
                capacity.release()

    server = ThreadingHTTPServer((bind, port), Handler)
    server.daemon_threads = True
    server.session = session
    server.records = records
    return server


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bind', default='127.0.0.1')
    parser.add_argument('--subnet', default='127.0.0.0/8')
    parser.add_argument('--port', type=int, default=8080)
    parser.add_argument('--efi', type=Path, default=Path(__file__).resolve().parents[1] / 'boot/build/bootx64.efi')
    parser.add_argument('--log', type=Path)
    parser.add_argument('--minutes', type=int, choices=range(1, 31), default=30)
    args = parser.parse_args()
    server = make_server(args.bind, args.port, args.subnet, {'bootx64.efi': args.efi}, args.log)
    print(f'Boot URL: http://{args.bind}:{server.server_port}/{server.session}/bootx64.efi', flush=True)
    print('HTTP delivery is not proof of execution. Session expires automatically.', flush=True)
    timer = threading.Timer(args.minutes * 60, server.shutdown)
    timer.daemon = True
    timer.start()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        timer.cancel()
        server.server_close()
