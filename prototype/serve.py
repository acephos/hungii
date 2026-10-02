"""Throwaway prototype server. Run: python3 prototype/serve.py"""
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from functools import partial
from pathlib import Path

root = Path(__file__).resolve().parent.parent
print("Hungii prototype: http://localhost:5173/prototype/", flush=True)
ThreadingHTTPServer(("0.0.0.0", 5173), partial(SimpleHTTPRequestHandler, directory=str(root))).serve_forever()
