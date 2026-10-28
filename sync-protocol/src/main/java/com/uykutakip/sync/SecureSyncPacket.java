package com.uykutakip.sync;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

public final class SecureSyncPacket {
    public static final int PORT = 47832;
    public static final int MAX_PACKET_BYTES = 32 * 1024 * 1024;
    private static final int WIRE_MAGIC = 0x47545331;
    private static final byte[] AAD = "GunlukTakip-Sync-v1".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    private SecureSyncPacket() {}

    public static void write(DataOutputStream out, byte[] plain, char[] code) throws IOException, GeneralSecurityException {
        var random = new SecureRandom();
        byte[] salt = new byte[16]; byte[] nonce = new byte[12];
        random.nextBytes(salt); random.nextBytes(nonce);
        byte[] encrypted = crypt(Cipher.ENCRYPT_MODE, plain, code, salt, nonce);
        out.writeInt(WIRE_MAGIC); out.write(salt); out.write(nonce); out.writeInt(encrypted.length); out.write(encrypted); out.flush();
    }

    public static byte[] read(DataInputStream in, char[] code) throws IOException, GeneralSecurityException {
        if (in.readInt() != WIRE_MAGIC) throw new IOException("Gecersiz baglanti");
        byte[] salt = new byte[16]; byte[] nonce = new byte[12];
        in.readFully(salt); in.readFully(nonce);
        int size = in.readInt();
        if (size < 16 || size > MAX_PACKET_BYTES) throw new IOException("Gecersiz paket boyutu");
        byte[] encrypted = new byte[size];
        in.readFully(encrypted);
        try { return crypt(Cipher.DECRYPT_MODE, encrypted, code, salt, nonce); }
        catch (AEADBadTagException error) { throw new GeneralSecurityException("Eslesme kodu yanlis veya paket bozuk", error); }
    }

    private static byte[] crypt(int mode, byte[] input, char[] code, byte[] salt, byte[] nonce) throws GeneralSecurityException {
        var spec = new PBEKeySpec(code, salt, 120_000, 256);
        byte[] keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        spec.clearPassword();
        try {
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(AAD);
            return cipher.doFinal(input);
        } finally { java.util.Arrays.fill(keyBytes, (byte) 0); }
    }
}
