"""Deploy the existing FoodHub API using password SFTP and a verified host key.

Credentials are read from ignored local files, never command-line arguments.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import hmac
import json
from pathlib import Path
import re
import shlex
import stat
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'build/alwaysdata-deploy/python-libs'))
import paramiko

HOST = 'ssh-chinh.alwaysdata.net'
USER = 'chinh'
HOME = '/home/chinh'
PUBLIC = HOME + '/www/food-delivery-api'
PRIVATE = HOME + '/private/foodhub'
BUNDLE = ROOT / 'build/alwaysdata-deploy/bundle'
LOCAL = Path(__file__).resolve().parent


class FingerprintMismatch(RuntimeError):
    pass


class VerifiedFingerprint(paramiko.MissingHostKeyPolicy):
    def __init__(self, expected: list[str]):
        self.expected = expected

    def missing_host_key(self, client, hostname, key):
        actual = base64.b64encode(hashlib.sha256(key.asbytes()).digest()).decode().rstrip('=')
        if not any(hmac.compare_digest(actual, value) for value in self.expected):
            raise FingerprintMismatch('Server fingerprint does not match the trusted value from Alwaysdata.')


def connect():
    password = (LOCAL / 'alwaysdata.password.local.txt').read_text(encoding='utf-8-sig').rstrip('\r\n')
    fingerprints = (LOCAL / 'alwaysdata.hostkey.local.txt').read_text(encoding='utf-8-sig')
    matches = re.findall(r'SHA256:([A-Za-z0-9+/]+)', fingerprints)
    if not password or not matches:
        raise RuntimeError('Fill the password and SHA256 ED25519 fingerprint in the two local files first.')
    expected = [value.rstrip('=') for value in matches]
    # The dashboard can show RSA or ECDSA instead of ED25519. Try those key
    # algorithms too, always requiring an exact trusted SHA256 match BEFORE auth.
    variants = [None,
                {'keys': ['ssh-ed25519', 'ecdsa-sha2-nistp256', 'ecdsa-sha2-nistp384', 'ecdsa-sha2-nistp521']},
                {'keys': ['ssh-ed25519', 'rsa-sha2-512', 'rsa-sha2-256', 'ssh-rsa']}]
    for disabled in variants:
        client = paramiko.SSHClient()
        client.set_missing_host_key_policy(VerifiedFingerprint(expected))
        try:
            client.connect(HOST, port=22, username=USER, password=password, timeout=15,
                           banner_timeout=20, auth_timeout=20, allow_agent=False,
                           look_for_keys=False, disabled_algorithms=disabled)
            return client
        except FingerprintMismatch:
            client.close()
    raise FingerprintMismatch('No supported SSH host key matches the fingerprint from the local file.')


def run(client, command, timeout=60):
    _, stdout, stderr = client.exec_command(command, timeout=timeout)
    output = stdout.read().decode('utf-8', errors='replace')
    errors = stderr.read().decode('utf-8', errors='replace')
    code = stdout.channel.recv_exit_status()
    if code:
        # Do not display arbitrary PHP exceptions that could contain credentials.
        raise RuntimeError(f'Remote check failed (exit {code}); output was withheld.')
    return output


def mkdir(sftp, directory, mode):
    if not directory.startswith(HOME + '/'):
        raise RuntimeError('Unexpected destination outside the account home.')
    try:
        info = sftp.lstat(directory)
    except FileNotFoundError:
        parent = directory.rsplit('/', 1)[0]
        if parent != HOME:
            mkdir(sftp, parent, 0o700 if '/private' in parent else 0o755)
        sftp.mkdir(directory, mode)
        info = sftp.lstat(directory)
    if not stat.S_ISDIR(info.st_mode):
        raise RuntimeError('Expected a real directory, not a symlink or file.')


def upload_atomic(sftp, source, destination, mode):
    mkdir(sftp, destination.rsplit('/', 1)[0], 0o700 if destination.startswith(PRIVATE) else 0o755)
    temporary = destination + '.upload-' + str(int(time.time()))
    sftp.put(str(source), temporary)
    sftp.chmod(temporary, mode)
    sftp.posix_rename(temporary, destination)


def preflight(client):
    php = r'''<?php
    require '/home/chinh/www/food-delivery-api/database/connection.php';
    $c=require '/home/chinh/private/foodhub/config.local.php';
    try {
        $p=foodhubConnectDatabase($c['mysql']);
        $tables=(int)$p->query("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE'")->fetchColumn();
        $missing=(int)$p->query("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND column_name='id' AND column_key='PRI' AND extra NOT LIKE '%auto_increment%'")->fetchColumn();
        $sa=json_decode(file_get_contents($c['firebase_service_account_file']),true);
        if(($sa['project_id']??'')!==$c['firebase_project_id'])throw new RuntimeException('Project mismatch');
        $certs=file_get_contents('https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com');
        if(!is_array(json_decode($certs,true)))throw new RuntimeException('Firebase HTTPS failed');
        $result=['php'=>PHP_VERSION,'database'=>$p->query('SELECT DATABASE()')->fetchColumn(),'tables'=>$tables,'missing_auto_increment'=>$missing,'firebase_https'=>true,'service_account_readable'=>true];
        if($result['database']!=='food_delivery_app'||$tables!==32||$missing)throw new RuntimeException('Database mismatch');
        echo json_encode($result,JSON_UNESCAPED_SLASHES).PHP_EOL;
    }catch(Throwable $e){echo 'Preflight failed: '.get_class($e).' code '.$e->getCode().PHP_EOL;exit(1);}
    '''
    command = 'php -d display_errors=0 -r ' + shlex.quote(php.removeprefix('<?php'))
    return json.loads(run(client, command))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['probe', 'deploy', 'check'])
    args = parser.parse_args()
    client = connect()
    try:
        sftp = client.open_sftp()
        if sftp.normalize('.') != HOME:
            raise RuntimeError('Unexpected SSH home directory.')
        if args.action == 'probe':
            print(json.dumps({'host': HOST, 'home': sftp.normalize('.'),
                              'www_exists': any(f.filename == 'www' and stat.S_ISDIR(f.st_mode) for f in sftp.listdir_attr(HOME)),
                              'php': run(client, "php -r 'echo PHP_VERSION;' ").strip()}))
            return
        if args.action == 'deploy':
            manifest = json.loads((BUNDLE.parent / 'manifest.json').read_text())['files']
            for name, expected in manifest.items():
                if hashlib.sha256((BUNDLE / name).read_bytes()).hexdigest() != expected:
                    raise RuntimeError('Deployment bundle changed; regenerate it first.')
            mkdir(sftp, PRIVATE, 0o700)
            mkdir(sftp, PRIVATE + '/cache', 0o700)
            backup = PRIVATE + '/backups/' + time.strftime('%Y%m%d-%H%M%S', time.gmtime())
            mkdir(sftp, backup, 0o700)
            # Back up only the existing app directory and private inputs, not other sites.
            for relative in manifest:
                area, name = relative.split('/', 1)
                destination = (PUBLIC if area == 'public' else PRIVATE) + '/' + name
                try:
                    previous = sftp.lstat(destination)
                except FileNotFoundError:
                    continue
                if not stat.S_ISREG(previous.st_mode):
                    raise RuntimeError('Unexpected symlink or special file at deployment destination.')
                target = backup + '/' + relative
                mkdir(sftp, target.rsplit('/', 1)[0], 0o700)
                with sftp.open(destination, 'rb') as src, sftp.open(target, 'wb') as dst:
                    dst.write(src.read())
                sftp.chmod(target, 0o600)
            # Upload the entrypoint last so private config and dependencies exist first.
            ordered = sorted(manifest, key=lambda n: (n == 'public/api/index.php', n.startswith('public/'), n))
            for relative in ordered:
                area, name = relative.split('/', 1)
                destination = (PUBLIC if area == 'public' else PRIVATE) + '/' + name
                upload_atomic(sftp, BUNDLE / relative, destination, 0o600 if area == 'private' else 0o644)
            for relative in manifest:
                area, name = relative.split('/', 1)
                destination = (PUBLIC if area == 'public' else PRIVATE) + '/' + name
                with sftp.open(destination, 'rb') as file:
                    if hashlib.sha256(file.read()).hexdigest() != manifest[relative]:
                        raise RuntimeError('Upload checksum failed.')
            php_files = [PUBLIC+'/api/index.php', PUBLIC+'/runtime.php', PUBLIC+'/database/connection.php', PRIVATE+'/config.local.php']
            for file in php_files:
                run(client, 'php -l ' + shlex.quote(file))
            print(json.dumps({'uploaded_files': len(manifest), 'backup_directory': backup}))
        result = preflight(client)
        print(json.dumps({'preflight': result}))
        (BUNDLE.parent / 'deployment-result.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    finally:
        client.close()


if __name__ == '__main__':
    try:
        main()
    except Exception as exc:
        print(f'Deployment stopped: {type(exc).__name__}. {str(exc) if isinstance(exc, RuntimeError) else "No credentials were printed."}', file=sys.stderr)
        sys.exit(1)
