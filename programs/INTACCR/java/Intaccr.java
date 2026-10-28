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
import java.util.Locale;

public class Intaccr {
    private BufferedReader acctReader;
    private BufferedWriter outWriter;
    private boolean endOfAccounts = false;

    private String acctId = "          ";
    private BigDecimal acctBalance = BigDecimal.ZERO.setScale(2);
    private BigDecimal acctRate = BigDecimal.ZERO.setScale(4);
    private int acctDays = 0;
    private boolean acctPremium = false;

    private BigDecimal wsPenaltyRate = new BigDecimal("0.0500");
    private BigDecimal wsFeeFloor = new BigDecimal("1000.00");
    private BigDecimal wsFeeCap = new BigDecimal("25.00");
    private BigDecimal wsInterest = BigDecimal.ZERO.setScale(2);
    private BigDecimal wsFee = BigDecimal.ZERO.setScale(2);
    private BigDecimal wsNewBal = BigDecimal.ZERO.setScale(2);
    private int wsReadCnt = 0;
    private int wsWriteCnt = 0;

    private static final BigDecimal D365 = new BigDecimal("365");
    private static final BigDecimal D_FEE_FACTOR = new BigDecimal("0.0015");

    public static void main(String[] args) throws IOException {
        Intaccr p = new Intaccr();
        p.m0000Main();
    }

    private void m0000Main() throws IOException {
        m1000Init();
        while (!endOfAccounts) {
            m2000Process();
        }
        m9000Term();
    }

    private void m1000Init() throws IOException {
        String inPath = System.getenv("DD_ACCTIN");
        String outPath = System.getenv("DD_INTOUT");
        if (inPath == null || inPath.isEmpty()) {
            System.out.println("INTACCR OPEN ACCTIN FAILED 35");
            System.exit(16);
            return;
        }
        try {
            acctReader = new BufferedReader(new InputStreamReader(new FileInputStream(inPath), StandardCharsets.ISO_8859_1));
        } catch (IOException e) {
            System.out.println("INTACCR OPEN ACCTIN FAILED 35");
            System.exit(16);
            return;
        }
        if (outPath == null || outPath.isEmpty()) {
            System.out.println("INTACCR OPEN INTOUT FAILED 35");
            System.exit(16);
            return;
        }
        try {
            outWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outPath), StandardCharsets.ISO_8859_1));
        } catch (IOException e) {
            System.out.println("INTACCR OPEN INTOUT FAILED 35");
            System.exit(16);
            return;
        }
        m8000Read();
    }

    private void m2000Process() throws IOException {
        wsReadCnt++;
        m3000CalcInterest();
        m4000CalcFee();
        m5000WriteResult();
        m8000Read();
    }

    private void m3000CalcInterest() {
        if (acctBalance.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal num = acctBalance.multiply(acctRate).multiply(BigDecimal.valueOf((long) acctDays));
            BigDecimal rounded = num.divide(D365, 2, RoundingMode.HALF_UP);
            wsInterest = fit(rounded, 7, 2);
        } else if (acctBalance.compareTo(BigDecimal.ZERO) < 0) {
            BigDecimal eff = acctRate.add(wsPenaltyRate);
            BigDecimal num = acctBalance.multiply(eff).multiply(BigDecimal.valueOf((long) acctDays));
            BigDecimal rounded = num.divide(D365, 2, RoundingMode.HALF_UP);
            wsInterest = fit(rounded, 7, 2);
        } else {
            wsInterest = BigDecimal.ZERO.setScale(2);
        }
    }

    private void m4000CalcFee() {
        wsFee = BigDecimal.ZERO.setScale(2);
        if (!acctPremium && acctBalance.compareTo(wsFeeFloor) < 0) {
            BigDecimal diff = wsFeeFloor.subtract(acctBalance);
            BigDecimal raw = diff.multiply(D_FEE_FACTOR);
            BigDecimal trunc = raw.setScale(2, RoundingMode.DOWN);
            wsFee = fit(trunc, 5, 2);
            if (wsFee.compareTo(wsFeeCap) > 0) {
                wsFee = wsFeeCap;
            }
        }
    }

    private void m5000WriteResult() throws IOException {
        BigDecimal rawNew = acctBalance.add(wsInterest).subtract(wsFee);
        rawNew = rawNew.setScale(2, RoundingMode.UNNECESSARY);
        wsNewBal = fit(rawNew, 9, 2);
        String outInterestStr = formatSignedSeparate(wsInterest, 7, 2);
        String outFeeStr = formatSignedSeparate(wsFee, 5, 2);
        String outNewBalStr = formatSignedSeparate(wsNewBal, 9, 2);
        String outStatus;
        if (wsNewBal.compareTo(BigDecimal.ZERO) < 0) {
            outStatus = "OD";
        } else if (wsFee.compareTo(BigDecimal.ZERO) > 0) {
            outStatus = "FE";
        } else {
            outStatus = "OK";
        }
        String id = acctId;
        if (id.length() < 10) {
            StringBuilder sb = new StringBuilder(id);
            while (sb.length() < 10) sb.append(' ');
            id = sb.toString();
        } else if (id.length() > 10) {
            id = id.substring(0, 10);
        }
        String outRec = id + outInterestStr + outFeeStr + outNewBalStr + outStatus;
        outWriter.write(outRec);
        outWriter.write('\n');
        wsWriteCnt++;
    }

    private void m8000Read() throws IOException {
        String line = acctReader.readLine();
        if (line == null) {
            endOfAccounts = true;
            return;
        }
        if (line.length() < 31) {
            StringBuilder sb = new StringBuilder(line);
            while (sb.length() < 31) sb.append(' ');
            line = sb.toString();
        } else if (line.length() > 31) {
            line = line.substring(0, 31);
        }
        acctId = line.substring(0, 10);
        String balStr = line.substring(10, 22);
        String rateStr = line.substring(22, 27);
        String daysStr = line.substring(27, 30);
        String tierStr = line.substring(30, 31);
        char s = balStr.charAt(0);
        boolean neg = (s == '-');
        String digPart = balStr.substring(1);
        StringBuilder cleaned = new StringBuilder();
        for (int i = 0; i < digPart.length(); i++) {
            char c = digPart.charAt(i);
            if (c == ' ') cleaned.append('0');
            else cleaned.append(c);
        }
        BigInteger unscaled;
        try {
            String num = cleaned.toString();
            if (num.isEmpty()) num = "0";
            unscaled = new BigInteger((neg ? "-" : "") + num);
        } catch (NumberFormatException e) {
            unscaled = BigInteger.ZERO;
        }
        acctBalance = new BigDecimal(unscaled, 2);
        StringBuilder rc = new StringBuilder();
        for (int i = 0; i < rateStr.length(); i++) {
            char c = rateStr.charAt(i);
            if (c == ' ') rc.append('0');
            else rc.append(c);
        }
        String rs = rc.toString();
        if (rs.isEmpty()) rs = "0";
        BigInteger rUnscaled;
        try {
            rUnscaled = new BigInteger(rs);
        } catch (NumberFormatException e) {
            rUnscaled = BigInteger.ZERO;
        }
        acctRate = new BigDecimal(rUnscaled, 4);
        StringBuilder dc = new StringBuilder();
        for (int i = 0; i < daysStr.length(); i++) {
            char c = daysStr.charAt(i);
            if (c == ' ') dc.append('0');
            else dc.append(c);
        }
        String ds = dc.toString();
        if (ds.isEmpty()) ds = "0";
        int dval = 0;
        try {
            dval = Integer.parseInt(ds);
        } catch (NumberFormatException e) {
            dval = 0;
        }
        acctDays = dval;
        acctPremium = tierStr.equals("P");
    }

    private void m9000Term() throws IOException {
        if (acctReader != null) {
            try { acctReader.close(); } catch (IOException ignored) { }
        }
        if (outWriter != null) {
            try { outWriter.close(); } catch (IOException ignored) { }
        }
        System.out.println("INTACCR READ=" + formatCount(wsReadCnt) + " WRITTEN=" + formatCount(wsWriteCnt));
    }

    private static BigDecimal fit(BigDecimal v, int intDigits, int scale) {
        BigDecimal s = v.setScale(scale, RoundingMode.UNNECESSARY);
        if (s.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(scale);
        }
        BigInteger base = BigInteger.TEN.pow(intDigits + scale);
        BigInteger u = s.unscaledValue();
        BigInteger r = u.remainder(base);
        return new BigDecimal(r, scale);
    }

    private static String formatSignedSeparate(BigDecimal v, int intDigits, int fracDigits) {
        boolean negative = v.signum() < 0;
        BigDecimal abs = v.abs().setScale(fracDigits, RoundingMode.UNNECESSARY);
        BigInteger u = abs.unscaledValue();
        BigInteger powFrac = BigInteger.TEN.pow(fracDigits);
        BigInteger[] dr = u.divideAndRemainder(powFrac);
        String intStr = dr[0].toString();
        String fracStr = dr[1].toString();
        while (fracStr.length() < fracDigits) fracStr = "0" + fracStr;
        while (intStr.length() < intDigits) intStr = "0" + intStr;
        if (intStr.length() > intDigits) intStr = intStr.substring(intStr.length() - intDigits);
        char sign = negative ? '-' : '+';
        return sign + intStr + fracStr;
    }

    private static String formatCount(int v) {
        long u = v & 0xFFFFFFFFL;
        return String.format(Locale.ROOT, "%010d", u);
    }
}
