#!/usr/bin/env python3
"""Validate executed JUnit reports and inherited integration tags without report-name guesses."""
import argparse
import re
from pathlib import Path
import xml.etree.ElementTree as ET


def integration_classes(source_dir):
    classes = {}
    for path in Path(source_dir).rglob('*.java'):
        text = path.read_text(encoding='utf-8')
        text = re.sub(r'/\*.*?\*/|//[^\n]*', '', text, flags=re.S)
        package = re.search(r'\bpackage\s+([\w.]+)\s*;', text)
        declaration = re.search(r'\b(?:class|interface)\s+(\w+)(?:\s+extends\s+([\w.]+))?', text)
        if not package or not declaration:
            continue
        name, parent = declaration.groups()
        full_name = package[1] + '.' + name
        # Conservatively classify a whole class when a method is tagged integration.
        tagged = bool(re.search(r'@Tag\s*\(\s*"integration"\s*\)', text))
        classes[full_name] = (parent, tagged)
    selected = {name for name, (_, tagged) in classes.items() if tagged}
    while True:
        additions = {name for name, (parent, _) in classes.items()
                     if parent and any(parent == tagged or tagged.endswith('.' + parent) for tagged in selected)}
        if additions.issubset(selected):
            return selected
        selected.update(additions)


def required_suites(manifest):
    return [line.split('#', 1)[0].strip() for line in Path(manifest).read_text(encoding='utf-8').splitlines()
            if line.split('#', 1)[0].strip()]


def validate(directory, phase, source_dir, manifest=None):
    directory = Path(directory)
    if not directory.is_dir():
        raise ValueError('Report directory not found: ' + str(directory))
    reports = sorted(directory.glob('TEST-*.xml'))
    if not reports:
        raise ValueError('No test reports found: ' + str(directory))
    tagged = integration_classes(source_dir)
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    executed_suites = set()
    for path in reports:
        root = ET.parse(path).getroot()
        if root.tag != 'testsuite':
            raise ValueError('Unexpected report root: ' + str(path))
        values = {key: int(root.attrib[key]) for key in totals}
        cases = root.findall('testcase')
        if values['tests'] != len(cases) or any(value < 0 for value in values.values()):
            raise ValueError('Invalid report counts: ' + str(path))
        for key, value in values.items():
            totals[key] += value
        if any(values[key] for key in ('failures', 'errors', 'skipped')) or any(
                case.find(tag) is not None for case in cases for tag in ('failure', 'error', 'skipped')):
            raise ValueError('Failed, errored or skipped tests: ' + str(path))
        names = {root.attrib.get('name', '')} | {case.attrib.get('classname', '') for case in cases}
        for name in names:
            owner = name.split('$', 1)[0]
            if phase == 'unit' and owner in tagged:
                raise ValueError('Integration-tagged class leaked into Surefire: ' + name)
            if phase == 'integration' and owner not in tagged:
                raise ValueError('Non-integration class leaked into Failsafe: ' + name)
        if values['tests'] > 0:
            executed_suites.update(names)
    if totals['tests'] <= 0:
        raise ValueError('Zero tests executed')
    if phase == 'integration':
        if not manifest:
            raise ValueError('Required suite manifest is missing')
        suites = required_suites(manifest)
        if not suites:
            raise ValueError('Required suite manifest is empty')
        for suite in suites:
            if suite not in tagged:
                raise ValueError('Required suite is not an integration-tagged project class: ' + suite)
            if suite not in executed_suites:
                raise ValueError('Required suite did not execute: ' + suite)
    return totals


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('phase', choices=['unit', 'integration'])
    parser.add_argument('reports')
    parser.add_argument('--source-dir', default='backend/src/test/java')
    parser.add_argument('--manifest', default='scripts/ci/required-integration-tests.txt')
    args = parser.parse_args()
    try:
        print(validate(args.reports, args.phase, args.source_dir, args.manifest))
    except (ValueError, OSError, ET.ParseError, KeyError) as error:
        parser.exit(1, 'Report validation failed: ' + str(error) + '\n')
