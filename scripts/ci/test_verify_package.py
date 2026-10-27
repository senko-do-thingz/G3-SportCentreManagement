import hashlib
import tempfile
import unittest
from pathlib import Path
from verify_package import verify


class ArtifactIntegrityTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root/'app.jar').write_bytes(b'Synthetic jar integrity fixture')
        (self.root/'source-sha.txt').write_text('a'*40+'\n')
        self.manifest = '\n'.join(hashlib.sha256((self.root/name).read_bytes()).hexdigest()+'  '+name
                                  for name in ['app.jar', 'source-sha.txt'])+'\n'
        (self.root/'SHA256SUMS').write_text(self.manifest)

    def test_exact_source_and_bytes_pass(self):
        self.assertEqual(verify(self.root, 'a'*40), 'app.jar')

    def test_stale_source_tamper_missing_and_duplicate_entries_fail(self):
        with self.assertRaises(ValueError): verify(self.root, 'b'*40)
        for contents in [self.manifest+self.manifest, self.manifest.splitlines()[0]+'\n',
                         '0'*64+'  ../other.jar\n']:
            (self.root/'SHA256SUMS').write_text(contents)
            with self.assertRaises(ValueError): verify(self.root, 'a'*40)
        (self.root/'SHA256SUMS').write_text(self.manifest)
        (self.root/'app.jar').write_bytes(b'Tampered')
        with self.assertRaises(ValueError): verify(self.root, 'a'*40)

    def test_missing_or_ambiguous_artifact_fails(self):
        (self.root/'other.jar').write_bytes(b'Unexpected')
        with self.assertRaises(ValueError): verify(self.root, 'a'*40)
        with self.assertRaises(ValueError): verify(self.root, '')
