"""Stamp release versions and verify immutable release tags before retrying."""

import argparse
import re
import subprocess
from pathlib import Path


def git(*args):
    return subprocess.check_output(['git', *args], text=True).strip()


def validate_version(version):
    if not re.fullmatch(r'(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)', version):
        raise ValueError('Use a stable version such as 2.0.1, without v or -SNAPSHOT')


def stamp(content, version):
    validate_version(version)
    pattern = r'(<revision>)[^<]+(</revision>)'
    if len(re.findall(pattern, content)) != 1:
        raise ValueError('Expected exactly one revision property in the root POM')
    return re.sub(pattern, lambda match: match[1] + version + match[2], content)


def verify_tag(version, default_ref):
    validate_version(version)
    tag = f'refs/tags/v{version}'
    commit = git('rev-parse', f'{tag}^{{commit}}')
    parents = git('rev-list', '--parents', '-n', '1', commit).split()
    if len(parents) != 2:
        raise ValueError('A release commit must have exactly one parent')
    parent = parents[1]
    subprocess.run(['git', 'merge-base', '--is-ancestor', parent, default_ref], check=True)
    if git('diff', '--name-only', parent, commit) != 'pom.xml':
        raise ValueError('A release tag must change only the root POM from the default branch')
    before = subprocess.check_output(['git', 'show', f'{parent}:pom.xml'], text=True)
    after = subprocess.check_output(['git', 'show', f'{commit}:pom.xml'], text=True)
    if after != stamp(before, version):
        raise ValueError('The tagged POM must differ only by the requested revision')
    return commit


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['validate', 'stamp', 'verify-tag'])
    parser.add_argument('version')
    parser.add_argument('--default-ref', default='origin/main')
    args = parser.parse_args()
    validate_version(args.version)
    if args.command == 'stamp':
        pom = Path('pom.xml')
        pom.write_text(stamp(pom.read_text(), args.version))
    elif args.command == 'verify-tag':
        print(verify_tag(args.version, args.default_ref))


if __name__ == '__main__':
    main()
