#!/usr/bin/env python3
"""Prevent publishing an APK that cannot update the existing preview install."""
import os,re,subprocess,sys
from pathlib import Path
expected=os.environ.get('EXPECTED_PREVIEW_CERT','').replace(':','').lower()
if not re.fullmatch(r'[0-9a-f]{64}',expected):
    raise SystemExit('Set the public HUNGII_PREVIEW_CERT_SHA256 repository variable.')
tool=Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0/apksigner'
for apk in sys.argv[1:]:
    output=subprocess.check_output([str(tool),'verify','--print-certs',apk],text=True)
    certs=re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)',output)
    if len(certs)!=1 or certs[0].lower()!=expected:
        raise SystemExit('Preview signing certificate differs from the installed app: '+apk)
    print('Verified stable preview certificate: '+Path(apk).name)
