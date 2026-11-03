"""Compile the portable core and desktop tools, without Android SDK or Gradle."""
import argparse
import hashlib
from pathlib import Path
import os
import shutil
import subprocess

ROOT = Path(__file__).resolve().parent.parent

def build(java_home=None):
    javac = str(Path(java_home)/'bin'/('javac.exe' if os.name == 'nt' else 'javac')) if java_home else shutil.which('javac')
    if not javac:
        raise RuntimeError('A JDK 17+ is required; set JAVA_HOME or pass --java-home')
    java = str(Path(javac).with_name('java.exe' if os.name == 'nt' else 'java'))
    files = sorted((ROOT/'runtime/core/src').rglob('*.java')) + sorted((ROOT/'runtime/desktop/src').rglob('*.java'))
    files += [ROOT/'runtime/tests/org/circus/core/LocalGameStorage.java',ROOT/'runtime/tests/org/circus/core/RangeStorage.java']
    resources = sorted(p for p in (ROOT/'runtime/core/resources').rglob('*') if p.is_file())
    digest = hashlib.sha256(b''.join(str(p).encode()+p.read_bytes() for p in files+resources)).hexdigest()
    output=ROOT/'build/desktop/classes'; marker=ROOT/'build/desktop/source.sha256'
    if not marker.exists() or marker.read_text(encoding='utf-8') != digest:
        output.mkdir(parents=True, exist_ok=True)
        command=[javac,'-encoding','UTF-8','--release','8','-d',str(output)]+[str(p) for p in files]
        subprocess.run(command,check=True)
        for resource in resources:
            target=output/resource.relative_to(ROOT/'runtime/core/resources')
            target.parent.mkdir(parents=True,exist_ok=True)
            shutil.copyfile(resource,target)
        marker.write_text(digest,encoding='utf-8')
    return java, output

if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home',default=os.environ.get('JAVA_HOME'))
    args=parser.parse_args()
    print(build(args.java_home))
