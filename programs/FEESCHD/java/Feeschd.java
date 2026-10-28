import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class Feeschd {
    private BufferedReader feeReader;
    private BufferedWriter chgWriter;
    private boolean endOfAccounts = false;
    private String feeAcctId = "          ";
    private char feeProduct = ' ';
    private long feeAvgBalCents = 0L;
    private int feeTxnCount = 0;
    private int feeMonthDays = 0;
    private long wsBaseCents = 0L;
    private int wsFreeTxns = 0;
    private long wsExcess = 0L;
    private long wsTxnFeeCents = 0L;
    private long wsOdIntCents = 0L;
    private long wsTotalCents = 0L;
    private String wsCode = "OK";
    private long wsReadCnt = 0L;
    private long wsCappedCnt = 0L;
    private static final BigDecimal WS_PER_TXN = new BigDecimal("0.35");
    private static final BigDecimal WS_OD_RATE = new BigDecimal("0.1899");
    private static final long WS_WAIVER_CENTS = 500000L;
    private static final long WS_CAP_CENTS = 4000L;

    public static void main(String[] args) throws Exception {
        Feeschd p = new Feeschd();
        p.p0000Main();
    }

    private void p0000Main() throws Exception {
        p1000Init();
        while (!endOfAccounts) {
            p2000Process();
        }
        p9000Term();
    }

    private void p1000Init() throws Exception {
        String feePath = System.getenv("DD_FEEIN");
        String chgPath = System.getenv("DD_CHGOUT");
        try {
            if (feePath == null) {
                throw new java.io.FileNotFoundException();
            }
            feeReader = Files.newBufferedReader(Paths.get(feePath), StandardCharsets.ISO_8859_1);
        } catch (Exception e) {
            System.out.println("FEESCHD OPEN FEEIN FAILED 35");
            System.out.flush();
            System.exit(16);
            return;
        }
        try {
            if (chgPath == null) {
                throw new java.io.FileNotFoundException();
            }
            chgWriter = Files.newBufferedWriter(Paths.get(chgPath), StandardCharsets.ISO_8859_1,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (Exception e) {
            System.out.println("FEESCHD OPEN CHGOUT FAILED 35");
            System.out.flush();
            try { feeReader.close(); } catch (Exception x) { }
            System.exit(16);
            return;
        }
        p8000Read();
    }

    private void p2000Process() throws Exception {
        wsReadCnt = (wsReadCnt + 1L) % 10000000L;
        wsCode = "OK";
        p3000BaseFee();
        p4000TxnFee();
        p5000OdInterest();
        p6000Total();
        p7000WriteResult();
        p8000Read();
    }

    private void p3000BaseFee() {
        if (feeProduct == 'A') {
            wsBaseCents = 600L;
            wsFreeTxns = 10;
        } else if (feeProduct == 'B') {
            wsBaseCents = 350L;
            wsFreeTxns = 25;
        } else {
            wsBaseCents = 0L;
            wsFreeTxns = 9999;
        }
        if (feeAvgBalCents >= WS_WAIVER_CENTS && wsBaseCents > 0L) {
            wsBaseCents = 0L;
            wsCode = "WV";
        }
    }

    private void p4000TxnFee() {
        long raw = (long) feeTxnCount - (long) wsFreeTxns;
        BigInteger bi = BigInteger.valueOf(raw);
        BigInteger mod = BigInteger.TEN.pow(5);
        wsExcess = bi.remainder(mod).longValue();
        if (wsExcess > 0L) {
            BigDecimal e = BigDecimal.valueOf(wsExcess);
            BigDecimal v = e.multiply(WS_PER_TXN);
            wsTxnFeeCents = truncToCents(v, 3, 2);
        } else {
            wsTxnFeeCents = 0L;
        }
    }

    private void p5000OdInterest() {
        if (feeAvgBalCents < 0L) {
            BigDecimal avg = BigDecimal.valueOf(feeAvgBalCents, 2);
            BigDecimal days = BigDecimal.valueOf((long) feeMonthDays);
            BigDecimal base = avg.negate().multiply(WS_OD_RATE).multiply(days);
            BigDecimal v = base.divide(new BigDecimal("365"), 30, RoundingMode.HALF_UP);
            wsOdIntCents = roundToCents(v, 5, 2);
        } else {
            wsOdIntCents = 0L;
        }
    }

    private void p6000Total() {
        BigDecimal b = BigDecimal.valueOf(wsBaseCents, 2);
        BigDecimal t = BigDecimal.valueOf(wsTxnFeeCents, 2);
        BigDecimal o = BigDecimal.valueOf(wsOdIntCents, 2);
        BigDecimal sum = b.add(t).add(o);
        wsTotalCents = truncToCents(sum, 3, 2);
        boolean isPrivate = (feeProduct == 'C');
        if (!isPrivate && wsTotalCents > WS_CAP_CENTS) {
            wsTotalCents = WS_CAP_CENTS;
            wsCode = "CP";
            wsCappedCnt = (wsCappedCnt + 1L) % 10000000L;
        }
    }

    private void p7000WriteResult() throws Exception {
        String rec = feeAcctId
                + formatSigned(wsBaseCents, 3, 2)
                + formatSigned(wsTxnFeeCents, 3, 2)
                + formatSigned(wsOdIntCents, 5, 2)
                + formatSigned(wsTotalCents, 3, 2)
                + wsCode;
        chgWriter.write(rec);
        chgWriter.write("\n");
    }

    private void p8000Read() throws Exception {
        String line;
        try {
            line = feeReader.readLine();
        } catch (Exception e) {
            line = null;
        }
        if (line == null) {
            endOfAccounts = true;
            return;
        }
        if (line.length() < 29) {
            StringBuilder sb = new StringBuilder(line);
            while (sb.length() < 29) {
                sb.append(' ');
            }
            line = sb.toString();
        } else if (line.length() > 29) {
            line = line.substring(0, 29);
        }
        feeAcctId = line.substring(0, 10);
        feeProduct = line.charAt(10);
        String avgStr = line.substring(11, 23);
        String txnStr = line.substring(23, 27);
        String daysStr = line.substring(27, 29);
        feeAvgBalCents = parseSignedCents(avgStr);
        feeTxnCount = parseUnsigned(txnStr);
        feeMonthDays = parseUnsigned(daysStr);
    }

    private void p9000Term() throws Exception {
        try { if (feeReader != null) feeReader.close(); } catch (Exception e) { }
        try { if (chgWriter != null) chgWriter.close(); } catch (Exception e) { }
        System.out.println("FEESCHD ACCOUNTS=" + formatCounter(wsReadCnt) + " CAPPED=" + formatCounter(wsCappedCnt));
    }

    private static long truncToCents(BigDecimal v, int intDigits, int fracDigits) {
        BigDecimal s = v.setScale(fracDigits, RoundingMode.DOWN);
        BigInteger cents = s.movePointRight(fracDigits).toBigIntegerExact();
        BigInteger mod = BigInteger.TEN.pow(intDigits + fracDigits);
        BigInteger r = cents.remainder(mod);
        return r.longValue();
    }

    private static long roundToCents(BigDecimal v, int intDigits, int fracDigits) {
        BigDecimal s = v.setScale(fracDigits, RoundingMode.HALF_UP);
        BigInteger cents = s.movePointRight(fracDigits).toBigIntegerExact();
        BigInteger mod = BigInteger.TEN.pow(intDigits + fracDigits);
        BigInteger r = cents.remainder(mod);
        return r.longValue();
    }

    private static String formatSigned(long cents, int intDigits, int fracDigits) {
        char sign = cents < 0L ? '-' : '+';
        long abs = cents < 0L ? -cents : cents;
        int total = intDigits + fracDigits;
        String d = Long.toString(abs);
        if (d.length() > total) {
            d = d.substring(d.length() - total);
        } else {
            while (d.length() < total) {
                d = "0" + d;
            }
        }
        return sign + d;
    }

    private static String formatCounter(long v) {
        long m = v % 10000000L;
        if (m < 0L) {
            m += 10000000L;
        }
        String d = Long.toString(m);
        while (d.length() < 10) {
            d = "0" + d;
        }
        return d;
    }

    private static long parseSignedCents(String f) {
        if (f == null) {
            return 0L;
        }
        boolean allSpace = true;
        for (int i = 0; i < f.length(); i++) {
            if (f.charAt(i) != ' ') {
                allSpace = false;
                break;
            }
        }
        if (allSpace) {
            return 0L;
        }
        char c0 = f.charAt(0);
        int sign = 1;
        String digits;
        if (c0 == '+') {
            sign = 1;
            digits = f.substring(1);
        } else if (c0 == '-') {
            sign = -1;
            digits = f.substring(1);
        } else if (c0 == ' ') {
            sign = 1;
            digits = f.substring(1);
        } else {
            sign = 1;
            digits = f;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            } else if (c == ' ') {
                sb.append('0');
            } else {
                sb.append('0');
            }
        }
        if (sb.length() == 0) {
            return 0L;
        }
        long mag;
        try {
            mag = Long.parseLong(sb.toString());
        } catch (Exception e) {
            BigInteger big = new BigInteger(sb.toString());
            BigInteger mod = BigInteger.TEN.pow(11);
            mag = big.remainder(mod).longValue();
        }
        return sign < 0 ? -mag : mag;
    }

    private static int parseUnsigned(String f) {
        if (f == null) {
            return 0;
        }
        String t = f.trim();
        if (t.length() == 0) {
            return 0;
        }
        try {
            return Integer.parseInt(t);
        } catch (Exception e) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < f.length(); i++) {
                char c = f.charAt(i);
                if (c >= '0' && c <= '9') {
                    sb.append(c);
                }
            }
            if (sb.length() == 0) {
                return 0;
            }
            try {
                return Integer.parseInt(sb.toString());
            } catch (Exception x) {
                return 0;
            }
        }
    }
}
