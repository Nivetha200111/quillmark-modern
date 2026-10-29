import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;

public class Intaccr {
    private String wsAcctStatus = "00";
    private String wsOutStatus = "00";
    private boolean eofFlag = false;
    private final BigDecimal wsPenaltyRate = new BigDecimal("0.0500");
    private final BigDecimal wsFeeFloor = new BigDecimal("1000.00");
    private final BigDecimal wsFeeCap = new BigDecimal("25.00");
    private BigDecimal wsInterest = BigDecimal.ZERO;
    private BigDecimal wsFee = BigDecimal.ZERO;
    private BigDecimal wsNewBal = BigDecimal.ZERO;
    private boolean wsInterestNeg = false;
    private boolean wsFeeNeg = false;
    private boolean wsNewBalNeg = false;
    private int wsReadCnt = 0;
    private int wsWriteCnt = 0;
    private BufferedReader acctReader;
    private BufferedWriter outWriter;
    private String acctId = "          ";
    private BigDecimal acctBalance = BigDecimal.ZERO;
    private BigDecimal acctRate = BigDecimal.ZERO;
    private int acctDays = 0;
    private String acctTier = " ";
    private static final BigInteger MOD_7 = BigInteger.TEN.pow(7);
    private static final BigInteger MOD_9 = BigInteger.TEN.pow(9);
    private static final BigInteger MOD_11 = BigInteger.TEN.pow(11);
    private static final BigInteger MOD_FEE = BigInteger.TEN.pow(7);
    private static final BigInteger DENOM = BigInteger.valueOf(3650000L);

    public static void main(String[] args) throws Exception {
        Intaccr prog = new Intaccr();
        prog.m0000Main();
    }

    private void m0000Main() throws Exception {
        m1000Init();
        while (!eofFlag) {
            m2000Process();
        }
        m9000Term();
    }

    private void m1000Init() throws Exception {
        String inPath = System.getenv("DD_ACCTIN");
        if (inPath == null || inPath.isEmpty()) {
            inPath = "ACCTIN";
        }
        String outPath = System.getenv("DD_INTOUT");
        if (outPath == null || outPath.isEmpty()) {
            outPath = "INTOUT";
        }
        try {
            acctReader = new BufferedReader(new InputStreamReader(new FileInputStream(inPath), StandardCharsets.ISO_8859_1));
            wsAcctStatus = "00";
        } catch (java.io.FileNotFoundException e) {
            wsAcctStatus = "35";
            System.out.println("INTACCR OPEN ACCTIN FAILED " + wsAcctStatus);
            System.exit(16);
            return;
        } catch (IOException e) {
            wsAcctStatus = "30";
            System.out.println("INTACCR OPEN ACCTIN FAILED " + wsAcctStatus);
            System.exit(16);
            return;
        }
        try {
            outWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outPath), StandardCharsets.ISO_8859_1));
            wsOutStatus = "00";
        } catch (IOException e) {
            wsOutStatus = "30";
            System.out.println("INTACCR OPEN INTOUT FAILED " + wsOutStatus);
            try { acctReader.close(); } catch (Exception ex) { }
            System.exit(16);
            return;
        }
        m8000Read();
    }

    private void m2000Process() throws Exception {
        wsReadCnt = (wsReadCnt + 1) % 10000000;
        m3000CalcInterest();
        m4000CalcFee();
        m5000WriteResult();
        m8000Read();
    }

    private void m3000CalcInterest() {
        if (acctBalance.compareTo(BigDecimal.ZERO) > 0) {
            wsInterest = interestRounded(acctBalance, acctRate, acctDays);
        } else if (acctBalance.compareTo(BigDecimal.ZERO) < 0) {
            BigDecimal effRate = acctRate.add(wsPenaltyRate);
            wsInterest = interestRounded(acctBalance, effRate, acctDays);
        } else {
            wsInterest = BigDecimal.ZERO;
            wsInterestNeg = false;
        }
    }

    private void m4000CalcFee() {
        wsFee = BigDecimal.ZERO;
        wsFeeNeg = false;
        boolean isPremium = "P".equals(acctTier);
        if (!isPremium && acctBalance.compareTo(wsFeeFloor) < 0) {
            BigDecimal diff = wsFeeFloor.subtract(acctBalance);
            BigDecimal raw = diff.multiply(new BigDecimal("0.0015"));
            BigDecimal trunc = raw.setScale(2, RoundingMode.DOWN);
            BigDecimal stored = truncStore(trunc, 5, 2);
            wsFee = stored;
            wsFeeNeg = trunc.signum() < 0;
            if (wsFee.compareTo(wsFeeCap) > 0) {
                wsFee = wsFeeCap;
                wsFeeNeg = false;
            }
        }
    }

    private void m5000WriteResult() throws Exception {
        BigDecimal newBalRaw = acctBalance.add(wsInterest).subtract(wsFee);
        wsNewBal = truncStore(newBalRaw.setScale(2, RoundingMode.DOWN), 9, 2);
        wsNewBalNeg = newBalRaw.signum() < 0;
        String outId = acctId;
        String sInterest = formatSigned(wsInterest, 7, 2, wsInterestNeg);
        String sFee = formatSigned(wsFee, 5, 2, wsFeeNeg);
        String sNewBal = formatSigned(wsNewBal, 9, 2, wsNewBalNeg);
        String status;
        if (wsNewBal.compareTo(BigDecimal.ZERO) < 0) {
            status = "OD";
        } else if (wsFee.compareTo(BigDecimal.ZERO) > 0) {
            status = "FE";
        } else {
            status = "OK";
        }
        String rec = outId + sInterest + sFee + sNewBal + status;
        outWriter.write(rec);
        outWriter.write("\n");
        wsWriteCnt = (wsWriteCnt + 1) % 10000000;
    }

    private void m8000Read() throws Exception {
        String line = null;
        try {
            line = acctReader.readLine();
        } catch (IOException e) {
            line = null;
        }
        if (line == null) {
            eofFlag = true;
            return;
        }
        if (line.endsWith("\r") && line.length() > 0) {
        }
        String rec = padRight(line, 31);
        if (rec.length() > 31) {
            rec = rec.substring(0, 31);
        }
        acctId = rec.substring(0, 10);
        String sBal = rec.substring(10, 22);
        String sRate = rec.substring(22, 27);
        String sDays = rec.substring(27, 30);
        String sTier = rec.substring(30, 31);
        acctBalance = parseBalance(sBal);
        acctRate = parseRate(sRate);
        acctDays = parseDays(sDays);
        acctTier = sTier;
    }

    private void m9000Term() throws Exception {
        try { if (acctReader != null) acctReader.close(); } catch (Exception e) { }
        try { if (outWriter != null) outWriter.close(); } catch (Exception e) { }
        String r = String.format("%07d", wsReadCnt);
        String w = String.format("%07d", wsWriteCnt);
        System.out.println("INTACCR READ=" + r + " WRITTEN=" + w);
    }

    private BigDecimal interestRounded(BigDecimal bal, BigDecimal rate, int days) {
        if (days == 0 || bal.compareTo(BigDecimal.ZERO) == 0) {
            wsInterestNeg = false;
            return BigDecimal.ZERO;
        }
        BigInteger bScaled = bal.movePointRight(2).toBigIntegerExact();
        BigInteger rScaled = rate.movePointRight(4).toBigIntegerExact();
        BigInteger num = bScaled.multiply(rScaled).multiply(BigInteger.valueOf(days));
        boolean neg = num.signum() < 0;
        BigInteger abs = num.abs();
        BigInteger q = abs.divide(DENOM);
        BigInteger rem = abs.remainder(DENOM);
        if (rem.multiply(BigInteger.valueOf(2)).compareTo(DENOM) >= 0) {
            q = q.add(BigInteger.ONE);
        }
        if (neg) {
            q = q.negate();
        }
        wsInterestNeg = q.signum() < 0;
        BigInteger trunc = q.remainder(MOD_9);
        return new BigDecimal(trunc, 2);
    }

    private static BigDecimal truncStore(BigDecimal v, int intDigits, int fracDigits) {
        BigDecimal s = v.setScale(fracDigits, RoundingMode.DOWN);
        BigInteger scaled = s.movePointRight(fracDigits).toBigIntegerExact();
        BigInteger mod = BigInteger.TEN.pow(intDigits + fracDigits);
        BigInteger t = scaled.remainder(mod);
        return new BigDecimal(t, fracDigits);
    }

    private static String formatSigned(BigDecimal v, int intDigits, int fracDigits, boolean neg) {
        BigDecimal s = v.setScale(fracDigits, RoundingMode.DOWN);
        char sign = neg ? '-' : (s.signum() < 0 ? '-' : '+');
        BigInteger abs = s.abs().movePointRight(fracDigits).toBigIntegerExact();
        String digits = abs.toString();
        int total = intDigits + fracDigits;
        if (digits.length() < total) {
            StringBuilder sb = new StringBuilder(total);
            for (int i = 0; i < total - digits.length(); i++) sb.append('0');
            sb.append(digits);
            digits = sb.toString();
        } else if (digits.length() > total) {
            digits = digits.substring(digits.length() - total);
        }
        return sign + digits;
    }

    private static String padRight(String s, int n) {
        if (s.length() >= n) return s;
        StringBuilder sb = new StringBuilder(n);
        sb.append(s);
        for (int i = s.length(); i < n; i++) sb.append(' ');
        return sb.toString();
    }

    private static BigDecimal parseBalance(String f) {
        if (f == null || f.isEmpty()) return BigDecimal.ZERO;
        if (f.indexOf('.') >= 0) {
            String t = f.trim();
            if (t.isEmpty() || t.equals("+") || t.equals("-")) return BigDecimal.ZERO;
            try {
                BigDecimal v = new BigDecimal(t);
                v = v.setScale(2, RoundingMode.DOWN);
                return truncStore(v, 9, 2);
            } catch (NumberFormatException e) {
                return BigDecimal.ZERO;
            }
        }
        char c0 = f.charAt(0);
        boolean hasSign = (c0 == '+' || c0 == '-' || c0 == ' ');
        boolean neg = (c0 == '-');
        String part = hasSign ? f.substring(1) : f;
        StringBuilder dg = new StringBuilder();
        for (int i = 0; i < part.length(); i++) {
            char c = part.charAt(i);
            if (c >= '0' && c <= '9') dg.append(c);
        }
        if (dg.length() == 0) return BigDecimal.ZERO;
        BigInteger bi = new BigInteger(dg.toString());
        BigDecimal v = new BigDecimal(bi, 2);
        if (neg) v = v.negate();
        return v;
    }

    private static BigDecimal parseRate(String f) {
        if (f == null) return BigDecimal.ZERO;
        if (f.indexOf('.') >= 0) {
            String t = f.trim();
            if (t.isEmpty()) return BigDecimal.ZERO;
            try {
                return new BigDecimal(t);
            } catch (NumberFormatException e) {
                return BigDecimal.ZERO;
            }
        }
        String t = f.trim();
        if (t.isEmpty()) return BigDecimal.ZERO;
        try {
            long v = Long.parseLong(t);
            return BigDecimal.valueOf(v, 4);
        } catch (NumberFormatException e) {
            StringBuilder dg = new StringBuilder();
            for (int i = 0; i < f.length(); i++) {
                char c = f.charAt(i);
                if (c >= '0' && c <= '9') dg.append(c);
            }
            if (dg.length() == 0) return BigDecimal.ZERO;
            long v = Long.parseLong(dg.toString());
            return BigDecimal.valueOf(v, 4);
        }
    }

    private static int parseDays(String f) {
        if (f == null) return 0;
        String t = f.trim();
        if (t.isEmpty()) return 0;
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException e) {
            StringBuilder dg = new StringBuilder();
            for (int i = 0; i < f.length(); i++) {
                char c = f.charAt(i);
                if (c >= '0' && c <= '9') dg.append(c);
            }
            if (dg.length() == 0) return 0;
            return Integer.parseInt(dg.toString());
        }
    }
}
