#!/usr/bin/env python3
"""Reject failed/cancelled/missing checks and any skip not justified by a validated policy."""
import argparse
import json
import os


def validate(policy, needs, security=False, backend=False, frontend=False, pr=False):
    if security:
        expected = {'codeql': backend, 'frontend-codeql': frontend, 'dependency-review': pr}
    else:
        if policy.get('version') != 1 or set(policy.get('flags', {})) != {'backend', 'frontend', 'compose'}:
            raise ValueError('Missing or incompatible impact policy')
        if any(type(v) is not bool for v in policy['flags'].values()) or 'error' in policy:
            raise ValueError('Untrusted classifier output')
        if policy.get('mode') not in ('full', 'selective') or (policy['mode'] == 'full' and not all(policy['flags'].values())):
            raise ValueError('Inconsistent impact mode')
        flags = policy['flags']
        expected = {'impact': True, 'conventions': True, 'security': True,
                    'migration-guard': flags['backend'], 'unit-tests': flags['backend'],
                    'integration-tests': flags['backend'], 'package': flags['backend'],
                    'frontend': flags['frontend'], 'compose-smoke': flags['compose']}
    if set(needs) != set(expected):
        raise ValueError('Missing or unexpected child checks: ' + str(set(needs) ^ set(expected)))
    messages = []
    for name, required in expected.items():
        result = needs[name].get('result')
        if result == 'success':
            messages.append(name + ': verified success')
        elif result == 'skipped' and not required:
            messages.append(name + ': intentional policy skip')
        else:
            raise ValueError(f'{name}: {result!r}; required={required}')
    return messages


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--security', action='store_true')
    args = parser.parse_args()
    try:
        if args.security and any(os.environ.get(name) not in ('true', 'false') for name in ['BACKEND', 'FRONTEND', 'IS_PR']):
            raise ValueError('Missing or invalid security requirements')
        messages = validate(json.loads(os.environ.get('POLICY_JSON', '{}')), json.loads(os.environ['NEEDS_JSON']),
                            security=args.security, backend=os.environ.get('BACKEND') == 'true',
                            frontend=os.environ.get('FRONTEND') == 'true', pr=os.environ.get('IS_PR') == 'true')
        print('\n'.join(messages))
    except (ValueError, KeyError, TypeError) as failure:
        parser.exit(1, 'Merge gate rejected: ' + str(failure) + '\n')
