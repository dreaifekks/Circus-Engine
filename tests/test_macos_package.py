"""Game name and icon for the Mac app, from small artificial files."""
from pathlib import Path
import struct
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / 'tools'))
import game_identity


def resource_section(rva):
    """A resource tree of one 16 px icon group and one version block."""
    image = b'\x89PNG\r\n\x1a\nfake'
    small = b'small'
    group = struct.pack('<HHH', 0, 1, 2) + struct.pack('<BBBBHHIH', 16, 16, 0, 0, 1, 32, len(small), 1) + \
        struct.pack('<BBBBHHIH', 0, 0, 0, 0, 1, 32, len(image), 2)
    version = 'ProductName'.encode('utf-16le') + b'\0\0' + 'DC2PC'.encode('utf-16le') + b'\0\0' + \
        'FileDescription'.encode('utf-16le') + b'\0\0\0\0' + 'D.C.Test～サブ～'.encode('utf-16le') + b'\0\0'
    blobs = {(3, 1): small, (3, 2): image, (14, 1): group, (16, 1): version}
    kinds = sorted({kind for kind, _ in blobs})

    def directory(count):
        return struct.pack('<IIHHHH', 0, 0, 0, 0, 0, count)

    size = 16 + 8 * len(kinds)
    placed = {}
    for kind in kinds:
        ids = [i for k, i in blobs if k == kind]
        placed[kind] = size
        size += 16 + 8 * len(ids)
        for i in ids:
            placed[(kind, i)] = size
            size += 16 + 8
    for key in blobs:
        placed[('entry', key)] = size
        size += 16
    data_at = size
    out = bytearray(data_at)
    out[:16] = directory(len(kinds))
    for n, kind in enumerate(kinds):
        struct.pack_into('<II', out, 16 + 8 * n, kind, placed[kind] | 0x80000000)
        ids = [i for k, i in blobs if k == kind]
        out[placed[kind]:placed[kind] + 16] = directory(len(ids))
        for m, i in enumerate(ids):
            struct.pack_into('<II', out, placed[kind] + 16 + 8 * m, i, placed[(kind, i)] | 0x80000000)
            at = placed[(kind, i)]
            out[at:at + 16] = directory(1)
            struct.pack_into('<II', out, at + 16, 1041, placed[('entry', (kind, i))])
    for key, blob in blobs.items():
        struct.pack_into('<II', out, placed[('entry', key)], rva + len(out), len(blob))
        out += blob
    return bytes(out), image


def fake_exe():
    section, image = resource_section(0x1000)
    pe = 0x40
    optional = 224
    headers = pe + 24 + optional + 40
    raw = (headers + 0x1ff) & ~0x1ff
    data = bytearray(raw + len(section))
    data[:2] = b'MZ'
    struct.pack_into('<I', data, 0x3c, pe)
    data[pe:pe + 4] = b'PE\0\0'
    struct.pack_into('<H', data, pe + 6, 1)
    struct.pack_into('<H', data, pe + 20, optional)
    struct.pack_into('<H', data, pe + 24, 0x10b)
    struct.pack_into('<II', data, pe + 24 + 96 + 16, 0x1000, len(section))
    struct.pack_into('<IIII', data, pe + 24 + optional + 8, len(section), 0x1000, len(section), raw)
    data[raw:] = section
    return bytes(data), image


class GameIdentityTests(unittest.TestCase):
    def test_short_title_drops_subtitle_and_notes(self):
        self.assertEqual(game_identity.short_title('D.C.II P.C.～ダ・カーポII～'), 'D.C.II P.C.')
        self.assertEqual(game_identity.short_title('D.C.Ⅲ DreamDays～ダ・カーポⅢ～'), 'D.C.III DreamDays')
        self.assertEqual(game_identity.short_title('D.C.P.C. DVD (local 63 20)'), 'D.C.P.C. DVD')

    def test_reads_title_and_largest_icon(self):
        exe, image = fake_exe()
        with tempfile.TemporaryDirectory() as folder:
            game = Path(folder)
            (game / 'Test_Game.exe').write_bytes(exe)
            (game / 'setup.exe').write_bytes(exe + b'\0' * 4096)
            found = game_identity.describe(game)
        self.assertEqual(found.name, 'D.C.Test')
        self.assertEqual(found.slug, 'test-game')
        self.assertTrue(found.icon.endswith(image))
        self.assertEqual(struct.unpack_from('<HHH', found.icon), (0, 1, 1))

    def test_a_game_without_exe_uses_its_folder(self):
        with tempfile.TemporaryDirectory() as folder:
            game = Path(folder) / 'Some Game'
            game.mkdir()
            found = game_identity.describe(game)
        self.assertEqual(found.name, 'Some Game')
        self.assertEqual(found.slug, 'some-game')
        self.assertIsNone(found.icon)


if __name__ == '__main__':
    unittest.main()
