package io.freemytv;

import java.nio.charset.StandardCharsets;

/**
 * A small QR code encoder (byte mode, error correction M, versions 1 to 6: up to 106 bytes, plenty for a web address),
 * for PhoneUpload's screen: a phone's camera opens the address. Follows the QR standard (ISO/IEC 18004) the way
 * Project Nayuki's MIT-licensed generator lays it out.
 */
final class Qr {
    private Qr() {}

    // Error correction M, by version (index 0 unused).
    private static final int[] ECC_PER_BLOCK = {-1, 10, 16, 26, 18, 24, 16};
    private static final int[] BLOCKS = {-1, 1, 1, 1, 2, 2, 4};
    private static final int FORMAT_M = 0;   // the format bits of level M

    /** The modules, [y][x], true = dark; null when the text doesn't fit in version 6. */
    static boolean[][] encode(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        for (int ver = 1; ver <= 6; ver++) {
            int capacity = dataCodewords(ver) * 8;
            int needed = 4 + 8 + bytes.length * 8;
            if (needed <= capacity) return build(ver, bytes);
        }
        return null;
    }

    private static boolean[][] build(int ver, byte[] bytes) {
        int capacityBits = dataCodewords(ver) * 8;
        Bits bits = new Bits(capacityBits);
        bits.add(0x4, 4);                    // byte mode
        bits.add(bytes.length, 8);           // character count (8 bits up to version 9)
        for (byte b : bytes) bits.add(b & 0xFF, 8);
        bits.add(0, Math.min(4, capacityBits - bits.size));   // terminator
        bits.add(0, (8 - bits.size % 8) % 8);
        for (int pad = 0xEC; bits.size < capacityBits; pad ^= 0xEC ^ 0x11) bits.add(pad, 8);
        byte[] data = new byte[capacityBits / 8];
        for (int i = 0; i < bits.size; i++) if (bits.get(i)) data[i >>> 3] |= (byte) (1 << (7 - (i & 7)));

        int size = ver * 4 + 17;
        boolean[][] modules = new boolean[size][size];
        boolean[][] function = new boolean[size][size];
        drawFunctionPatterns(ver, size, modules, function);
        drawCodewords(size, interleave(ver, data), modules, function);

        int bestMask = 0;
        long bestPenalty = Long.MAX_VALUE;
        for (int mask = 0; mask < 8; mask++) {
            applyMask(mask, size, modules, function);
            drawFormat(mask, size, modules, function);
            long p = penalty(size, modules);
            if (p < bestPenalty) {
                bestPenalty = p;
                bestMask = mask;
            }
            applyMask(mask, size, modules, function);   // XOR again: undone
        }
        applyMask(bestMask, size, modules, function);
        drawFormat(bestMask, size, modules, function);
        return modules;
    }

    // MARK: - Sizes

    private static int rawDataModules(int ver) {
        int result = (16 * ver + 128) * ver + 64;
        if (ver >= 2) {
            int align = ver / 7 + 2;
            result -= (25 * align - 10) * align - 55;
            if (ver >= 7) result -= 36;
        }
        return result;
    }

    private static int dataCodewords(int ver) {
        return rawDataModules(ver) / 8 - ECC_PER_BLOCK[ver] * BLOCKS[ver];
    }

    // MARK: - Function patterns

    private static void set(boolean[][] m, boolean[][] f, int x, int y, boolean dark) {
        m[y][x] = dark;
        f[y][x] = true;
    }

    private static void drawFunctionPatterns(int ver, int size, boolean[][] m, boolean[][] f) {
        for (int i = 0; i < size; i++) {
            set(m, f, 6, i, i % 2 == 0);
            set(m, f, i, 6, i % 2 == 0);
        }
        finder(3, 3, size, m, f);
        finder(size - 4, 3, size, m, f);
        finder(3, size - 4, size, m, f);
        int[] pos = alignmentPositions(ver, size);
        int n = pos.length;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if ((i == 0 && j == 0) || (i == 0 && j == n - 1) || (i == n - 1 && j == 0)) continue;
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        set(m, f, pos[i] + dx, pos[j] + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
                    }
                }
            }
        }
        drawFormat(0, size, m, f);   // reserves the format areas; drawn for real after the mask is chosen
    }

    private static void finder(int cx, int cy, int size, boolean[][] m, boolean[][] f) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int x = cx + dx, y = cy + dy;
                if (x < 0 || x >= size || y < 0 || y >= size) continue;
                int dist = Math.max(Math.abs(dx), Math.abs(dy));
                set(m, f, x, y, dist != 2 && dist != 4);
            }
        }
    }

    private static int[] alignmentPositions(int ver, int size) {
        if (ver == 1) return new int[0];
        int n = ver / 7 + 2;
        int step = (ver * 4 + n * 2 + 1) / (n * 2 - 2) * 2;
        int[] out = new int[n];
        out[0] = 6;
        for (int i = n - 1, pos = size - 7; i >= 1; i--, pos -= step) out[i] = pos;
        return out;
    }

    private static void drawFormat(int mask, int size, boolean[][] m, boolean[][] f) {
        int data = FORMAT_M << 3 | mask;
        int rem = data;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        int bits = (data << 10 | rem) ^ 0x5412;
        for (int i = 0; i <= 5; i++) set(m, f, 8, i, bit(bits, i));
        set(m, f, 8, 7, bit(bits, 6));
        set(m, f, 8, 8, bit(bits, 7));
        set(m, f, 7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) set(m, f, 14 - i, 8, bit(bits, i));
        for (int i = 0; i < 8; i++) set(m, f, size - 1 - i, 8, bit(bits, i));
        for (int i = 8; i < 15; i++) set(m, f, 8, size - 15 + i, bit(bits, i));
        set(m, f, 8, size - 8, true);   // the dark module
    }

    private static boolean bit(int x, int i) {
        return ((x >>> i) & 1) != 0;
    }

    // MARK: - Data

    /** Splits the data into blocks, adds each block's Reed-Solomon codewords and interleaves them. */
    private static byte[] interleave(int ver, byte[] data) {
        int blocks = BLOCKS[ver], eccLen = ECC_PER_BLOCK[ver];
        int raw = rawDataModules(ver) / 8;
        int shortBlocks = blocks - raw % blocks, shortLen = raw / blocks;
        byte[][] out = new byte[blocks][];
        byte[] divisor = rsDivisor(eccLen);
        for (int i = 0, k = 0; i < blocks; i++) {
            int datLen = shortLen - eccLen + (i < shortBlocks ? 0 : 1);
            byte[] dat = new byte[datLen];
            System.arraycopy(data, k, dat, 0, datLen);
            k += datLen;
            byte[] ecc = rsRemainder(dat, divisor);
            byte[] block = new byte[shortLen + 1];
            System.arraycopy(dat, 0, block, 0, datLen);
            System.arraycopy(ecc, 0, block, block.length - eccLen, eccLen);
            out[i] = block;
        }
        byte[] result = new byte[raw];
        int n = 0;
        for (int i = 0; i < out[0].length; i++) {
            for (int j = 0; j < blocks; j++) {
                if (i != shortLen - eccLen || j >= shortBlocks) result[n++] = out[j][i];
            }
        }
        return result;
    }

    private static byte[] rsDivisor(int degree) {
        byte[] result = new byte[degree];
        result[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < degree; j++) {
                result[j] = (byte) gfMul(result[j] & 0xFF, root);
                if (j + 1 < degree) result[j] ^= result[j + 1];
            }
            root = gfMul(root, 0x02);
        }
        return result;
    }

    private static byte[] rsRemainder(byte[] data, byte[] divisor) {
        byte[] result = new byte[divisor.length];
        for (byte b : data) {
            int factor = (b ^ result[0]) & 0xFF;
            System.arraycopy(result, 1, result, 0, result.length - 1);
            result[result.length - 1] = 0;
            for (int i = 0; i < result.length; i++) result[i] ^= (byte) gfMul(divisor[i] & 0xFF, factor);
        }
        return result;
    }

    private static int gfMul(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z & 0xFF;
    }

    /** Zigzags the codewords through the modules that aren't function patterns. */
    private static void drawCodewords(int size, byte[] data, boolean[][] m, boolean[][] f) {
        int i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;
                    if (!f[y][x] && i < data.length * 8) {
                        m[y][x] = ((data[i >>> 3] >>> (7 - (i & 7))) & 1) != 0;
                        i++;
                    }
                }
            }
        }
    }

    private static void applyMask(int mask, int size, boolean[][] m, boolean[][] f) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (f[y][x]) continue;
                boolean invert;
                switch (mask) {
                    case 0: invert = (x + y) % 2 == 0; break;
                    case 1: invert = y % 2 == 0; break;
                    case 2: invert = x % 3 == 0; break;
                    case 3: invert = (x + y) % 3 == 0; break;
                    case 4: invert = (x / 3 + y / 2) % 2 == 0; break;
                    case 5: invert = x * y % 2 + x * y % 3 == 0; break;
                    case 6: invert = (x * y % 2 + x * y % 3) % 2 == 0; break;
                    default: invert = ((x + y) % 2 + x * y % 3) % 2 == 0; break;
                }
                if (invert) m[y][x] = !m[y][x];
            }
        }
    }

    /** The standard's four penalty rules: the mask with the lowest is the easiest to read. */
    private static long penalty(int size, boolean[][] m) {
        long p = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (int a = 0; a < size; a++) {
                int run = 1;
                for (int b = 1; b < size; b++) {
                    boolean cur = pass == 0 ? m[a][b] : m[b][a], prev = pass == 0 ? m[a][b - 1] : m[b - 1][a];
                    if (cur == prev) {
                        run++;
                        if (run == 5) p += 3;
                        else if (run > 5) p++;
                    } else {
                        run = 1;
                    }
                }
                for (int b = 0; b + 11 <= size; b++) {
                    if (finderLike(m, a, b, pass == 1, false) || finderLike(m, a, b, pass == 1, true)) p += 40;
                }
            }
        }
        int dark = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (m[y][x]) dark++;
                if (x + 1 < size && y + 1 < size && m[y][x] == m[y][x + 1] && m[y][x] == m[y + 1][x]
                        && m[y][x] == m[y + 1][x + 1]) p += 3;
            }
        }
        int total = size * size;
        int k = (Math.abs(dark * 20 - total * 10) + total - 1) / total - 1;
        return p + Math.max(0, k) * 10L;
    }

    private static final boolean[] FINDER_RUN = {true, false, true, true, true, false, true, false, false, false, false};

    private static boolean finderLike(boolean[][] m, int a, int b, boolean column, boolean reversed) {
        for (int i = 0; i < 11; i++) {
            boolean want = FINDER_RUN[reversed ? 10 - i : i];
            boolean got = column ? m[b + i][a] : m[a][b + i];
            if (got != want) return false;
        }
        return true;
    }

    private static final class Bits {
        final boolean[] v;
        int size;

        Bits(int capacity) {
            v = new boolean[capacity];
        }

        void add(int value, int count) {
            for (int i = count - 1; i >= 0; i--) v[size++] = ((value >>> i) & 1) != 0;
        }

        boolean get(int i) {
            return v[i];
        }
    }
}
