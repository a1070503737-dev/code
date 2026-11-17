#!/usr/bin/env python3
"""Generate update.json from a verified signed APK; publish it beside that APK."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

from verify_update import inspect, tool


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--notes-file', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    try:
        package, code, _ = inspect(args.apk)
        badging = subprocess.run([tool('aapt'), 'dump', 'badging', str(args.apk)], check=True, capture_output=True, text=True).stdout
        match = re.search(r"versionName='(\d+\.\d+\.\d+)'", badging)
        if package != 'com.zhuangxiu.ledger' or code <= 0 or not match:
            raise ValueError('APK 包名或版本不符合装修记账更新要求')
        version = match[1]
        if args.apk.name != f'renovation-ledger-{version}-signed.apk':
            raise ValueError('APK 文件名必须与包内版本号一致，并使用 signed.apk 后缀')
        size = args.apk.stat().st_size
        if not 0 < size <= 150 * 1024 * 1024:
            raise ValueError('APK 大小不符合更新要求')
        manifest = {
            'schemaVersion': 1,
            'packageName': package,
            'versionName': version,
            'versionCode': code,
            'notes': args.notes_file.read_text(encoding='utf-8').strip()[:12_000],
            'apk': {
                'fileName': args.apk.name,
                'size': size,
                'sha256': hashlib.sha256(args.apk.read_bytes()).hexdigest(),
            },
        }
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        print(f'生成更新文件失败：{error}', file=sys.stderr)
        return 1
    print(f'更新文件已生成：{args.output.name}，版本 {version} / {code}，APK 签名校验通过。')
    return 0


if __name__ == '__main__':
    sys.exit(main())
