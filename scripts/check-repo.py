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
# Screenshots stay in history forever; keep each and the whole set within budget.
IMAGE_SUFFIXES = {'.png', '.jpg', '.jpeg', '.webp', '.gif'}
IMAGE_LIMIT, ARTIFACT_BUDGET = 1_500_000, 16_000_000
artifact_bytes = 0
for relative in sorted(set(files) - {''}):
    path = ROOT / relative
    if not path.is_file():
        continue
    if path.suffix in {'.apk', '.jks', '.keystore', '.mp4'} or (path.name.startswith('.env') and not path.name.endswith('.example')):
        errors.append(f'{relative}: private/generated file must not be published')
    if relative.startswith('artifacts/') and path.suffix.lower() in IMAGE_SUFFIXES:
        size = path.stat().st_size
        artifact_bytes += size
        if size > IMAGE_LIMIT:
            errors.append(f'{relative}: {size} bytes; compress or downscale screenshots below {IMAGE_LIMIT} bytes')
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
# Shared contract values defined once per language must agree.
tastes_ts = re.search(r'export const TASTES = \[([^\]]*)\]', (ROOT / 'supabase/functions/_shared/tracker.ts').read_text())
tastes_kt = re.search(r'val all = listOf\(([^)]*)\)', (ROOT / 'android-prototype/app/src/main/java/com/hungii/prototype/Models.kt').read_text())
if not tastes_ts or not tastes_kt:
    errors.append('Taste lists not found in tracker.ts or Models.kt')
elif re.findall(r'"([a-z]+)"', tastes_ts[1]) != ['any'] + re.findall(r'"([a-z]+)"', tastes_kt[1]):
    errors.append('Taste lists differ between tracker.ts and Android Tastes.all')
if artifact_bytes > ARTIFACT_BUDGET:
    errors.append(f'artifacts/: {artifact_bytes} bytes of screenshots exceeds {ARTIFACT_BUDGET}; attach new evidence to the PR or release')
if errors:
    raise SystemExit('\n'.join(errors))
print(f'Repository hygiene passed for {len(set(files) - {""})} publishable files.')
