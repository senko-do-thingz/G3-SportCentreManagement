#!/usr/bin/env python3
"""Verify the immutable same-run integration artifact before exposing the backend jar."""
import argparse
import hashlib
import re
from pathlib import Path


def verify(directory, expected_sha):
    root = Path(directory)
    if not re.fullmatch('[0-9a-f]{40}', expected_sha):
        raise ValueError('Expected source SHA is missing or invalid')
    if (root/'source-sha.txt').read_text().strip() != expected_sha:
        raise ValueError('Artifact source SHA mismatch')
    jars = list(root.glob('*.jar'))
    if len(jars) != 1 or not jars[0].is_file() or jars[0].is_symlink():
        raise ValueError('Expected one regular executable jar')
    expected = set()
    for line in (root/'SHA256SUMS').read_text().splitlines():
        match = re.fullmatch('([0-9a-f]{64})  ([A-Za-z0-9_.-]+)', line)
        if not match:
            raise ValueError('Invalid artifact checksum manifest')
        checksum, name = match.groups()
        if name in expected or name not in {jars[0].name, 'source-sha.txt'}:
            raise ValueError('Unexpected or duplicate checksum entry')
        expected.add(name)
        if hashlib.sha256((root/name).read_bytes()).hexdigest() != checksum:
            raise ValueError('Artifact checksum mismatch: ' + name)
    if expected != {jars[0].name, 'source-sha.txt'}:
        raise ValueError('Incomplete checksum manifest')
    return jars[0].name


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('directory')
    parser.add_argument('sha')
    args = parser.parse_args()
    try:
        print('Verified same-source integration jar:', verify(args.directory, args.sha))
    except (ValueError, OSError) as failure:
        parser.exit(1, str(failure) + '\n')
