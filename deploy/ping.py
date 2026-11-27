"""Server list ping: prints the version a Minecraft server or proxy reports.  python ping.py [host] [port]"""
import json
import socket
import struct
import sys


def varint(n):
    out = b""
    while True:
        b = n & 0x7F
        n >>= 7
        out += bytes([b | (0x80 if n else 0)])
        if not n:
            return out


def read_varint(s):
    n = shift = 0
    while True:
        b = s.recv(1)[0]
        n |= (b & 0x7F) << shift
        shift += 7
        if not b & 0x80:
            return n


host = sys.argv[1] if len(sys.argv) > 1 else "127.0.0.1"
port = int(sys.argv[2]) if len(sys.argv) > 2 else 25565
s = socket.create_connection((host, port), timeout=10)
name = host.encode()
handshake = varint(0) + varint(772) + varint(len(name)) + name + struct.pack(">H", port) + varint(1)
s.sendall(varint(len(handshake)) + handshake + varint(1) + varint(0))
read_varint(s)
read_varint(s)
size = read_varint(s)
data = b""
while len(data) < size:
    data += s.recv(size - len(data))
print(json.dumps(json.loads(data)["version"]))
