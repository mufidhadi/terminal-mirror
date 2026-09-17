import http.server
import socketserver
import os
import sys

PORT = 8000
DIRECTORY = os.path.abspath("apps/android/app/build/outputs/apk/debug")

class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DIRECTORY, **kwargs)

def main():
    with socketserver.TCPServer(("0.0.0.0", PORT), Handler) as httpd:
        print(f"Serving APKs from {DIRECTORY} on port {PORT}...", flush=True)
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            pass

if __name__ == "__main__":
    main()
