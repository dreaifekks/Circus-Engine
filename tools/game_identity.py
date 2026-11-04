"""What a game directory calls itself: its name and icon, read as data from its files. Nothing is executed."""
from dataclasses import dataclass
from pathlib import Path
import re
import struct
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from circus_tools import mes

RT_ICON, RT_GROUP_ICON, RT_VERSION = 3, 14, 16
NOT_THE_GAME = ('setup', 'dstart', 'uninst', 'unins')


@dataclass(frozen=True)
class Identity:
    name: str
    slug: str
    icon: bytes | None  # A one image .ico file.


def main_exe(game: Path) -> Path | None:
    exes = [p for p in game.iterdir() if p.is_file() and p.suffix.lower() == '.exe' and not p.name.startswith('._')
            and not any(word in p.stem.lower() for word in NOT_THE_GAME)]
    return max(exes, key=lambda p: p.stat().st_size, default=None)


class _Pe:
    def __init__(self, data: bytes):
        self.data = data
        if data[:2] != b'MZ':
            raise ValueError('not a PE file')
        pe = struct.unpack_from('<I', data, 0x3c)[0]
        if data[pe:pe + 4] != b'PE\0\0':
            raise ValueError('not a PE file')
        count, optional = struct.unpack_from('<H', data, pe + 6)[0], struct.unpack_from('<H', data, pe + 20)[0]
        magic = struct.unpack_from('<H', data, pe + 24)[0]
        directories = pe + 24 + (112 if magic == 0x20b else 96)
        self.sections = []
        for n in range(count):
            at = pe + 24 + optional + 40 * n
            size, rva, raw_size, raw = struct.unpack_from('<IIII', data, at + 8)
            self.sections.append((rva, max(size, raw_size), raw))
        rsrc = struct.unpack_from('<I', data, directories + 2 * 8)[0]
        self.root = self.offset(rsrc) if rsrc else None

    def offset(self, rva: int) -> int | None:
        for start, size, raw in self.sections:
            if start <= rva < start + size:
                return rva - start + raw
        return None

    def _entries(self, directory: int) -> list[tuple[int, int]]:
        named, ids = struct.unpack_from('<HH', self.data, directory + 12)
        return [struct.unpack_from('<II', self.data, directory + 16 + 8 * n) for n in range(named + ids)]

    def resources(self, kind: int) -> dict[int, bytes]:
        """Resource id to bytes, taking the first language of each."""
        found = {}
        if self.root is None:
            return found
        for type_id, types in self._entries(self.root):
            if type_id != kind or not types & 0x80000000:
                continue
            for name, names in self._entries(self.root + (types & 0x7fffffff)):
                if not names & 0x80000000:
                    continue
                languages = self._entries(self.root + (names & 0x7fffffff))
                if not languages:
                    continue
                rva, size = struct.unpack_from('<II', self.data, self.root + languages[0][1])
                start = self.offset(rva)
                if start is not None:
                    found[name] = self.data[start:start + size]
        return found


def largest_icon(data: bytes) -> bytes | None:
    """The biggest image of a program's first icon, as an .ico file."""
    pe = _Pe(data)
    groups, images = pe.resources(RT_GROUP_ICON), pe.resources(RT_ICON)
    if not groups:
        return None
    group = groups[min(groups)]
    best = None
    for n in range(struct.unpack_from('<H', group, 4)[0]):
        width, height, colors, _, planes, bits, _, image = struct.unpack_from('<BBBBHHIH', group, 6 + 14 * n)
        if image not in images:
            continue
        rank = (width or 256, bits)
        if best is None or rank > best[0]:
            best = (rank, width, height, colors, planes, bits, images[image])
    if best is None:
        return None
    _, width, height, colors, planes, bits, image = best
    return struct.pack('<HHH', 0, 1, 1) + struct.pack('<BBBBHHII', width, height, colors, 0, planes, bits, len(image), 22) + image


def version_strings(data: bytes) -> dict[str, str]:
    block = next(iter(_Pe(data).resources(RT_VERSION).values()), b'')
    found = {}
    for key in ('ProductName', 'FileDescription'):
        needle = key.encode('utf-16le') + b'\0\0'
        at = block.find(needle)
        if at < 0:
            continue
        at += len(needle)
        while at + 2 <= len(block) and block[at:at + 2] == b'\0\0':
            at += 2
        end = at
        while end + 2 <= len(block) and block[end:end + 2] != b'\0\0':
            end += 2
        found[key] = block[at:end].decode('utf-16le', 'replace').strip()
    return found


def short_title(title: str) -> str:
    """Drops the Japanese subtitle and notes: "D.C.II P.C.～ダ・カーポII～" is D.C.II P.C."""
    title = re.split(r'\s*[～〜~（]', title, maxsplit=1)[0].replace('Ⅲ', 'III').replace('Ⅱ', 'II')
    return re.sub(r'\s*\((?:local|\d{4})[^)]*\)\s*$', '', title).strip()


def _is_title(text: str) -> bool:
    """Most version blocks only hold a code name such as DC2PC; a real title has dots or Japanese."""
    return bool(text) and ('.' in text or not text.isascii())


def _profile_title(game: Path) -> str:
    scripts = next((p for p in game.iterdir() if p.is_dir() and p.name.lower() == 'advdata'), None)
    folder = next((p for p in scripts.iterdir() if p.is_dir() and p.name.lower() == 'mes'), None) if scripts else None
    if folder is None:
        return ''
    paths = sorted(p for p in folder.iterdir() if p.is_file() and p.suffix.lower() == '.mes' and not p.name.startswith('._'))
    profile_id = mes.collection_profile(paths)
    return next((p.get('title', '') for p in mes.profiles() if p['id'] == profile_id), '')


def describe(game: Path) -> Identity:
    exe = main_exe(game)
    data = exe.read_bytes() if exe else b''
    strings, icon = {}, None
    if data:
        try:
            strings, icon = version_strings(data), largest_icon(data)
        except (ValueError, struct.error):
            pass
    code = exe.stem if exe else game.name
    candidates = [strings.get('FileDescription', ''), strings.get('ProductName', '')]
    name = next((short_title(c) for c in candidates if _is_title(c)), '') or short_title(_profile_title(game))
    if len(name) < 5:  # A bare "D.C." says less than the code name does.
        name = code
    return Identity(name.replace('/', '_').replace(':', '_'), re.sub(r'[^a-z0-9]+', '-', code.lower()).strip('-') or 'game', icon)
