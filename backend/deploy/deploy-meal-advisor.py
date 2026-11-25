"""Deploy just the meal adviser, retaining existing hosting configuration.

Uses the existing verified SSH connection; never prints API keys or private config.
"""
from pathlib import Path
import argparse
import hashlib
import importlib.util
import json
import shlex
import stat
import subprocess
import time

spec = importlib.util.spec_from_file_location('foodhub_deploy', Path(__file__).with_name('alwaysdata.py'))
deploy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(deploy)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--sync-key', action='store_true', help='Upload the private Groq key file to the hosting private directory')
    parser.add_argument('--allow-router-update', action='store_true', help='Allow replacing api/index.php when hosting already has an uncommitted earlier deploy')
    args = parser.parse_args()
    client = deploy.connect()
    try:
        sftp = client.open_sftp()
        if sftp.normalize('.') != deploy.HOME:
            raise RuntimeError('Unexpected hosting directory')
        files = [
            'runtime.php',
            'database/connection.php',
            'ai/FoodHubSystemPrompt.php',
            'ai/FoodHubKnowledgeBase.php',
            'ai/ConversationContext.php',
            'ai/UserPersonalization.php',
            'ai/AiQualityMonitor.php',
            'ai/FoodHubSupport.php',
            'ai/MenuKnowledge.php',
            'ai/MealChat.php',
            'ai/MealAdvisor.php',
            'ai/AiOrderDraftService.php',
            'ai/AiOrderingOrchestrator.php',
            'ai/meal-advisor-route.php',
            'ai/ai-order-draft-route.php',
            'ai/ai-personalization-route.php',
            'recommendation/SearchHistoryService.php',
            'recommendation/RecommendationEngine.php',
            'recommendation/recommendation-route.php',
            'api/index.php',
        ]
        current_index = sftp.open(deploy.PUBLIC + '/api/index.php', 'rb').read()
        old_index = subprocess.check_output(['git', 'show', 'HEAD:backend/api/index.php'], cwd=deploy.ROOT)
        new_index = (deploy.ROOT / 'backend/api/index.php').read_bytes()
        normalize = lambda b: b.replace(b'\r\n', b'\n')
        if not args.allow_router_update and normalize(current_index) not in (normalize(old_index), normalize(new_index)):
            raise RuntimeError('Hosting router differs from both HEAD and local source. Review before deploying.')
        backup = deploy.PRIVATE + '/backups/meal-advisor-' + time.strftime('%Y%m%d-%H%M%S', time.gmtime())
        deploy.mkdir(sftp, backup, 0o700)
        for name in files:
            try:
                path = deploy.PUBLIC + '/' + name
                if not stat.S_ISREG(sftp.lstat(path).st_mode):
                    raise RuntimeError('Unexpected deployment destination')
                target = backup + '/' + name
                deploy.mkdir(sftp, target.rsplit('/', 1)[0], 0o700)
                with sftp.open(path, 'rb') as src, sftp.open(target, 'wb') as dst:
                    dst.write(src.read())
                sftp.chmod(target, 0o600)
            except FileNotFoundError:
                pass
        # Lint staged code before replacing the public entrypoint.
        for name in files:
            stage = backup + '/candidate/' + name
            deploy.upload_atomic(sftp, deploy.ROOT / 'backend' / name, stage, 0o600)
            deploy.run(client, 'php -l ' + shlex.quote(stage))
        if args.sync_key:
            local = deploy.ROOT / 'backend/deploy/groq.apikey.local.txt'
            key = local.read_text(encoding='utf-8-sig').strip()
            if not key.startswith('gsk_') or any(c.isspace() for c in key):
                raise ValueError('Invalid local Groq API key format')
            deploy.upload_atomic(sftp, local, deploy.PRIVATE + '/groq.apikey.local.txt', 0o600)
        for name in files:
            dest = deploy.PUBLIC + '/' + name
            deploy.mkdir(sftp, dest.rsplit('/', 1)[0], 0o755)
            deploy.upload_atomic(sftp, deploy.ROOT / 'backend' / name, dest, 0o644)
            expected = hashlib.sha256((deploy.ROOT / 'backend' / name).read_bytes()).hexdigest()
            actual = hashlib.sha256(sftp.open(dest, 'rb').read()).hexdigest()
            if actual != expected:
                raise RuntimeError('Uploaded checksum mismatch')
        check = """$base='/home/chinh/www/food-delivery-api';require $base.'/ai/MealAdvisor.php';
            $g=MealAdvisor::groqSettings($base);
            echo json_encode(['configured'=>$g['api_key']!=='','model'=>$g['model']]);"""
        configured = json.loads(deploy.run(client, 'php -r ' + shlex.quote(check)))
        print(json.dumps({'deployed': files, 'checksums_verified': True, 'php_lint': True,
                          'groq': configured, 'backup': backup}))
    finally:
        client.close()


if __name__ == '__main__':
    main()
