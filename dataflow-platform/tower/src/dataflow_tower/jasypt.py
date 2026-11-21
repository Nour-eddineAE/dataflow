import base64

from cryptography.hazmat.primitives import hashes, padding
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC

PREFIX = "ENC("
SUFFIX = ")"
SALT_SIZE = 16
IV_SIZE = 16
ITERATIONS = 1000
KEY_SIZE = 32


def decrypt(value: str | None, password: str | None) -> str | None:
    if value is None or not (value.startswith(PREFIX) and value.endswith(SUFFIX)):
        return value
    if not password:
        raise ValueError("an ENC(...) value needs JASYPT_ENCRYPTOR_PASSWORD")
    raw = base64.b64decode(value[len(PREFIX): -len(SUFFIX)])
    salt, iv, body = raw[:SALT_SIZE], raw[SALT_SIZE: SALT_SIZE + IV_SIZE], raw[SALT_SIZE + IV_SIZE:]
    key = PBKDF2HMAC(algorithm=hashes.SHA512(), length=KEY_SIZE, salt=salt, iterations=ITERATIONS).derive(
        password.encode()
    )
    decryptor = Cipher(algorithms.AES(key), modes.CBC(iv)).decryptor()
    padded = decryptor.update(body) + decryptor.finalize()
    unpadder = padding.PKCS7(algorithms.AES.block_size).unpadder()
    return (unpadder.update(padded) + unpadder.finalize()).decode()
