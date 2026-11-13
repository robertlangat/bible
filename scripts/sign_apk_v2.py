#!/usr/bin/env python3
"""Sign a zipaligned APK with APK Signature Scheme v2 (RSA PKCS#1 v1.5 + SHA-256).

For machines without the Android SDK's apksigner. Valid for minSdk >= 24 (v2 only, no v1 JAR
signature). Always check the result with `apksigner verify` (the Bible Daily CI does this).

Usage: sign_apk_v2.py <unsigned-aligned.apk> <out.apk> <keystore.p12> <alias-unused> <password-file>
Spec: https://source.android.com/docs/security/features/apksigning/v2
"""
import hashlib
import struct
import sys

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.serialization import pkcs12

V2_BLOCK_ID = 0x7109871A
ALGO_RSA_PKCS1_SHA256 = 0x0103
CHUNK = 1024 * 1024
MAGIC = b"APK Sig Block 42"


def lp(b: bytes) -> bytes:
    """uint32 little-endian length prefix."""
    return struct.pack("<I", len(b)) + b


def find_eocd(data: bytes) -> int:
    for i in range(len(data) - 22, max(-1, len(data) - 22 - 65536) - 1, -1):
        if data[i:i + 4] == b"PK\x05\x06":
            comment_len = struct.unpack_from("<H", data, i + 20)[0]
            if i + 22 + comment_len == len(data):
                return i
    raise ValueError("End of central directory not found")


def chunked_digest(sections) -> bytes:
    digests = []
    for section in sections:
        for off in range(0, len(section), CHUNK):
            chunk = section[off:off + CHUNK]
            digests.append(hashlib.sha256(b"\xa5" + struct.pack("<I", len(chunk)) + chunk).digest())
    return hashlib.sha256(b"\x5a" + struct.pack("<I", len(digests)) + b"".join(digests)).digest()


def main():
    src, dst, ks_path, _alias, pw_file = sys.argv[1:6]
    password = open(pw_file, "rb").read().strip()
    key, cert, _ = pkcs12.load_key_and_certificates(open(ks_path, "rb").read(), password)
    cert_der = cert.public_bytes(serialization.Encoding.DER)
    pub_der = key.public_key().public_bytes(
        serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)

    data = open(src, "rb").read()
    eocd = find_eocd(data)
    cd_offset = struct.unpack_from("<I", data, eocd + 16)[0]
    cd_size = struct.unpack_from("<I", data, eocd + 12)[0]
    if cd_offset + cd_size != eocd:
        raise ValueError("Unexpected data between central directory and EOCD")
    if data[cd_offset - 16:cd_offset] == MAGIC:
        raise ValueError("APK already has a signing block")

    contents, central_dir, eocd_bytes = data[:cd_offset], data[cd_offset:eocd], data[eocd:]
    # For digesting, the EOCD's central-directory offset points at the start of the signing block,
    # which is where the central directory starts in the unsigned file: i.e. unchanged here.
    digest = chunked_digest([contents, central_dir, eocd_bytes])

    signed_data = (
        lp(lp(struct.pack("<I", ALGO_RSA_PKCS1_SHA256) + lp(digest)))  # digests
        + lp(lp(cert_der))                                            # certificates
        + lp(b"")                                                     # additional attributes
    )
    signature = key.sign(signed_data, padding.PKCS1v15(), hashes.SHA256())
    signer = lp(signed_data) + lp(lp(struct.pack("<I", ALGO_RSA_PKCS1_SHA256) + lp(signature))) + lp(pub_der)
    v2_value = lp(lp(signer))

    pair = struct.pack("<I", V2_BLOCK_ID) + v2_value
    pairs = struct.pack("<Q", len(pair)) + pair
    block_size = len(pairs) + 8 + 16  # pairs + trailing size + magic
    block = struct.pack("<Q", block_size) + pairs + struct.pack("<Q", block_size) + MAGIC

    new_eocd = bytearray(eocd_bytes)
    struct.pack_into("<I", new_eocd, 16, cd_offset + len(block))
    with open(dst, "wb") as f:
        f.write(contents + block + central_dir + bytes(new_eocd))
    print(f"Signed {dst}: v2 block {len(block)} bytes, cert SHA-256 {hashlib.sha256(cert_der).hexdigest()}")


if __name__ == "__main__":
    main()
