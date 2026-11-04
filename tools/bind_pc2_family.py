"""Bind the games built on the DC2PC engine to its reviewed opcode names.

Their dispatch tables were compared with DC2PC.exe handler by handler, statically, and aligned in order. Most have the
same numbering; some have three unused opcodes inserted, which only moves the later numbers. Handlers are not identical
in every game, so this says what each opcode is, not that every effect matches its own exe.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
# Profile id: first of the three inserted opcodes, or None.
FAMILY = {'dc2ty-5f69': None, 'dc2sc-3b69': None, 'dc2fl-9c69': None, 'dcdx-7769': None, 'dcas-4e69': None, 'ktlep-6e69': None,
          'dc2bs-316c': 0x28, 'dc2cckko-a96c': 0x28, 'dc2ccotm-a86c': 0x28, 'dcsv-636c': 0x28, 'dcws-656c': 0x2a}


def main():
    path = ROOT / 'profiles/mes-semantics.json'
    data = json.loads(path.read_text(encoding='utf-8'))
    base = data['profiles']['dc2pc-5769']
    for pid, inserted in FAMILY.items():
        move = lambda op: op + 3 if inserted is not None and op >= inserted else op
        opcodes = {f'0x{move(int(op, 16)):02X}': name for op, name in base['opcodes'].items()}
        if inserted is not None:
            opcodes.update({f'0x{inserted + n:02X}': 'native.no_effect' for n in range(3)})
        # Opcodes DC2PC's own scripts never use have no reviewed name; the host records them and moves on.
        for op in range(0x6c if inserted is not None else 0x69):
            opcodes.setdefault(f'0x{op:02X}', 'native.unmodelled')
        data['profiles'][pid] = {
            'runtime_variable_model': base['runtime_variable_model'],
            'host_status': 'bound by comparison with DC2PC.exe; runs through the DC2PC host',
            'decoder': dict(base['decoder'], extension_opcode=move(base['decoder']['extension_opcode'])),
            'control': dict(base['control'], label_opcode=move(base['control']['label_opcode']),
                            block_marker_opcode=move(base['control']['block_marker_opcode'])),
            'opcodes': dict(sorted(opcodes.items())),
        }
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
