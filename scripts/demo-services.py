#!/usr/bin/env python3
"""Install restartable user services for this checkout's synthetic simulator."""
import argparse
import ipaddress
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import time

ROOT = Path(__file__).resolve().parents[1]


def unit_quote(value):
    # systemd parses ExecStart itself; there is no shell. Escape its specifiers
    # and environment expansion as well as quoted argument boundaries.
    return '"' + str(value).replace('\\', '\\\\').replace('"', '\\"').replace('%', '%%').replace('$', '$$') + '"'


def unit(description, command, bind=None, assistant=False):
    lines = ['[Unit]', f'Description={description}', 'StartLimitIntervalSec=0']
    if assistant:
        lines += ['After=hungii-simulator.service', 'Wants=hungii-simulator.service']
    lines += ['', '[Service]', 'Type=simple', f'WorkingDirectory={str(ROOT).replace("%", "%%")}']
    if bind:
        lines.append(f'Environment=HUNGII_DEMO_BIND={bind}')
    lines += ['ExecStart=' + ' '.join(unit_quote(arg) for arg in command),
              'Restart=on-failure', 'RestartSec=5', 'UMask=0077',
              '', '[Install]', 'WantedBy=default.target', '']
    return '\n'.join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bind', default='127.0.0.1', help='Loopback or this computer\'s Tailscale IPv4')
    parser.add_argument('--assistant', action='store_true', help='Also enable the existing .venv-agent cloud Assistant')
    args = parser.parse_args()
    try:
        address = ipaddress.IPv4Address(args.bind)
    except ipaddress.AddressValueError:
        parser.error('Use a loopback or Tailscale IPv4 address.')
    if address != ipaddress.IPv4Address('127.0.0.1') and address not in ipaddress.IPv4Network('100.64.0.0/10'):
        parser.error('The unauthenticated simulator can bind only to loopback or Tailscale.')
    deno = shutil.which('deno')
    if not deno or not shutil.which('systemctl') or not shutil.which('systemd-analyze'):
        parser.error('Install Deno 2.9.6 and run this on Linux with a systemd user manager.')
    if not subprocess.check_output([deno, '--version'], text=True).startswith('deno 2.9.6 '):
        parser.error('Use Deno 2.9.6, matching CI.')
    if '\n' in str(ROOT) or '\r' in str(ROOT):
        parser.error('The checkout path must not contain line breaks.')
    units = {'hungii-simulator.service': unit('Hungii synthetic MCP simulator and phone gateway',
        [deno, 'run', '--config', ROOT / 'supabase/functions/deno.json',
         f'--allow-net=127.0.0.1,{address}', '--allow-env', ROOT / 'demo/gateway.ts'], bind=str(address))}
    if args.assistant:
        python = ROOT / '.venv-agent/bin/python'
        if not os.access(python, os.X_OK):
            parser.error('Create .venv-agent and install demo/requirements-agent.txt first.')
        units['hungii-agent.service'] = unit('Hungii opt-in cloud Assistant',
            [python, ROOT / 'demo/agent.py'], assistant=True)
    # Verify staged units before changing any working service configuration.
    with tempfile.TemporaryDirectory(prefix='hungii-services-') as directory:
        staged = []
        for name, text in units.items():
            path = Path(directory) / name
            path.write_text(text)
            staged.append(str(path))
        subprocess.run(['systemd-analyze', '--user', 'verify', *staged], check=True)
    config = Path(os.environ.get('XDG_CONFIG_HOME', str(Path.home() / '.config'))) / 'systemd/user'
    config.mkdir(parents=True, exist_ok=True)
    for name, text in units.items():
        path = config / name
        if path.exists() and path.read_text() != text:
            shutil.copy2(path, path.with_suffix(f'.service.bak.{time.time_ns()}'))
        path.write_text(text)
    subprocess.run(['systemctl', '--user', 'daemon-reload'], check=True)
    subprocess.run(['systemctl', '--user', 'enable', '--now', *units], check=True)
    # Restart existing units too, so a changed checkout or bind is applied.
    subprocess.run(['systemctl', '--user', 'restart', *units], check=True)
    print('Enabled Hungii user services: ' + ', '.join(units))
    print('They start with the user manager. The computer must stay awake.')


if __name__ == '__main__':
    try:
        main()
    except subprocess.CalledProcessError:
        sys.exit('Service setup failed. Check systemctl --user status hungii-simulator hungii-agent.')
