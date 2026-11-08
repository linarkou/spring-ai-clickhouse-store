import os
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from release import stamp, validate_version, verify_tag


POM = '<project><version>${revision}</version><properties><revision>2.0.1-SNAPSHOT</revision></properties></project>\n'


class VersionTests(unittest.TestCase):
    def test_stable_versions(self):
        for version in ['0.1.0', '2.0.1', '12.34.56']:
            validate_version(version)

    def test_rejects_snapshots_prereleases_and_shell_input(self):
        for version in ['', 'v2.0.1', '2.0', '02.0.1', '2.0.1-SNAPSHOT', '2.0.1-rc1', '2.0.1\n', '$(id)']:
            with self.subTest(version=version), self.assertRaises(ValueError):
                validate_version(version)

    def test_stamps_only_revision(self):
        self.assertEqual(stamp(POM, '2.1.0'), POM.replace('2.0.1-SNAPSHOT', '2.1.0'))

    def test_rejects_missing_or_duplicate_revision(self):
        for content in ['<project/>', POM + '<revision>1.0.0</revision>']:
            with self.assertRaises(ValueError):
                stamp(content, '2.1.0')


class TagTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.cwd = patch.dict(os.environ, {'GIT_CONFIG_NOSYSTEM': '1', 'GIT_CONFIG_GLOBAL': os.devnull})
        self.cwd.start()
        self.addCleanup(self.cwd.stop)
        previous = Path.cwd()
        os.chdir(self.directory.name)
        self.addCleanup(os.chdir, previous)
        self.git('init', '-b', 'main')
        self.git('config', 'user.email', 'test@example.com')
        self.git('config', 'user.name', 'Release test')
        Path('pom.xml').write_text(POM)
        Path('code.txt').write_text('original\n')
        self.commit('Initial')
        self.git('checkout', '-b', 'release')

    def git(self, *args):
        return subprocess.check_output(['git', *args], text=True, stderr=subprocess.DEVNULL).strip()

    def commit(self, message):
        self.git('add', '.')
        self.git('commit', '-m', message)

    def tag(self):
        self.commit('Release v2.0.1')
        self.git('tag', '-a', 'v2.0.1', '-m', 'Release v2.0.1')

    def test_accepts_release_commit_and_later_default_branch_changes(self):
        Path('pom.xml').write_text(stamp(POM, '2.0.1'))
        self.tag()
        release_commit = self.git('rev-parse', 'HEAD')
        self.git('checkout', 'main')
        Path('code.txt').write_text('later change\n')
        self.commit('Continue development')
        self.assertEqual(verify_tag('2.0.1', 'main'), release_commit)

    def test_rejects_tag_with_code_changes(self):
        Path('pom.xml').write_text(stamp(POM, '2.0.1'))
        Path('code.txt').write_text('unexpected change\n')
        self.tag()
        with self.assertRaises(ValueError):
            verify_tag('2.0.1', 'main')

    def test_rejects_pom_changes_beyond_revision(self):
        Path('pom.xml').write_text(stamp(POM, '2.0.1').replace('<project>', '<project><name>Changed</name>'))
        self.tag()
        with self.assertRaises(ValueError):
            verify_tag('2.0.1', 'main')

    def test_rejects_wrong_tag_revision(self):
        Path('pom.xml').write_text(stamp(POM, '2.0.2'))
        self.tag()
        with self.assertRaises(ValueError):
            verify_tag('2.0.1', 'main')

    def test_rejects_parent_outside_default_branch(self):
        Path('code.txt').write_text('unmerged branch\n')
        self.commit('Unmerged feature')
        Path('pom.xml').write_text(stamp(POM, '2.0.1'))
        self.tag()
        with self.assertRaises(subprocess.CalledProcessError):
            verify_tag('2.0.1', 'main')


if __name__ == '__main__':
    unittest.main()
