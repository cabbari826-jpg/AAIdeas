# Fake GitHub API for testing the publish buttons offline (see README).
import json, sys, base64, hashlib
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse, parse_qs
FILES = {}   # path -> text
REL = {}     # tag -> release
ASSETS = {}  # id -> (name, bytes)
NEXT = [100]
LOG = []
P = '/repos/o/r'
def sha(t): return hashlib.sha1(t.encode()).hexdigest()
class H(BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def send(self, code, obj=None, raw=None):
        body = raw if raw is not None else (json.dumps(obj).encode() if obj is not None else b'')
        self.send_response(code); self.send_header('Content-Length', str(len(body))); self.end_headers(); self.wfile.write(body)
    def body(self):
        n = int(self.headers.get('Content-Length') or 0); return self.rfile.read(n)
    def rel_json(self, r):
        r = dict(r); r['assets'] = [{'id': i, 'name': ASSETS[i][0], 'url': f'http://127.0.0.1:{PORT}{P}/releases/assets/{i}'} for i in r['asset_ids']]; return r
    def do_GET(self):
        u = urlparse(self.path); LOG.append('GET ' + u.path)
        if u.path.startswith(P + '/contents/'):
            path = u.path[len(P + '/contents/'):]
            if path in FILES:
                if 'raw' in (self.headers.get('Accept') or ''): return self.send(200, raw=FILES[path].encode())
                return self.send(200, {'sha': sha(FILES[path]), 'type': 'file'})
            kids = [k for k in FILES if k.rsplit('/', 1)[0] == path]
            if kids: return self.send(200, [{'name': k.rsplit('/', 1)[1], 'type': 'file', 'download_url': 'x'} for k in kids])
            return self.send(404, {'message': 'Not Found'})
        if u.path.startswith(P + '/releases/tags/'):
            t = u.path.rsplit('/', 1)[1]
            return self.send(200, self.rel_json(REL[t])) if t in REL else self.send(404, {'message': 'Not Found'})
        if u.path.startswith(P + '/releases/assets/'):
            i = int(u.path.rsplit('/', 1)[1]); return self.send(200, raw=ASSETS[i][1])
        if u.path == '/dump':
            return self.send(200, {'files': FILES, 'rel': REL, 'assets': {i: [n, b.decode()] for i, (n, b) in ASSETS.items()}, 'log': LOG})
        self.send(404, {'message': 'Not Found'})
    def do_PUT(self):
        u = urlparse(self.path); LOG.append('PUT ' + u.path)
        path = u.path[len(P + '/contents/'):]
        b = json.loads(self.body())
        if path in FILES and b.get('sha') != sha(FILES[path]): return self.send(409, {'message': 'sha mismatch'})
        FILES[path] = base64.b64decode(b['content']).decode()
        self.send(200, {'commit': {'html_url': 'http://commit/' + b['message']}})
    def do_POST(self):
        u = urlparse(self.path); LOG.append('POST ' + u.path)
        if u.path == P + '/releases':
            b = json.loads(self.body()); assert b['prerelease'] and b['make_latest'] == 'false'
            NEXT[0] += 1; REL[b['tag_name']] = {'id': NEXT[0], 'asset_ids': []}
            return self.send(201, self.rel_json(REL[b['tag_name']]))
        if '/assets' in u.path:
            rid = int(u.path.split('/')[-2]); name = parse_qs(u.query)['name'][0]
            NEXT[0] += 1; ASSETS[NEXT[0]] = (name, self.body())
            for r in REL.values():
                if r['id'] == rid: r['asset_ids'].append(NEXT[0])
            return self.send(201, {'browser_download_url': 'http://dl/' + name})
        self.send(404, {'message': 'nf'})
    def do_DELETE(self):
        u = urlparse(self.path); LOG.append('DELETE ' + u.path)
        i = int(u.path.rsplit('/', 1)[1]); ASSETS.pop(i)
        for r in REL.values():
            if i in r['asset_ids']: r['asset_ids'].remove(i)
        self.send(204)
PORT = int(sys.argv[1])
for a in sys.argv[2:]:
    k, v = a.split('=', 1); FILES[k] = open(v).read()
HTTPServer(('127.0.0.1', PORT), H).serve_forever()
