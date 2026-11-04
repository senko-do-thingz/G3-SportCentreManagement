"""Execute mapped acceptance cases on newly created owned SQL fixtures only."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys


def main():
    root = Path(__file__).resolve().parents[2]
    rows = json.loads(Path(__file__).with_name('flow1-4-acceptance.json').read_text())
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--requirement', choices=['ALL'] + [r['id'] for r in rows], default='ALL')
    options = parser.parse_args()
    for name in ('UNIFY_TEST_SQL_PORT', 'UNIFY_TEST_DB_USER', 'UNIFY_TEST_DB_PASSWORD'):
        if not os.environ.get(name):
            parser.error(name + ' must explicitly identify the owned native fixture server')
    env = os.environ.copy()
    env['DB_URL'] = 'jdbc:sqlserver://127.0.0.1:1;databaseName=release_unconfigured_target;encrypt=false'
    selected = rows if options.requirement == 'ALL' else [r for r in rows if r['id'] == options.requirement]
    # ALL runs every integration regression, including finance/migrations and negative paths.
    selector = '*' if options.requirement == 'ALL' else selected[0]['selectors']
    print('Acceptance IDs: ' + ','.join(r['id'] for r in selected), flush=True)
    command = ['mvn.cmd' if os.name == 'nt' else 'mvn', '-B', '-ntp', '-DskipUnitTests=true', '-Dit.test=' + selector, 'verify']
    process = subprocess.Popen(command, cwd=root / 'backend', env=env, stdout=subprocess.PIPE,
                               stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace')
    for line in process.stdout:
        for name in ('DB_PASSWORD', 'UNIFY_TEST_DB_PASSWORD', 'JWT_SECRET'):
            secret = env.get(name, '')
            if len(secret) > 6:
                line = line.replace(secret, '[REDACTED]')
        line = re.sub(r'(?i)("(?:password|temporaryPassword|accessToken|refreshToken|token)"\s*:\s*")[^"]*', r'\1[REDACTED]', line)
        line = re.sub(r'SCIN1\.[A-Za-z0-9_-]{43}', '[REDACTED_CHECK_IN_PASS]', line)
        line = re.sub(r'(?i)(Bearer\s+)[A-Za-z0-9_.-]+', r'\1[REDACTED]', line)
        print(line, end='')
    return process.wait()


if __name__ == '__main__':
    sys.exit(main())
