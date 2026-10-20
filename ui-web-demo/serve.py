#!/usr/bin/env python3
import http.server
import socketserver
import os
import sys

PORT = 8080

class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=os.path.dirname(os.path.abspath(__file__)), **kwargs)

if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else PORT
    socketserver.TCPServer.allow_reuse_address = True
    try:
        with socketserver.TCPServer(("", port), Handler) as httpd:
            print(f"ClashMetaX UI Web Demo running at: http://localhost:{port}")
            httpd.serve_forever()
    except OSError as e:
        print(f"Port {port} is busy, try another port. Error: {e}")
