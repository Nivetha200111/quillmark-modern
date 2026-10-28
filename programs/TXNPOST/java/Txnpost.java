import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

public class Txnpost {
    private static final BigDecimal SURCHARGE_RATE = new BigDecimal("0.0125");
    private static final BigDecimal SURCHARGE_CAP = new BigDecimal("15.00");
    private static final BigDecimal ZERO_2 = new BigDecimal("0.00");
    private static final BigDecimal TWELVE = new BigDecimal("12");

    private BufferedReader txnReader;
    private BufferedWriter postWriter;
    private boolean endOfTxns = false;

    private String txnId = "         ";
    private char txnType = ' ';
    private BigDecimal txnAmount = ZERO_2;
    private BigDecimal txnBalance = ZERO_2;
    private int txnOdLimit = 0;

    private String postStatus = "  ";

    private BigDecimal wsCharge = ZERO_2;
    private BigDecimal wsCredit = ZERO_2;
    private BigDecimal wsNewBal = ZERO_2;
    private BigDecimal wsFloor = ZERO_2;

    private int wsPostedCnt = 0;
    private int wsRejectCnt = 0;

    public static void main(String[] args) throws Exception {
        Txnpost p = new Txnpost();
        p.p0000Main();
    }

    private void p0000Main() {
        p1000Init();
        while (!endOfTxns) {
            p2000Process();
        }
        p9000Term();
    }

    private void p1000Init() {
        String inPath = System.getenv("DD_TXNIN");
        String outPath = System.getenv("DD_POSTOUT");
        try {
            if (inPath == null || inPath.isEmpty()) {
                throw new java.io.IOException("missing DD_TXNIN");
            }
            txnReader = Files.newBufferedReader(Paths.get(inPath), StandardCharsets.ISO_8859_1);
        } catch (Exception e) {
            System.out.print("TXNPOST OPEN TXNIN FAILED 35\n");
            System.exit(16);
            return;
        }
        try {
            if (outPath == null || outPath.isEmpty()) {
                throw new java.io.IOException("missing DD_POSTOUT");
            }
            postWriter = Files.newBufferedWriter(Paths.get(outPath), StandardCharsets.ISO_8859_1,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (Exception e) {
            try {
                if (txnReader != null) {
                    txnReader.close();
                }
            } catch (Exception ex) {
            }
            System.out.print("TXNPOST OPEN POSTOUT FAILED 35\n");
            System.exit(16);
            return;
        }
        p8000Read();
    }

    private void p2000Process() {
        wsCharge = ZERO_2;
        wsNewBal = txnBalance;
        if (txnType == 'P') {
            p3000PostPayment();
        } else if (txnType == 'F') {
            BigDecimal r = txnBalance.subtract(txnAmount);
            r = r.setScale(2, RoundingMode.UNNECESSARY);
            wsNewBal = fit(r, 9);
            postStatus = "FE";
        } else if (txnType == 'I') {
            BigDecimal c = txnAmount.divide(TWELVE, 2, RoundingMode.HALF_UP);
            wsCredit = fit(c, 7);
            BigDecimal r = txnBalance.add(wsCredit);
            r = r.setScale(2, RoundingMode.UNNECESSARY);
            wsNewBal = fit(r, 9);
            postStatus = "IC";
        } else {
            postStatus = "RJ";
        }
        if (postStatus.equals("RJ") || postStatus.equals("NS")) {
            wsRejectCnt = wsRejectCnt + 1;
        } else {
            wsPostedCnt = wsPostedCnt + 1;
        }
        p5000WriteResult();
        p8000Read();
    }

    private void p3000PostPayment() {
        if (txnAmount.compareTo(BigDecimal.ZERO) <= 0) {
            postStatus = "RJ";
        } else {
            BigDecimal prod = txnAmount.multiply(SURCHARGE_RATE);
            BigDecimal trunc = prod.setScale(2, RoundingMode.DOWN);
            wsCharge = fit(trunc, 3);
            if (wsCharge.compareTo(SURCHARGE_CAP) > 0) {
                wsCharge = SURCHARGE_CAP;
            }
            BigDecimal r = txnBalance.subtract(txnAmount).subtract(wsCharge);
            r = r.setScale(2, RoundingMode.UNNECESSARY);
            wsNewBal = fit(r, 9);
            BigDecimal od = BigDecimal.valueOf((long) txnOdLimit).setScale(2);
            BigDecimal f = ZERO_2.subtract(od);
            wsFloor = fit(f, 9);
            if (wsNewBal.compareTo(wsFloor) < 0) {
                wsNewBal = txnBalance;
                wsCharge = ZERO_2;
                postStatus = "NS";
            } else {
                postStatus = "OK";
            }
        }
    }

    private void p5000WriteResult() {
        String chargeStr = formatSigned(wsCharge, 3);
        String newBalStr = formatSigned(wsNewBal, 9);
        String line = txnId + chargeStr + newBalStr + postStatus;
        try {
            postWriter.write(line);
            postWriter.write("\n");
        } catch (Exception e) {
        }
    }

    private void p8000Read() {
        try {
            String line = txnReader.readLine();
            if (line == null) {
                endOfTxns = true;
            } else {
                if (line.length() < 37) {
                    StringBuilder sb = new StringBuilder(line);
                    while (sb.length() < 37) {
                        sb.append(' ');
                    }
                    line = sb.toString();
                } else if (line.length() > 37) {
                    line = line.substring(0, 37);
                }
                txnId = line.substring(0, 9);
                txnType = line.charAt(9);
                txnAmount = parseSigned(line.substring(10, 20), 7, 2);
                txnBalance = parseSigned(line.substring(20, 32), 9, 2);
                txnOdLimit = parseUnsigned(line.substring(32, 37));
            }
        } catch (Exception e) {
            endOfTxns = true;
        }
    }

    private void p9000Term() {
        try {
            if (txnReader != null) {
                txnReader.close();
            }
        } catch (Exception e) {
        }
        try {
            if (postWriter != null) {
                postWriter.close();
            }
        } catch (Exception e) {
        }
        String out = "TXNPOST POSTED=" + String.format(Locale.US, "%010d", wsPostedCnt)
                + " REJECTED=" + String.format(Locale.US, "%010d", wsRejectCnt);
        System.out.print(out + "\n");
    }

    private static BigDecimal fit(BigDecimal v, int intDigits) {
        BigDecimal mod = BigDecimal.TEN.pow(intDigits);
        if (v.abs().compareTo(mod) < 0) {
            return v.setScale(2, RoundingMode.UNNECESSARY);
        }
        BigDecimal r = v.remainder(mod);
        return r.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal parseSigned(String field, int intDigits, int decDigits) {
        char s = field.charAt(0);
        String digits = field.substring(1);
        StringBuilder sb = new StringBuilder(digits.length());
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            } else {
                sb.append('0');
            }
        }
        String all = sb.toString();
        String intPart = all.substring(0, intDigits);
        String decPart = all.substring(intDigits);
        String num = intPart + "." + decPart;
        if (s == '-') {
            num = "-" + num;
        }
        return new BigDecimal(num);
    }

    private static int parseUnsigned(String field) {
        StringBuilder sb = new StringBuilder(field.length());
        for (int i = 0; i < field.length(); i++) {
            char c = field.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            } else if (c == ' ') {
                sb.append('0');
            } else {
                sb.append('0');
            }
        }
        String s = sb.toString();
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String formatSigned(BigDecimal v, int intDigits) {
        boolean neg = v.compareTo(BigDecimal.ZERO) < 0;
        BigDecimal abs = v.abs().setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal scaled = abs.movePointRight(2);
        long cents = scaled.longValueExact();
        int total = intDigits + 2;
        String digits = String.format(Locale.US, "%0" + total + "d", cents);
        if (digits.length() > total) {
            digits = digits.substring(digits.length() - total);
        }
        return (neg ? "-" : "+") + digits;
    }
}
