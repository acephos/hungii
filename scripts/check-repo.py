#!/usr/bin/env python3
"""Check publishable paths, obvious secret formats and local Markdown links."""
import re
import subprocess
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
files = subprocess.check_output(['git', 'ls-files', '--cached', '--others', '--exclude-standard', '-z'], cwd=ROOT).decode().split('\0')
errors = []
secret = re.compile(r'gsk_[A-Za-z0-9]{20,}|sk_(?:live|test)_[A-Za-z0-9]{20,}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----')
links = re.compile(r'(?<!!)\[[^\]]*\]\(([^\n)]+)\)|!\[[^\]]*\]\(([^\n)]+)\)')
for relative in sorted(set(files) - {''}):
    path = ROOT / relative
    if not path.is_file():
        continue
    if path.suffix in {'.apk', '.jks', '.keystore', '.mp4'} or (path.name.startswith('.env') and not path.name.endswith('.example')):
        errors.append(f'{relative}: private/generated file must not be published')
    raw = path.read_bytes()
    if b'\0' in raw:
        continue
    try:
        text = raw.decode('utf-8')
    except UnicodeDecodeError:
        continue
    for number, line in enumerate(text.splitlines(), 1):
        if secret.search(line):
            errors.append(f'{relative}:{number}: possible private credential (value omitted)')
    if path.suffix != '.md':
        continue
    for match in links.finditer(text):
        target = (match[1] or match[2]).strip()
        target = target[1:target.index('>')] if target.startswith('<') and '>' in target else target.split()[0]
        parts = urlsplit(target)
        if parts.scheme or parts.netloc or not parts.path:
            continue
        linked = (path.parent / unquote(parts.path)).resolve()
        if not linked.is_relative_to(ROOT) or not linked.exists():
            number = text.count('\n', 0, match.start()) + 1
            errors.append(f'{relative}:{number}: missing local link: {parts.path}')
if errors:
    raise SystemExit('\n'.join(errors))
print(f'Repository hygiene passed for {len(set(files) - {""})} publishable files.')
