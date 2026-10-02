#!/usr/bin/env python3
"""Write public mobile configuration only. No server/provider secrets belong here."""
import os
from pathlib import Path
from urllib.parse import urlparse
url=os.environ.get('HUNGII_SUPABASE_URL','')
key=os.environ.get('HUNGII_SUPABASE_PUBLISHABLE_KEY','')
client=os.environ.get('HUNGII_WORKOS_CLIENT_ID','')
ready=bool(url and key and client)
if ready:
    assert urlparse(url).scheme=='https' and urlparse(url).hostname.endswith('.supabase.co')
    assert client.startswith('client_') and key.startswith('sb_publishable_')
if os.environ.get('HUNGII_REQUIRE_CONFIG')=='true' and not ready:
    raise SystemExit('Configure the three public HUNGII repository variables before publishing.')
sdk=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
assert sdk and Path(sdk).is_dir(),'Android SDK unavailable'
values={'sdk.dir':sdk,'hungii.supabaseUrl':url,'hungii.supabasePublishableKey':key,'hungii.workosClientId':client,'hungii.workosAuthReady':str(ready).lower()}
assert all('\n' not in v and '\r' not in v for v in values.values())
Path('android-prototype/local.properties').write_text(''.join(k+'='+v+'\n' for k,v in values.items()))
print('Android public configuration ready; values omitted from logs.')
