import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Acctmtch {
    static boolean isEbcdic;
    static final byte[] EBCMAP = new byte[256];
    static {
        Charset cs = null;
        try { cs = Charset.forName("Cp037"); } catch (Exception e) { cs = null; }
        for (int i = 0; i < 256; i++) {
            if (i == 0xFF) { EBCMAP[i] = (byte) 0xFF; continue; }
            if (cs != null) {
                try {
                    byte[] b = String.valueOf((char) i).getBytes(cs);
                    if (b.length == 1) { EBCMAP[i] = b[0]; continue; }
                } catch (Exception e) { }
            }
            EBCMAP[i] = (byte) i;
        }
        String c = System.getenv("DIOCLE_COLLATION");
        isEbcdic = c != null && c.equalsIgnoreCase("EBCDIC");
    }

    private List<byte[]> mstLines;
    private List<byte[]> txnLines;
    private int mstPos;
    private int txnPos;
    private byte[] msRec = new byte[24];
    private byte[] txRec = new byte[19];
    private byte[] nmRec = new byte[28];
    private byte[] exRec = new byte[30];
    private byte[] wsMstKey = new byte[8];
    private byte[] wsTxnKey = new byte[8];
    private BigDecimal wsBalance = new BigDecimal(BigInteger.ZERO, 2);
    private int wsCount = 0;
    private int wsExcCnt = 0;
    private int wsNewCnt = 0;
    private OutputStream newOut;
    private OutputStream excOut;

    public static void main(String[] args) throws Exception {
        Acctmtch p = new Acctmtch();
        p.p0000Main();
    }

    private void p0000Main() throws Exception {
        String c = System.getenv("DIOCLE_COLLATION");
        isEbcdic = c != null && c.equalsIgnoreCase("EBCDIC");
        String mstPath = System.getenv("DD_MSTIN");
        String txnPath = System.getenv("DD_TXNIN");
        String newPath = System.getenv("DD_NEWMST");
        String excPath = System.getenv("DD_EXCOUT");
        mstLines = readLineSeq(mstPath);
        txnLines = readLineSeq(txnPath);
        mstPos = 0;
        txnPos = 0;
        newOut = Files.newOutputStream(Paths.get(newPath), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        excOut = Files.newOutputStream(Paths.get(excPath), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        Arrays.fill(msRec, (byte) 0x20);
        Arrays.fill(txRec, (byte) 0x20);
        p8100ReadMst();
        p8200ReadTxn();
        while (!(isHigh(wsMstKey) && isHigh(wsTxnKey))) {
            p2000Match();
        }
        newOut.flush();
        newOut.close();
        excOut.flush();
        excOut.close();
        String out = "ACCTMTCH NEW=" + format7(wsNewCnt) + " EXC=" + format7(wsExcCnt);
        System.out.print(out + "\n");
        System.out.flush();
        if (wsExcCnt > 0) System.exit(4);
    }

    private void p2000Match() throws Exception {
        int cmp = compareKeys(wsTxnKey, wsMstKey);
        if (cmp < 0) {
            System.arraycopy(txRec, 0, exRec, 0, 8);
            System.arraycopy(txRec, 8, exRec, 8, 10);
            byte[] r = "NO ACCOUNT  ".getBytes(StandardCharsets.ISO_8859_1);
            System.arraycopy(r, 0, exRec, 18, 12);
            p7000WriteExc();
            p8200ReadTxn();
        } else if (cmp == 0) {
            p3000Apply();
            p8200ReadTxn();
        } else {
            p4000WriteMaster();
            p8100ReadMst();
        }
    }

    private void p3000Apply() throws Exception {
        boolean frozen = msRec[20] == (byte) 'F' && msRec[21] == (byte) 'R' && msRec[22] == (byte) 'Z' && msRec[23] == (byte) 'N';
        if (frozen) {
            System.arraycopy(txRec, 0, exRec, 0, 8);
            System.arraycopy(txRec, 8, exRec, 8, 10);
            byte[] r = "FROZEN      ".getBytes(StandardCharsets.ISO_8859_1);
            System.arraycopy(r, 0, exRec, 18, 12);
            p7000WriteExc();
        } else {
            byte t = txRec[18];
            if (t == (byte) 'C') {
                BigDecimal amt = parseLeadSep(txRec, 8, 9, 2);
                wsBalance = truncBal(wsBalance.add(amt));
                wsCount = (wsCount + 1) % 10000;
            } else if (t == (byte) 'D') {
                BigDecimal amt = parseLeadSep(txRec, 8, 9, 2);
                wsBalance = truncBal(wsBalance.subtract(amt));
                wsCount = (wsCount + 1) % 10000;
            } else {
                System.arraycopy(txRec, 0, exRec, 0, 8);
                System.arraycopy(txRec, 8, exRec, 8, 10);
                byte[] r = "BAD TYPE    ".getBytes(StandardCharsets.ISO_8859_1);
                System.arraycopy(r, 0, exRec, 18, 12);
                p7000WriteExc();
            }
        }
    }

    private void p4000WriteMaster() throws Exception {
        Arrays.fill(nmRec, (byte) 0x20);
        System.arraycopy(msRec, 0, nmRec, 0, 8);
        formatLeadSep(wsBalance, nmRec, 8, 11, 2);
        System.arraycopy(msRec, 20, nmRec, 20, 4);
        String s = formatN(wsCount, 4);
        byte[] b = s.getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(b, 0, nmRec, 24, 4);
        newOut.write(nmRec);
        newOut.write('\n');
        wsNewCnt = (wsNewCnt + 1) % 10000000;
    }

    private void p7000WriteExc() throws Exception {
        excOut.write(exRec);
        excOut.write('\n');
        wsExcCnt = (wsExcCnt + 1) % 10000000;
    }

    private void p8100ReadMst() {
        if (mstPos < mstLines.size()) {
            byte[] line = mstLines.get(mstPos++);
            Arrays.fill(msRec, (byte) 0x20);
            int n = Math.min(line.length, msRec.length);
            System.arraycopy(line, 0, msRec, 0, n);
            System.arraycopy(msRec, 0, wsMstKey, 0, 8);
            wsBalance = truncBal(parseLeadSep(msRec, 8, 11, 2));
            wsCount = 0;
        } else {
            Arrays.fill(wsMstKey, (byte) 0xFF);
        }
    }

    private void p8200ReadTxn() {
        if (txnPos < txnLines.size()) {
            byte[] line = txnLines.get(txnPos++);
            Arrays.fill(txRec, (byte) 0x20);
            int n = Math.min(line.length, txRec.length);
            System.arraycopy(line, 0, txRec, 0, n);
            System.arraycopy(txRec, 0, wsTxnKey, 0, 8);
        } else {
            Arrays.fill(wsTxnKey, (byte) 0xFF);
        }
    }

    static int compareKeys(byte[] a, byte[] b) {
        for (int i = 0; i < 8; i++) {
            int x = a[i] & 0xFF;
            int y = b[i] & 0xFF;
            int ex;
            int ey;
            if (isEbcdic) {
                ex = EBCMAP[x] & 0xFF;
                ey = EBCMAP[y] & 0xFF;
            } else {
                ex = x;
                ey = y;
            }
            if (ex != ey) return ex - ey;
        }
        return 0;
    }

    static boolean isHigh(byte[] k) {
        for (int i = 0; i < k.length; i++) if ((k[i] & 0xFF) != 0xFF) return false;
        return true;
    }

    static BigDecimal parseLeadSep(byte[] rec, int off, int totalDigits, int fracDigits) {
        int sign = 1;
        if (rec[off] == (byte) '-') sign = -1;
        StringBuilder d = new StringBuilder();
        for (int i = 1; i <= totalDigits; i++) {
            char ch = (char) (rec[off + i] & 0xFF);
            if (ch >= '0' && ch <= '9') d.append(ch);
            else d.append('0');
        }
        BigInteger mag;
        if (d.length() == 0) mag = BigInteger.ZERO;
        else mag = new BigInteger(d.toString());
        if (mag.signum() == 0) return new BigDecimal(BigInteger.ZERO, fracDigits);
        if (sign < 0) mag = mag.negate();
        return new BigDecimal(mag, fracDigits);
    }

    static void formatLeadSep(BigDecimal v, byte[] rec, int off, int totalDigits, int fracDigits) {
        int sign = v.signum() < 0 ? -1 : 1;
        BigDecimal av = v.abs();
        BigInteger unscaled;
        try {
            unscaled = av.movePointRight(fracDigits).toBigIntegerExact();
        } catch (ArithmeticException e) {
            unscaled = av.movePointRight(fracDigits).toBigInteger();
        }
        String s = unscaled.toString();
        if (s.length() > totalDigits) s = s.substring(s.length() - totalDigits);
        else if (s.length() < totalDigits) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < totalDigits - s.length(); i++) sb.append('0');
            sb.append(s);
            s = sb.toString();
        }
        rec[off] = (byte) (sign < 0 ? '-' : '+');
        for (int i = 0; i < totalDigits; i++) rec[off + 1 + i] = (byte) s.charAt(i);
    }

    static BigDecimal truncBal(BigDecimal v) {
        BigInteger unscaled;
        try {
            unscaled = v.movePointRight(2).toBigIntegerExact();
        } catch (ArithmeticException e) {
            unscaled = v.movePointRight(2).toBigInteger();
        }
        if (unscaled.signum() == 0) return new BigDecimal(BigInteger.ZERO, 2);
        BigInteger mag = unscaled.abs().mod(BigInteger.TEN.pow(11));
        if (mag.signum() == 0) return new BigDecimal(BigInteger.ZERO, 2);
        if (unscaled.signum() < 0) mag = mag.negate();
        return new BigDecimal(mag, 2);
    }

    static String format7(int v) {
        int m = v % 10000000;
        if (m < 0) m += 10000000;
        String s = Integer.toString(m);
        while (s.length() < 7) s = "0" + s;
        return s;
    }

    static String formatN(int v, int w) {
        int mod = 1;
        for (int i = 0; i < w; i++) mod *= 10;
        int m = v % mod;
        if (m < 0) m += mod;
        String s = Integer.toString(m);
        while (s.length() < w) s = "0" + s;
        if (s.length() > w) s = s.substring(s.length() - w);
        return s;
    }

    static List<byte[]> readLineSeq(String path) throws Exception {
        byte[] data = Files.readAllBytes(Paths.get(path));
        List<byte[]> out = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == (byte) 0x0A) {
                int end = i;
                if (end > start && data[end - 1] == (byte) 0x0D) end--;
                out.add(Arrays.copyOfRange(data, start, end));
                start = i + 1;
            }
        }
        if (start < data.length) {
            int end = data.length;
            if (end > start && data[end - 1] == (byte) 0x0D) end--;
            out.add(Arrays.copyOfRange(data, start, end));
        }
        return out;
    }
}
