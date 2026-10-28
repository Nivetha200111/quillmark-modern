import java.io.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class Intaccr {
    private String wsAcctStatus = "00";
    private String wsOutStatus = "00";
    private boolean endOfAccounts = false;
    private final BigDecimal wsPenaltyRate = new BigDecimal("0.0500");
    private final BigDecimal wsFeeFloor = new BigDecimal("1000.00");
    private final BigDecimal wsFeeCap = new BigDecimal("25.00");
    private BigDecimal wsInterest = BigDecimal.ZERO.setScale(2);
    private boolean wsInterestNegZero = false;
    private BigDecimal wsFee = BigDecimal.ZERO.setScale(2);
    private BigDecimal wsNewBal = BigDecimal.ZERO.setScale(2);
    private boolean wsNewBalNegZero = false;
    private long wsReadCnt = 0;
    private long wsWriteCnt = 0;

    private String acctId = "          ";
    private BigDecimal acctBalance = BigDecimal.ZERO.setScale(2);
    private BigDecimal acctRate = BigDecimal.ZERO.setScale(4);
    private int acctDays = 0;
    private String acctTier = " ";

    private BufferedReader acctReader = null;
    private OutputStream outStream = null;
    private String outAcctId = "          ";
    private BigDecimal outInterestV = BigDecimal.ZERO.setScale(2);
    private boolean outInterestNegZero = false;
    private BigDecimal outFeeV = BigDecimal.ZERO.setScale(2);
    private BigDecimal outNewBalV = BigDecimal.ZERO.setScale(2);
    private boolean outNewBalNegZero = false;
    private String outStatus = "  ";

    public static void main(String[] args) throws Exception {
        Intaccr p = new Intaccr();
        p.m0000Main();
    }

    private void m0000Main() throws Exception {
        m1000Init();
        while (!endOfAccounts) {
            m2000Process();
        }
        m9000Term();
    }

    private void m1000Init() throws Exception {
        String acctPath = System.getenv("DD_ACCTIN");
        String outPath = System.getenv("DD_INTOUT");
        if (acctPath == null || acctPath.isEmpty()) {
            wsAcctStatus = "35";
            displayLine("INTACCR OPEN ACCTIN FAILED " + wsAcctStatus);
            System.exit(16);
        }
        try {
            acctReader = Files.newBufferedReader(Paths.get(acctPath), StandardCharsets.ISO_8859_1);
            wsAcctStatus = "00";
        } catch (Exception e) {
            wsAcctStatus = "35";
            displayLine("INTACCR OPEN ACCTIN FAILED " + wsAcctStatus);
            System.exit(16);
        }
        if (outPath == null || outPath.isEmpty()) {
            wsOutStatus = "35";
            displayLine("INTACCR OPEN INTOUT FAILED " + wsOutStatus);
            System.exit(16);
        }
        try {
            Path op = Paths.get(outPath);
            Path parent = op.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            outStream = Files.newOutputStream(op, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            wsOutStatus = "00";
        } catch (Exception e) {
            wsOutStatus = "35";
            displayLine("INTACCR OPEN INTOUT FAILED " + wsOutStatus);
            System.exit(16);
        }
        m8000Read();
    }

    private void m2000Process() throws Exception {
        wsReadCnt = (wsReadCnt + 1) % 10000000L;
        m3000CalcInterest();
        m4000CalcFee();
        m5000WriteResult();
        m8000Read();
    }

    private void m3000CalcInterest() {
        int cmp = acctBalance.compareTo(BigDecimal.ZERO);
        if (cmp > 0) {
            BigDecimal numerator = acctBalance.multiply(acctRate).multiply(BigDecimal.valueOf(acctDays));
            wsInterest = roundQuotientToCents(numerator, 365);
            wsInterestNegZero = false;
            wsInterest = truncStore(wsInterest, 7, 2);
            if (wsInterest.compareTo(BigDecimal.ZERO) == 0) wsInterestNegZero = false;
        } else if (cmp < 0) {
            BigDecimal effRate = acctRate.add(wsPenaltyRate);
            BigDecimal numerator = acctBalance.multiply(effRate).multiply(BigDecimal.valueOf(acctDays));
            if (numerator.compareTo(BigDecimal.ZERO) == 0) {
                wsInterest = BigDecimal.ZERO.setScale(2);
                wsInterestNegZero = false;
            } else {
                BigDecimal r = roundQuotientToCents(numerator, 365);
                if (r.compareTo(BigDecimal.ZERO) == 0) {
                    wsInterest = BigDecimal.ZERO.setScale(2);
                    wsInterestNegZero = numerator.signum() < 0;
                } else {
                    wsInterest = truncStore(r, 7, 2);
                    wsInterestNegZero = false;
                    if (wsInterest.compareTo(BigDecimal.ZERO) == 0) wsInterestNegZero = numerator.signum() < 0;
                }
            }
        } else {
            wsInterest = BigDecimal.ZERO.setScale(2);
            wsInterestNegZero = false;
        }
    }

    private void m4000CalcFee() {
        wsFee = BigDecimal.ZERO.setScale(2);
        boolean isPremium = "P".equals(acctTier);
        if (!isPremium && acctBalance.compareTo(wsFeeFloor) < 0) {
            BigDecimal diff = wsFeeFloor.subtract(acctBalance);
            BigDecimal prod = diff.multiply(new BigDecimal("0.0015"));
            BigDecimal t = prod.setScale(2, RoundingMode.DOWN);
            t = t.setScale(2);
            t = truncStore(t, 5, 2);
            wsFee = t;
            if (wsFee.compareTo(wsFeeCap) > 0) {
                wsFee = wsFeeCap;
            }
        }
    }

    private void m5000WriteResult() throws Exception {
        BigDecimal nb = acctBalance.add(wsInterest).subtract(wsFee);
        nb = nb.setScale(2);
        nb = truncStore(nb, 9, 2);
        boolean negZero = false;
        if (nb.compareTo(BigDecimal.ZERO) == 0) {
            boolean exactNeg = false;
            BigDecimal exact = acctBalance.add(effectiveInterestValue()).subtract(wsFee);
            if (exact.compareTo(BigDecimal.ZERO) < 0) exactNeg = true;
            else if (exact.compareTo(BigDecimal.ZERO) == 0) {
                if (wsInterestNegZero) exactNeg = true;
            }
            negZero = exactNeg;
        }
        wsNewBal = nb;
        wsNewBalNegZero = negZero;
        outAcctId = acctId;
        outInterestV = wsInterest;
        outInterestNegZero = wsInterestNegZero;
        outFeeV = wsFee;
        outNewBalV = wsNewBal;
        outNewBalNegZero = wsNewBalNegZero;
        if (isNegative(wsNewBal, wsNewBalNegZero)) {
            outStatus = "OD";
        } else if (wsFee.compareTo(BigDecimal.ZERO) > 0) {
            outStatus = "FE";
        } else {
            outStatus = "OK";
        }
        String rec = padRight(outAcctId, 10)
            + formatSigned(outInterestV, outInterestNegZero, 7, 2)
            + formatSigned(outFeeV, false, 5, 2)
            + formatSigned(outNewBalV, outNewBalNegZero, 9, 2)
            + outStatus;
        outStream.write(rec.getBytes(StandardCharsets.ISO_8859_1));
        outStream.write('\n');
        wsWriteCnt = (wsWriteCnt + 1) % 10000000L;
    }

    private void m8000Read() throws Exception {
        String line = acctReader.readLine();
        if (line == null) {
            endOfAccounts = true;
            return;
        }
        if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
        if (line.length() < 31) line = line + " ".repeat(31 - line.length());
        if (line.length() > 31) line = line.substring(0, 31);
        acctId = line.substring(0, 10);
        String balStr = line.substring(10, 22);
        String rateStr = line.substring(22, 27);
        String daysStr = line.substring(27, 30);
        acctTier = line.substring(30, 31);
        acctBalance = parseBalance(balStr);
        acctRate = parseRate(rateStr);
        acctDays = parseDays(daysStr);
    }

    private void m9000Term() throws Exception {
        try { if (acctReader != null) acctReader.close(); } catch (Exception e) {}
        try { if (outStream != null) outStream.close(); } catch (Exception e) {}
        String cntR = String.format("%07d", wsReadCnt);
        String cntW = String.format("%07d", wsWriteCnt);
        displayLine("INTACCR READ=" + cntR + " WRITTEN=" + cntW);
    }

    private BigDecimal effectiveInterestValue() {
        if (wsInterestNegZero) return new BigDecimal("-0.00");
        return wsInterest;
    }

    private static boolean isNegative(BigDecimal v, boolean negZero) {
        if (v.compareTo(BigDecimal.ZERO) < 0) return true;
        if (v.compareTo(BigDecimal.ZERO) == 0 && negZero) return true;
        return false;
    }

    private static BigDecimal roundQuotientToCents(BigDecimal numerator, int divisor) {
        BigInteger N = numerator.movePointRight(6).toBigIntegerExact();
        BigInteger D = BigInteger.valueOf(3650000L);
        if (N.signum() == 0) return BigDecimal.ZERO.setScale(2);
        boolean neg = N.signum() < 0;
        BigInteger absN = N.abs();
        BigInteger[] qr = absN.divideAndRemainder(D);
        BigInteger q = qr[0];
        BigInteger r = qr[1];
        BigInteger twice = r.shiftLeft(1);
        int c = twice.compareTo(D);
        if (c > 0 || c == 0) q = q.add(BigInteger.ONE);
        if (neg) q = q.negate();
        BigDecimal cents = new BigDecimal(q);
        return cents.movePointLeft(2).setScale(2);
    }

    private static BigDecimal truncStore(BigDecimal v, int intDigits, int fracDigits) {
        BigDecimal t = v.setScale(fracDigits, RoundingMode.DOWN);
        BigInteger scaled = t.movePointRight(fracDigits).toBigIntegerExact();
        BigInteger mod = BigInteger.TEN.pow(intDigits + fracDigits);
        boolean neg = scaled.signum() < 0;
        BigInteger abs = scaled.abs();
        BigInteger trunc = abs.mod(mod);
        if (neg) trunc = trunc.negate();
        return new BigDecimal(trunc).movePointLeft(fracDigits).setScale(fracDigits);
    }

    private static String formatSigned(BigDecimal v, boolean negZero, int intDigits, int fracDigits) {
        boolean neg;
        if (v.compareTo(BigDecimal.ZERO) < 0) neg = true;
        else if (v.compareTo(BigDecimal.ZERO) == 0) neg = negZero;
        else neg = false;
        BigInteger scaled = v.movePointRight(fracDigits).toBigIntegerExact().abs();
        String digits = scaled.toString();
        int total = intDigits + fracDigits;
        if (digits.length() < total) digits = "0".repeat(total - digits.length()) + digits;
        else if (digits.length() > total) digits = digits.substring(digits.length() - total);
        return (neg ? "-" : "+") + digits;
    }

    private static String padRight(String s, int len) {
        if (s.length() >= len) return s.substring(0, len);
        return s + " ".repeat(len - s.length());
    }

    private static BigDecimal parseBalance(String s) {
        char sc = s.charAt(0);
        String digs = s.substring(1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digs.length(); i++) {
            char c = digs.charAt(i);
            if (c >= '0' && c <= '9') sb.append(c);
            else if (c == ' ') sb.append('0');
            else sb.append(c);
        }
        String d = sb.toString();
        if (d.isEmpty()) d = "0";
        BigDecimal iv = new BigDecimal(new BigInteger(d));
        BigDecimal v = iv.movePointLeft(2).setScale(2);
        if (sc == '-') v = v.negate();
        return v;
    }

    private static BigDecimal parseRate(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') sb.append(c);
            else if (c == ' ') sb.append('0');
            else sb.append(c);
        }
        String d = sb.toString();
        if (d.isEmpty()) d = "0";
        BigDecimal iv = new BigDecimal(new BigInteger(d));
        return iv.movePointLeft(4).setScale(4);
    }

    private static int parseDays(String s) {
        String t = s.trim();
        if (t.isEmpty()) return 0;
        try { return Integer.parseInt(t); } catch (Exception e) { return 0; }
    }

    private static void displayLine(String s) throws Exception {
        System.out.write((s + "\n").getBytes(StandardCharsets.ISO_8859_1));
        System.out.flush();
    }
}
