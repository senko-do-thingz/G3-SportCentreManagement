#!/usr/bin/env python3
"""Fail on high or critical npm advisories unless a reviewed, unexpired exception covers the advisory."""
import argparse
from datetime import date
import json
from pathlib import Path
import sys

BLOCKING_SEVERITIES = ('high', 'critical')
REQUIRED_EXCEPTION_FIELDS = ('id', 'package', 'reason', 'expires')


def advisory_id(advisory):
    # Advisory URLs end with the GHSA identifier, for example .../advisories/GHSA-xxxx-xxxx-xxxx.
    return str(advisory.get('url', '')).rstrip('/').rsplit('/', 1)[-1] or str(advisory.get('source', ''))


def load_report(path):
    report = json.loads(Path(path).read_text(encoding='utf-8'))
    if not isinstance(report, dict) or 'error' in report:
        raise ValueError('npm audit did not produce a report: ' + json.dumps(report.get('error') if isinstance(report, dict) else report))
    if not isinstance(report.get('vulnerabilities'), dict) or not isinstance(report.get('metadata'), dict):
        raise ValueError('Unexpected npm audit report format: ' + str(path))
    return report


def load_exceptions(path, today):
    entries = json.loads(Path(path).read_text(encoding='utf-8'))
    if not isinstance(entries, list):
        raise ValueError('Audit exceptions must be a JSON array: ' + str(path))
    active = {}
    for entry in entries:
        missing = [field for field in REQUIRED_EXCEPTION_FIELDS if not str(entry.get(field, '')).strip()]
        if missing:
            raise ValueError('Audit exception is missing ' + ', '.join(missing) + ': ' + json.dumps(entry))
        expires = date.fromisoformat(entry['expires'])
        if expires < today:
            raise ValueError('Audit exception expired on ' + entry['expires'] + ': ' + entry['id'])
        active[entry['id']] = entry
    return active


def blocking_advisories(report):
    advisories = {}
    for package in report['vulnerabilities'].values():
        for advisory in package.get('via', []):
            # String entries only point at another vulnerable package; the advisory itself is listed there.
            if isinstance(advisory, dict) and advisory.get('severity') in BLOCKING_SEVERITIES:
                advisories[(advisory_id(advisory), advisory.get('name', ''))] = advisory
    return advisories


def evaluate(report, exceptions):
    accepted, rejected = [], []
    for (identifier, package), advisory in sorted(blocking_advisories(report).items()):
        exception = exceptions.get(identifier)
        if exception and exception['package'] == package:
            accepted.append((identifier, package, advisory, exception))
        else:
            rejected.append((identifier, package, advisory))
    reported = {identifier for identifier, _ in blocking_advisories(report)}
    unused = sorted(set(exceptions) - reported)
    return accepted, rejected, unused


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('report', help='Output of npm audit --json')
    parser.add_argument('exceptions', help='JSON array of reviewed advisory exceptions')
    args = parser.parse_args(argv)
    try:
        report = load_report(args.report)
        accepted, rejected, unused = evaluate(report, load_exceptions(args.exceptions, date.today()))
    except (OSError, ValueError, KeyError, TypeError) as error:
        print('npm audit gate failed: ' + str(error), file=sys.stderr)
        return 1
    print('npm audit totals: ' + json.dumps(report['metadata'].get('vulnerabilities', {}), sort_keys=True))
    for identifier, package, advisory, exception in accepted:
        print(f"Accepted {advisory.get('severity')} {identifier} in {package} until {exception['expires']}: {exception['reason']}")
    for identifier in unused:
        print(f'Exception {identifier} no longer matches a reported advisory and can be removed.')
    for identifier, package, advisory in rejected:
        print(f"Blocking {advisory.get('severity')} {identifier} in {package}: {advisory.get('title', '')} {advisory.get('url', '')}",
              file=sys.stderr)
    return 1 if rejected else 0


if __name__ == '__main__':
    sys.exit(main())
