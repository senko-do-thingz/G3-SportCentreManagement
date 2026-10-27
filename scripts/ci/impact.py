#!/usr/bin/env python3
"""Conservative Git change-impact policy. No API file-list truncation or branch-name trust."""
import argparse
import hashlib
import json
import os
import re
import subprocess
from pathlib import Path, PurePosixPath

FLAGS = ('backend', 'frontend', 'compose')
ROOT_DOCS = {'README.md', 'CHANGELOG.md', 'CONTRIBUTING.md', 'LICENSE', 'LICENSE.md'}
CONTRACT_DOCS = ('docs/api/', 'docs/database/', 'docs/nx-03/', 'docs/requirements/')


def full(reason):
    return {'version': 1, 'flags': dict.fromkeys(FLAGS, True), 'reasons': [reason], 'mode': 'full'}


def classify(changes):
    policy = {'version': 1, 'flags': dict.fromkeys(FLAGS, False), 'reasons': [], 'mode': 'selective'}
    if not changes:
        return full('Empty or unavailable diff is not evidence of a documentation-only change')
    for status, paths in changes:
        if status not in ('A', 'M') or len(paths) != 1:
            return full('Renamed, deleted, type-changed or unrecognized Git status: ' + status)
        path = paths[0]
        parts = PurePosixPath(path).parts
        if not path or path.startswith('/') or '..' in parts or '\\' in path:
            return full('Unclassifiable path')
        if path.startswith(CONTRACT_DOCS):
            return full('Shared API/database/requirement contract: ' + path)
        if path in ROOT_DOCS or (path.startswith('docs/') and path.endswith('.md')):
            policy['reasons'].append('Non-build documentation: ' + path)
            continue
        if path.startswith(('frontend/src/api/', 'frontend/src/auth/', 'frontend/src/generated/')):
            return full('Client/server contract or identity boundary: ' + path)
        if path.startswith('frontend/'):
            if path.startswith('frontend/scripts/') or PurePosixPath(path).name in ('Dockerfile', 'nginx.conf'):
                return full('Executable/shared infrastructure: ' + path)
            policy['flags']['frontend'] = True
            policy['reasons'].append('Frontend source, dependencies or toolchain: ' + path)
            continue
        if path.startswith('backend/'):
            if path.startswith(('backend/src/main/resources/', 'backend/src/main/java/com/sportify/identity/',
                                'backend/src/main/java/com/sportify/core/')) or '/controller/' in path or '/dto/' in path:
                return full('Server contract, security, schema or runtime configuration: ' + path)
            policy['flags']['backend'] = True
            if path in ('backend/pom.xml', 'backend/Dockerfile'):
                policy['flags']['compose'] = True
            policy['reasons'].append('Backend source/tests/build: ' + path)
            continue
        return full('Unknown or repository/CI/deployment infrastructure: ' + path)
    return policy


def git(root, *args):
    return subprocess.check_output(['git', *args], cwd=root, stderr=subprocess.PIPE)


def commit(value):
    if not isinstance(value, str) or not re.fullmatch('[0-9a-f]{40}', value):
        raise ValueError('Expected an immutable GitHub commit SHA')
    return value


def parse_diff(data):
    tokens = data.decode('utf-8', errors='strict').split('\0')
    if tokens[-1] != '':
        raise ValueError('Truncated NUL-delimited Git diff')
    tokens.pop()
    changes = []
    while tokens:
        status = tokens.pop(0)
        count = 2 if status.startswith(('R', 'C')) else 1
        if len(tokens) < count:
            raise ValueError('Missing Git diff path')
        paths, tokens = tokens[:count], tokens[count:]
        changes.append((status, paths))
    return changes


def select(event_name, event, root):
    if event_name != 'pull_request':
        return full('Authoritative main/release, scheduled, reusable or manual full regression')
    pr = event['pull_request']
    base, head = commit(pr['base']['sha']), commit(pr['head']['sha'])
    for sha in (base, head):
        git(root, 'cat-file', '-e', sha + '^{commit}')
    bases = git(root, 'merge-base', '--all', base, head).decode().splitlines()
    if len(bases) != 1:
        raise ValueError('Ambiguous or missing merge base')
    merge_base = commit(bases[0])
    diff = git(root, 'diff', '--no-ext-diff', '--no-textconv', '--name-status', '-z', '--find-renames', merge_base, head)
    changes = parse_diff(diff)
    policy = classify(changes)
    policy['source'] = {'base': base, 'head': head, 'merge_base': merge_base, 'diff_sha256': hashlib.sha256(diff).hexdigest()}
    policy['changes'] = [{'status': status, 'paths': paths} for status, paths in changes]
    return policy


def outputs(policy, output):
    lines = [f'{flag}={str(policy["flags"][flag]).lower()}' for flag in FLAGS]
    lines.append('policy=' + json.dumps(policy, separators=(',', ':'), ensure_ascii=True))
    with Path(output).open('a', encoding='utf-8') as stream:
        stream.write('\n'.join(lines) + '\n')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--event', default=os.environ.get('GITHUB_EVENT_NAME', ''))
    parser.add_argument('--event-path', default=os.environ.get('GITHUB_EVENT_PATH'))
    parser.add_argument('--root', default='.')
    parser.add_argument('--output', default=os.environ.get('GITHUB_OUTPUT'))
    parser.add_argument('--report', default='impact.json')
    args = parser.parse_args()
    failed = False
    try:
        event = json.loads(Path(args.event_path).read_text(encoding='utf-8')) if args.event_path else {}
        policy = select(args.event, event, args.root)
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError) as failure:
        policy = full('Classifier failed; every workload must run and the aggregate gate must reject this run')
        policy['error'] = type(failure).__name__
        failed = True
    if args.output:
        outputs(policy, args.output)
    Path(args.report).write_text(json.dumps(policy, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(policy, indent=2))
    return 1 if failed else 0


if __name__ == '__main__':
    raise SystemExit(main())
