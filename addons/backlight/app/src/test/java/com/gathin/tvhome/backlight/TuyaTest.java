package com.gathin.tvhome.backlight;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.json.JSONObject;
import org.junit.Test;

/** The frames below were made by tinytuya 1.20 with a made-up key and fixed nonces, so these match it byte for byte. */
public class TuyaTest {
    private static final byte[] KEY = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] NONCE = range(0, 16), REMOTE = range(100, 16);
    private static final String STEP1 = "000055aa000000010000000300000044a07999f0e2bfbe16f99593e984a449b7377222e061a924c591cd9c27ea163ed484a896dcb29647fa6e5c5024c9533704640e90001c9f4d52f8b4480cc12648340000aa55";
    private static final String STEP2 = "000055aa00000001000000040000006800000000938f3d8e1d412d55ebe9bf61ea1b0ea6133d7ef10fca101a5416af7bf5abf5cc204370fd8642db07ea0c5c880c5bce11377222e061a924c591cd9c27ea163ed41101aac35f79a262faab4ebbb4b7fc1087cce7c0b67323c8c7915291cd607d820000aa55";
    private static final String STEP3 = "000055aa0000000200000005000000541b3940106e88c2074dd243e0928a320b71cc6856ddabdc15ee092a5cfa1ea535377222e061a924c591cd9c27ea163ed4da3bfb3105635a9f49c4fad3899e5fe5e9a4b8c7be115d9cb7299a92982bcaaa0000aa55";
    private static final String SESSION = "66c0d72e3ec70800f70b18898e76c684";
    private static final String CONTROL = "000055aa000000030000000d00000094e83c44d2b11a6f3c76eb5859bc3390fbd78326e53d680c0218d3ac3dee846cccaa9107ec337ba666a06cb6dd2be9e168cc19d59298a1ac0df585ce8696907ffe467bae36b6fb1bb6cb9d024ceba63258d58ebf26ad8b67e505bf17a26ac7c431823a69d585ad43b209b36e715e548d76c5266144579d8bbe2237b68e0d6db0d500bb86994e7e38869984865bc5dd16840000aa55";
    private static final String REPLY = "000055aa00000007000000080000009800000000e83c44d2b11a6f3c76eb5859bc3390fbd46be8427160458babd628d396280bb2747f100b948b5a7b50994fa57d730ca4cc19d59298a1ac0df585ce8696907ffe467bae36b6fb1bb6cb9d024ceba63258d58ebf26ad8b67e505bf17a26ac7c431823a69d585ad43b209b36e715e548d76742f834077533fe84a73ceef4f1f6cdd6df937ebd7c9fbd12f8c4a1e9370c17c0000aa55";
    private static final String UDP = "000055aa00000000000000130000009c00000000d09766676f3369eb10b5e9f132fd802a70172896956a9e330910344d11ac363f839e7217afd55150d43986a99019033b500483bb1b34d6c8ff9c0c0b922c73d194a2a5c0faadb57de544bfb8d43c63c3c0ba520148045e7605fa0498dfea5aab15ead773a4a8c86468c2ec2f57540f1a7d0808899a93fafccbfd1bc4d3933dd29606e5cbf1729aa73023966399b044b82bf3c9530000aa55";
    private static final String SYNC = "82000000000000000000000000000000";

    @Test
    public void theSessionRequestMatchesTinytuya() {
        assertEquals(STEP1, hex(Tuya.pack(1, Tuya.NEG_START, Tuya.aes(KEY, NONCE, true, true), KEY)));
    }

    @Test
    public void theBoxsAnswerGivesTheSessionKeyAndStepThree() throws IOException {
        Tuya.Frame f = Tuya.unpack(bytes(STEP2), KEY);
        assertEquals(Tuya.NEG_RESP, f.cmd);
        assertEquals(SESSION, hex(Tuya.sessionKey(KEY, NONCE, f.payload)));
        byte[] remote = Tuya.remoteNonce(KEY, f.payload);
        assertArrayEquals(REMOTE, remote);
        assertEquals(STEP3, hex(Tuya.pack(2, Tuya.NEG_FINISH, Tuya.aes(KEY, Tuya.hmac(KEY, remote), true, true), KEY)));
    }

    @Test
    public void aBoxWithoutTheKeyIsRefused() throws IOException {
        byte[] other = "fedcba9876543210".getBytes(StandardCharsets.US_ASCII);
        Tuya.Frame f = Tuya.unpack(bytes(STEP2), KEY);
        byte[] wrongNonce = range(1, 16);
        assertNull(Tuya.sessionKey(KEY, wrongNonce, f.payload));
        try {
            Tuya.unpack(bytes(STEP2), other);
            throw new AssertionError("a frame signed with another key passed");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("hmac"));
        }
    }

    @Test
    public void theControlFrameMatchesTinytuya() {
        String json = Tuya.controlJson("25", SYNC, 1700000000L);
        assertEquals("{\"protocol\":5,\"t\":1700000000,\"data\":{\"dps\":{\"25\":\"" + SYNC + "\"}}}", json);
        assertEquals(CONTROL, hex(Tuya.pack(3, Tuya.CONTROL_NEW, Tuya.controlPayload(bytes(SESSION), json), bytes(SESSION))));
    }

    @Test
    public void theBoxsReplyIsReadBack() throws Exception {
        Tuya.Frame f = Tuya.unpack(bytes(REPLY), bytes(SESSION));
        assertEquals(0, f.retcode);
        JSONObject reply = new JSONObject(Tuya.replyText(bytes(SESSION), f.payload));
        assertEquals(SYNC, reply.getJSONObject("data").getJSONObject("dps").getString("25"));
    }

    @Test
    public void theAnnouncementNamesTheBoxAndItsAddress() throws Exception {
        JSONObject a = new JSONObject(Tuya.announcement(bytes(UDP)));
        assertEquals("bf0123456789abcdefxyzq", a.getString("gwId"));
        assertEquals("192.168.1.50", a.getString("ip"));
        byte[] broken = bytes(UDP);
        broken[40] ^= 1;
        assertNull(Tuya.announcement(broken));
    }

    @Test
    public void valuesKeepTheirJsonType() {
        assertEquals("true", Tuya.jsonValue("true"));
        assertEquals("1000", Tuya.jsonValue("1000"));
        assertEquals("\"scene\"", Tuya.jsonValue("scene"));
        assertEquals("\"a\\\"b\"", Tuya.jsonValue("a\"b"));
    }

    private static byte[] range(int from, int n) {
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) b[i] = (byte) (from + i);
        return b;
    }

    private static byte[] bytes(String hex) {
        byte[] b = new byte[hex.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        return b;
    }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02x", x));
        return s.toString();
    }
}
