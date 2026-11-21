import pytest

from dataflow_tower.jasypt import decrypt

ENCRYPTED = "ENC(TPODWSM60N6gfKiJDvqs56PIz+ZaBvbLGaI83WgcOLGEpud1Awfko3Jy+HiCgPueRuAXTVG9QEE717t2tzJu7g==)"
TEST_ONLY_PASSWORD = "tower-test-only"


def test_decrypts_what_scripts_encrypt_sh_wrote():
    assert decrypt(ENCRYPTED, TEST_ONLY_PASSWORD) == "lake-secret-for-tests"


def test_plain_values_pass_through():
    assert decrypt("plain", None) == "plain"
    assert decrypt(None, None) is None


def test_an_encrypted_value_needs_the_password():
    with pytest.raises(ValueError, match="JASYPT_ENCRYPTOR_PASSWORD"):
        decrypt(ENCRYPTED, "")
