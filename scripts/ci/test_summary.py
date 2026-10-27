#!/usr/bin/env python3
"""Compact JUnit timing evidence; raw diagnostics remain in uploaded artifacts."""
import argparse
import json
import os
import xml.etree.ElementTree as ET
from pathlib import Path


def summarize(directory):
    suites = []
    for path in sorted(Path(directory).glob('TEST-*.xml')):
        root = ET.parse(path).getroot()
        suites.append({'name':root.attrib['name'], 'seconds':float(root.get('time', '0')),
                       **{key:int(root.attrib[key]) for key in ['tests','failures','errors','skipped']}})
    if not suites:
        raise ValueError('No JUnit reports to summarize')
    return {**{key:sum(s[key] for s in suites) for key in ['tests','failures','errors','skipped']},
            'suites':len(suites), 'slowest':sorted(suites,key=lambda s:s['seconds'],reverse=True)[:5]}


if __name__ == '__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('directory')
    parser.add_argument('title')
    args=parser.parse_args()
    result=summarize(args.directory)
    print(json.dumps(result,indent=2))
    summary=os.environ.get('GITHUB_STEP_SUMMARY')
    if summary:
        with Path(summary).open('a',encoding='utf-8') as stream:
            stream.write(f"### {args.title}\n\nTests: {result['tests']}; failures: {result['failures']}; errors: {result['errors']}; skipped: {result['skipped']}.\n\n")
            stream.write('| Slow suite | Seconds |\n| --- | --- |\n')
            for suite in result['slowest']:
                stream.write(f"| `{suite['name']}` | {suite['seconds']:.3f} |\n")
