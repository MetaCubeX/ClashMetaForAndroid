#!/usr/bin/env python3
"""Write signing.properties with java.util.Properties-compatible escaping."""

from __future__ import annotations

import os
from pathlib import Path


def escape(value: str) -> str:
    out = []
    for ch in value:
        code = ord(ch)
        if ch == "\\":
            out.append("\\\\")
        elif ch == "\n":
            out.append("\\n")
        elif ch == "\r":
            out.append("\\r")
        elif ch == "\t":
            out.append("\\t")
        elif ch == "\f":
            out.append("\\f")
        elif ch in "=:#! ":
            out.append("\\" + ch)
        elif code < 0x20 or code > 0x7E:
            out.append(f"\\u{code:04x}")
        else:
            out.append(ch)
    return "".join(out)


def required(name: str) -> str:
    value = os.environ.get(name, "")
    if not value:
        raise SystemExit(f"{name} is not configured")
    return value


values = {
    "keystore.password": required("SIGNING_STORE_PASSWORD"),
    "key.alias": required("SIGNING_KEY_ALIAS"),
    "key.password": required("SIGNING_KEY_PASSWORD"),
}

Path("signing.properties").write_text(
    "".join(f"{key}={escape(value)}\n" for key, value in values.items()),
    encoding="ascii",
)
