import java.io.*;
import java.math.*;
import java.nio.charset.StandardCharsets;

public class Dayctrl {
    private static BufferedReader dayReader = null;
    private static BufferedWriter ctlWriter = null;
    private static String wsDayStatus = "00";
    private static String wsCtlStatus = "00";
    private static boolean endOfAccounts = false;
    private static BigDecimal wsInterest = BigDecimal.ZERO.setScale(2);
    private static BigDecimal wsCtlTotal = BigDecimal.ZERO.setScale(2);
    private static long wsReadCnt = 0;
    private static long wsLostCnt = 0;
    private static String dayAcctId = "          ";
    private static BigDecimal dayPrincipal = BigDecimal.ZERO.setScale(2);
    private static BigDecimal dayRate = BigDecimal.ZERO.setScale(4);
    private static int dayDays = 0;
    private static char ctlFlag = '-';
    private static String ctlAcctId = "          ";

    public static void main(String[] args) throws Exception {
        p0000Main();
    }

    private static void p0000Main() throws Exception {
        p1000Init();
        while (!endOfAccounts) {
            p2000Process();
        }
        p9000Term();
    }

    private static void p1000Init() throws Exception {
        String dayPath = System.getenv("DD_DAYIN");
        String ctlPath = System.getenv("DD_CTLOUT");
        if (dayPath == null || dayPath.isEmpty()) {
            wsDayStatus = "35";
            System.out.println("DAYCTRL OPEN DAYIN FAILED " + wsDayStatus);
            System.exit(16);
        }
        try {
            dayReader = new BufferedReader(new InputStreamReader(new FileInputStream(dayPath), StandardCharsets.ISO_8859_1));
            wsDayStatus = "00";
        } catch (FileNotFoundException e) {
            wsDayStatus = "35";
            System.out.println("DAYCTRL OPEN DAYIN FAILED " + wsDayStatus);
            System.exit(16);
        } catch (IOException e) {
            wsCtlStatus = "30";
            wsDayStatus = "30";
            System.out.println("DAYCTRL OPEN DAYIN FAILED " + wsDayStatus);
            System.exit(16);
        }
        if (ctlPath == null || ctlPath.isEmpty()) {
            wsCtlStatus = "35";
            System.out.println("DAYCTRL OPEN CTLOUT FAILED " + wsCtlStatus);
            System.exit(16);
        }
        try {
            ctlWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(ctlPath), StandardCharsets.ISO_8859_1));
            wsCtlStatus = "00";
        } catch (FileNotFoundException e) {
            wsCtlStatus = "35";
            System.out.println("DAYCTRL OPEN CTLOUT FAILED " + wsCtlStatus);
            System.exit(16);
        } catch (IOException e) {
            wsCtlStatus = "30";
            System.out.println("DAYCTRL OPEN CTLOUT FAILED " + wsCtlStatus);
            System.exit(16);
        }
        p8000Read();
    }

    private static void p2000Process() throws Exception {
        wsReadCnt = (wsReadCnt + 1) % 10000000L;
        p3000Accrue();
        p5000WriteResult();
        p8000Read();
    }

    private static void p3000Accrue() {
        BigDecimal daysBD = BigDecimal.valueOf(dayDays);
        BigDecimal prod = dayPrincipal.multiply(dayRate).multiply(daysBD);
        BigDecimal interest = prod.divide(BigDecimal.valueOf(365), 2, RoundingMode.HALF_UP);
        interest = truncToPic(interest, 7, 2);
        wsInterest = interest;
        wsCtlTotal = truncToPic(wsCtlTotal.add(wsInterest), 11, 2);
        boolean interestZero = wsInterest.compareTo(BigDecimal.ZERO) == 0;
        boolean princNz = dayPrincipal.compareTo(BigDecimal.ZERO) != 0;
        boolean rateNz = dayRate.compareTo(BigDecimal.ZERO) != 0;
        boolean daysNz = dayDays != 0;
        if (interestZero && princNz && rateNz && daysNz) {
            ctlFlag = 'Z';
            wsLostCnt = (wsLostCnt + 1) % 10000000L;
        } else {
            ctlFlag = '-';
        }
    }

    private static void p5000WriteResult() throws Exception {
        ctlAcctId = dayAcctId;
        String interestField = formatSignedSeparate(wsInterest, 7, 2);
        String totalField = formatSignedSeparate(wsCtlTotal, 11, 2);
        String line = ctlAcctId + interestField + totalField + ctlFlag;
        ctlWriter.write(line);
        ctlWriter.write("\n");
    }

    private static void p8000Read() throws Exception {
        if (dayReader == null) {
            endOfAccounts = true;
            return;
        }
        String line;
        try {
            line = dayReader.readLine();
        } catch (IOException e) {
            endOfAccounts = true;
            return;
        }
        if (line == null) {
            endOfAccounts = true;
            return;
        }
        if (line.length() < 30) {
            StringBuilder sb = new StringBuilder(line);
            while (sb.length() < 30) sb.append(' ');
            line = sb.toString();
        } else if (line.length() > 30) {
            line = line.substring(0, 30);
        }
        dayAcctId = line.substring(0, 10);
        String princStr = line.substring(10, 22);
        String rateStr = line.substring(22, 27);
        String daysStr = line.substring(27, 30);
        dayPrincipal = parsePrincipal(princStr);
        dayRate = parseRate(rateStr);
        dayDays = parseDays(daysStr);
    }

    private static void p9000Term() throws Exception {
        try { if (dayReader != null) dayReader.close(); } catch (IOException e) { }
        try { if (ctlWriter != null) ctlWriter.close(); } catch (IOException e) { }
        String edit = formatCtlEdit(wsCtlTotal);
        String out = "DAYCTRL ACCOUNTS=" + String.format("%07d", wsReadCnt)
                + " LOST=" + String.format("%07d", wsLostCnt)
                + " CONTROL=" + edit;
        System.out.println(out);
    }

    private static BigDecimal parsePrincipal(String s) {
        if (s == null || s.length() < 12) {
            s = (s == null ? "" : s);
            while (s.length() < 12) s = s + " ";
        }
        char sc = s.charAt(0);
        int sign = (sc == '-') ? -1 : 1;
        String digits = s.substring(1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            if (c >= '0' && c <= '9') sb.append(c);
            else if (c == ' ') sb.append('0');
            else if (c == '+' || c == '-') sb.append('0');
            else sb.append('0');
        }
        String ds = sb.toString();
        if (ds.isEmpty()) ds = "0";
        BigInteger bi = new BigInteger(ds);
        BigDecimal v = new BigDecimal(bi, 2);
        if (sign < 0) v = v.negate();
        return v;
    }

    private static BigDecimal parseRate(String s) {
        if (s == null) s = "";
        while (s.length() < 5) s = s + " ";
        if (s.length() > 5) s = s.substring(0, 5);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') sb.append(c);
            else if (c == ' ') sb.append('0');
            else sb.append('0');
        }
        String ds = sb.toString();
        if (ds.isEmpty()) ds = "0";
        BigInteger bi = new BigInteger(ds);
        return new BigDecimal(bi, 4);
    }

    private static int parseDays(String s) {
        if (s == null) s = "";
        while (s.length() < 3) s = s + " ";
        if (s.length() > 3) s = s.substring(0, 3);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') sb.append(c);
            else if (c == ' ') sb.append('0');
            else sb.append('0');
        }
        String ds = sb.toString();
        if (ds.isEmpty()) return 0;
        try { return Integer.parseInt(ds); } catch (NumberFormatException e) { return 0; }
    }

    private static BigDecimal truncToPic(BigDecimal v, int intDigits, int fracDigits) {
        int total = intDigits + fracDigits;
        BigInteger mod = BigInteger.TEN.pow(total);
        BigDecimal scaled = v.setScale(fracDigits, RoundingMode.UNNECESSARY).movePointRight(fracDigits);
        BigInteger unscaled;
        try { unscaled = scaled.toBigIntegerExact(); } catch (ArithmeticException e) { unscaled = scaled.toBigInteger(); }
        boolean neg = unscaled.signum() < 0;
        BigInteger abs = unscaled.abs().mod(mod);
        if (abs.equals(BigInteger.ZERO)) {
            return BigDecimal.ZERO.setScale(fracDigits);
        }
        BigInteger res = neg ? abs.negate() : abs;
        return new BigDecimal(res, fracDigits);
    }

    private static String formatSignedSeparate(BigDecimal v, int intDigits, int fracDigits) {
        int total = intDigits + fracDigits;
        boolean neg = v.compareTo(BigDecimal.ZERO) < 0;
        BigDecimal abs = v.abs().setScale(fracDigits, RoundingMode.UNNECESSARY);
        BigInteger unscaledAbs = abs.movePointRight(fracDigits).toBigIntegerExact().mod(BigInteger.TEN.pow(total));
        String digits = unscaledAbs.toString();
        if (digits.length() < total) digits = "0".repeat(total - digits.length()) + digits;
        else if (digits.length() > total) digits = digits.substring(digits.length() - total);
        char sign = (neg && !unscaledAbs.equals(BigInteger.ZERO)) ? '-' : '+';
        return sign + digits;
    }

    private static String formatCtlEdit(BigDecimal total) {
        boolean neg = total.compareTo(BigDecimal.ZERO) < 0;
        BigDecimal abs = total.abs().setScale(2, RoundingMode.UNNECESSARY);
        BigInteger unscaled = abs.movePointRight(2).toBigIntegerExact();
        BigInteger[] dr = unscaled.divideAndRemainder(BigInteger.valueOf(100));
        BigInteger intPart = dr[0];
        int frac = dr[1].intValue();
        String intStr = intPart.toString();
        BigInteger mod12 = BigInteger.TEN.pow(12);
        intPart = intPart.mod(mod12);
        intStr = intPart.toString();
        if (intStr.length() < 12) intStr = "0".repeat(12 - intStr.length()) + intStr;
        else if (intStr.length() > 12) intStr = intStr.substring(intStr.length() - 12);
        String fracStr = String.format("%02d", frac);
        int firstNonZero = -1;
        for (int i = 0; i < 12; i++) {
            if (intStr.charAt(i) != '0') { firstNonZero = i; break; }
        }
        char[] out = new char[12];
        for (int i = 0; i < 12; i++) out[i] = ' ';
        if (firstNonZero == -1) {
            out[11] = '0';
            if (neg) {
                if (!unscaled.equals(BigInteger.ZERO)) out[10] = '-';
            }
        } else {
            for (int i = firstNonZero; i < 12; i++) out[i] = intStr.charAt(i);
            if (neg) out[firstNonZero - 1] = '-';
        }
        return new String(out) + "." + fracStr;
    }
}
