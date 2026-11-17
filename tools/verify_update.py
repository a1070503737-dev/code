#!/usr/bin/env python3
"""验证两个 APK 的签名、包名和递增版本号；失败即拒绝作为覆盖更新交付。"""
import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys


def tool(name):
    if found := shutil.which(name):
        return found
    sdk = os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME')
    if sdk:
        candidates = list(Path(sdk).glob(f'build-tools/*/{name}'))
        if candidates:
            return str(max(candidates, key=lambda p: tuple(int(n) for n in re.findall(r'\d+', p.parent.name))))
    raise ValueError(f'找不到 {name}，请设置 ANDROID_HOME 或把 build-tools 加入 PATH')


def inspect(apk):
    result = subprocess.run([tool('apksigner'), 'verify', '--print-certs', str(apk)], capture_output=True, text=True)
    if result.returncode:
        raise ValueError(f'{apk.name} 未通过签名验证（可能未签名）')
    certs = set(re.findall(r'certificate SHA-256 digest:\s*([0-9a-fA-F]{64})', result.stdout))
    if not certs:
        raise ValueError(f'{apk.name} 无法读取签名指纹')
    result = subprocess.run([tool('aapt'), 'dump', 'badging', str(apk)], check=True, capture_output=True, text=True)
    match = re.search(r"package: name='([^']+)' versionCode='(\d+)'", result.stdout)
    if not match:
        raise ValueError(f'{apk.name} 无法读取包名/版本')
    return match[1], int(match[2]), {c.lower() for c in certs}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('original', type=Path)
    parser.add_argument('updated', type=Path)
    args = parser.parse_args()
    try:
        old, new = inspect(args.original), inspect(args.updated)
        if old[0] != new[0]:
            raise ValueError('包名不同，不能覆盖安装')
        if old[2] != new[2]:
            raise ValueError('签名不同，不能覆盖安装；必须使用原签名密钥')
        if new[1] <= old[1]:
            raise ValueError('更新包 versionCode 必须高于原版')
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        print(f'校验失败：{error}', file=sys.stderr)
        return 1
    print(f'校验通过：包名 {new[0]}，versionCode {old[1]} → {new[1]}，签名相同。')
    print('以上为安装元数据验证；仍需在测试设备上验证覆盖升级及账本数据。')
    return 0


if __name__ == '__main__':
    sys.exit(main())
