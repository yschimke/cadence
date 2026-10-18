"""Fast checks for the devcontainer's provisioning and dependency access contract."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parent
CONFIG = json.loads((ROOT / 'devcontainer.json').read_text())


class DevcontainerConfigTest(unittest.TestCase):
    def test_dependency_hosts_are_allowed_in_both_firewall_inputs(self):
        hosts = {line.strip() for line in (ROOT / 'allowed-domains.txt').read_text().splitlines()
                 if line.strip() and not line.lstrip().startswith('#')}
        env_hosts = set(CONFIG['containerEnv']['COOEE_ALLOWED_DOMAINS'].split(','))
        self.assertEqual(hosts, env_hosts)
        self.assertLessEqual({'repo.maven.apache.org', 'plugins.gradle.org',
                              'oss.sonatype.org', 'jogamp.org'}, hosts)

    def test_failed_download_fails_provisioning(self):
        with tempfile.TemporaryDirectory() as directory:
            curl = Path(directory) / 'curl'
            curl.write_text('#!/bin/sh\nexit 22\n')
            curl.chmod(0o755)
            result = subprocess.run(['bash', '-c', CONFIG['postCreateCommand']],
                                    env={**os.environ, 'PATH': directory + os.pathsep + os.environ['PATH']},
                                    capture_output=True, text=True)
            self.assertEqual(result.returncode, 22, result.stderr)

    def test_successful_download_executes_installer(self):
        with tempfile.TemporaryDirectory() as directory:
            curl = Path(directory) / 'curl'
            curl.write_text("#!/bin/sh\nprintf 'printf provisioned\\\\n'\n")
            curl.chmod(0o755)
            result = subprocess.run(['bash', '-c', CONFIG['postCreateCommand']],
                                    env={**os.environ, 'PATH': directory + os.pathsep + os.environ['PATH']},
                                    capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn('provisioned', result.stdout)


if __name__ == '__main__':
    unittest.main()
