package com.gathin.tvhome.backlight;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.zip.CRC32;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Tuya's local protocol 3.4, just enough to set one data point: a 55AA frame with an HMAC-SHA256 trailer, a
 * three-step session key negotiation, then AES-ECB with the session key. Matches tinytuya (the tests use its
 * frames). No cloud: the box listens on TCP 6668 on the home network.
 */
final class Tuya {
    private Tuya() {
    }

    static final int PORT = 6668, BROADCAST_PORT = 6667;
    static final int NEG_START = 3, NEG_RESP = 4, NEG_FINISH = 5, CONTROL_NEW = 13;
    private static final int PREFIX = 0x000055AA, SUFFIX = 0x0000AA55;
    private static final byte[] VERSION_HEADER = new byte[15];
    /** The key every Tuya device encrypts its UDP announcement with: md5("yGAdlopoPVldABfn"). */
    private static final byte[] UDP_KEY;

    static {
        System.arraycopy("3.4".getBytes(StandardCharsets.US_ASCII), 0, VERSION_HEADER, 0, 3);
        try {
            UDP_KEY = MessageDigest.getInstance("MD5").digest("yGAdlopoPVldABfn".getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A received frame: command, return code (0 when the frame has none) and the still-encrypted payload. */
    static final class Frame {
        final int seq, cmd, retcode;
        final byte[] payload;

        Frame(int seq, int cmd, int retcode, byte[] payload) {
            this.seq = seq;
            this.cmd = cmd;
            this.retcode = retcode;
            this.payload = payload;
        }
    }

    static byte[] aes(byte[] key, byte[] data, boolean encrypt, boolean pad) {
        try {
            Cipher c = Cipher.getInstance(pad ? "AES/ECB/PKCS5Padding" : "AES/ECB/NoPadding");
            c.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
            return c.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException(e);
        }
    }

    static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(key, "HmacSHA256"));
            return m.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /** A frame we send: header, payload, HMAC of both with {@code key}, suffix. */
    static byte[] pack(int seq, int cmd, byte[] payload, byte[] key) {
        ByteBuffer b = ByteBuffer.allocate(16 + payload.length + 36);
        b.putInt(PREFIX).putInt(seq).putInt(cmd).putInt(payload.length + 36).put(payload);
        byte[] head = Arrays.copyOf(b.array(), 16 + payload.length);
        b.put(hmac(key, head)).putInt(SUFFIX);
        return b.array();
    }

    /**
     * A frame from the box (it always carries a return code). {@code key} null: a UDP announcement, checked with
     * CRC32 instead of an HMAC. Throws on a bad prefix, length or checksum.
     */
    static Frame unpack(byte[] data, byte[] key) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(data);
        if (data.length < 24 || b.getInt() != PREFIX) throw new IOException("not a Tuya frame");
        int seq = b.getInt(), cmd = b.getInt(), len = b.getInt();
        int trailer = key == null ? 8 : 36;
        if (len < 4 + trailer || 16 + len > data.length) throw new IOException("bad length " + len);
        int retcode = b.getInt();
        byte[] payload = Arrays.copyOfRange(data, 20, 16 + len - trailer);
        byte[] signed = Arrays.copyOf(data, 16 + len - trailer);
        if (key == null) {
            CRC32 crc = new CRC32();
            crc.update(signed);
            if ((int) crc.getValue() != ByteBuffer.wrap(data, 16 + len - 8, 4).getInt()) throw new IOException("bad crc");
        } else if (!MessageDigest.isEqual(hmac(key, signed), Arrays.copyOfRange(data, 16 + len - 36, 16 + len - 4))) {
            throw new IOException("bad hmac");
        }
        return new Frame(seq, cmd, retcode, payload);
    }

    /** The JSON a 3.4 box takes to set data points, without spaces (the box ignores it otherwise). */
    static String controlJson(String dp, String value, long seconds) {
        return "{\"protocol\":5,\"t\":" + seconds + ",\"data\":{\"dps\":{\"" + escape(dp) + "\":" + jsonValue(value) + "}}}";
    }

    /** true, false and numbers go as they are; anything else as a string. */
    static String jsonValue(String v) {
        if (v.equals("true") || v.equals("false") || v.matches("-?\\d{1,9}")) return v;
        return "\"" + escape(v) + "\"";
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** A control frame's payload: the "3.4" header and the JSON, encrypted with the session key. */
    static byte[] controlPayload(byte[] sessionKey, String json) {
        byte[] js = json.getBytes(StandardCharsets.UTF_8);
        byte[] plain = Arrays.copyOf(VERSION_HEADER, VERSION_HEADER.length + js.length);
        System.arraycopy(js, 0, plain, VERSION_HEADER.length, js.length);
        return aes(sessionKey, plain, true, true);
    }

    /** A reply's text: decrypted, with the "3.4" header taken off. */
    static String replyText(byte[] sessionKey, byte[] payload) {
        if (payload.length == 0) return "";
        byte[] plain = aes(sessionKey, payload, false, true);
        int from = plain.length >= 15 && plain[0] == '3' && plain[1] == '.' ? 15 : 0;
        return new String(plain, from, plain.length - from, StandardCharsets.UTF_8);
    }

    /** Step 2 checked (the box proves it knows the key), the session key worked out; null when the box is wrong. */
    static byte[] sessionKey(byte[] localKey, byte[] localNonce, byte[] step2Payload) {
        byte[] plain = aes(localKey, step2Payload, false, true);
        if (plain.length < 48) return null;
        byte[] remote = Arrays.copyOf(plain, 16);
        if (!MessageDigest.isEqual(hmac(localKey, localNonce), Arrays.copyOfRange(plain, 16, 48))) return null;
        byte[] x = new byte[16];
        for (int i = 0; i < 16; i++) x[i] = (byte) (localNonce[i] ^ remote[i]);
        return aes(localKey, x, true, false);
    }

    /** The box's remote nonce, from step 2's payload (for step 3). */
    static byte[] remoteNonce(byte[] localKey, byte[] step2Payload) {
        return Arrays.copyOf(aes(localKey, step2Payload, false, true), 16);
    }

    /** The JSON inside a UDP announcement (its "gwId" and "ip"), or null when the packet is something else. */
    static String announcement(byte[] packet) {
        try {
            Frame f = unpack(packet, null);
            byte[] p = f.payload;
            if (p.length > 0 && p[0] == '{') return new String(p, StandardCharsets.UTF_8);
            return new String(aes(UDP_KEY, p, false, true), StandardCharsets.UTF_8);
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Connects, negotiates a session and sets {@code dp} to {@code value}. Returns the box's reply, which names
     * the data point it changed. Throws when the box is unreachable, the key is wrong or it doesn't answer.
     */
    static String set(String host, String localKey, String dp, String value, int timeoutMs) throws IOException {
        byte[] key = localKey.getBytes(StandardCharsets.UTF_8);
        if (key.length != 16) throw new IOException("the local key must be 16 characters");
        byte[] nonce = new byte[16];
        new SecureRandom().nextBytes(nonce);
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, PORT), timeoutMs);
            s.setSoTimeout(timeoutMs);
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();
            out.write(pack(1, NEG_START, aes(key, nonce, true, true), key));
            Frame step2 = readUntil(in, key, NEG_RESP);
            byte[] session = sessionKey(key, nonce, step2.payload);
            if (session == null) throw new IOException("the box rejected the key");
            byte[] remote = remoteNonce(key, step2.payload);
            out.write(pack(2, NEG_FINISH, aes(key, hmac(key, remote), true, true), key));
            String json = controlJson(dp, value, System.currentTimeMillis() / 1000);
            out.write(pack(3, CONTROL_NEW, controlPayload(session, json), session));
            out.flush();
            // The box acks the command (empty), then reports the new value (cmd 8, STATUS).
            long end = System.currentTimeMillis() + timeoutMs;
            String reply = "";
            while (System.currentTimeMillis() < end) {
                Frame f = read(in, session);
                if (f.retcode != 0) throw new IOException("the box refused it (" + f.retcode + ")");
                reply = replyText(session, f.payload);
                if (reply.contains("\"" + dp + "\"")) return reply;
            }
            return reply;
        }
    }

    private static Frame readUntil(InputStream in, byte[] key, int cmd) throws IOException {
        for (int i = 0; i < 5; i++) {
            Frame f = read(in, key);
            if (f.cmd == cmd) return f;
        }
        throw new IOException("no answer to the session request");
    }

    private static Frame read(InputStream in, byte[] key) throws IOException {
        DataInputStream d = new DataInputStream(in);
        byte[] head = new byte[16];
        d.readFully(head);
        int len = ByteBuffer.wrap(head, 12, 4).getInt();
        if (len < 0 || len > 65536) throw new IOException("bad length " + len);
        byte[] rest = new byte[len];
        d.readFully(rest);
        ByteArrayOutputStream all = new ByteArrayOutputStream(16 + len);
        all.write(head);
        all.write(rest);
        return unpack(all.toByteArray(), key);
    }
}
