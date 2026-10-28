import java.io.BufferedReader;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;

public class Txnpost {
    private BufferedReader txnReader;
    private BufferedOutputStream postOut;
    private String wsTxnStatus = "00";
    private String wsPostStatus = "00";
    private boolean endOfTxns = false;
    private final BigDecimal wsSurchargeRate = new BigDecimal("0.0125");
    private final BigDecimal wsSurchargeCap = new BigDecimal("15.00");
    private BigDecimal wsCharge = new BigDecimal("0.00");
    private BigDecimal wsCredit = new BigDecimal("0.00");
    private BigDecimal wsNewBal = new BigDecimal("0.00");
    private BigDecimal wsFloor = new BigDecimal("0.00");
    private int wsPostedCnt = 0;
    private int wsRejectCnt = 0;
    private String txnId = "         ";
    private char txnType = ' ';
    private BigDecimal txnAmount = new BigDecimal("0.00");
    private BigDecimal txnBalance = new BigDecimal("0.00");
    private int txnOdLimit = 0;
    private String postTxnId = "         ";
    private BigDecimal postChargeVal = new BigDecimal("0.00");
    private BigDecimal postNewBalVal = new BigDecimal("0.00");
    private String postStatus = "  ";

    public static void main(String[] args) throws Exception {
        Txnpost p = new Txnpost();
        p.p0000Main();
    }

    private void p0000Main() throws Exception {
        p1000Init();
        while (!endOfTxns) {
            p2000Process();
        }
        p9000Term();
    }

    private void p1000Init() throws Exception {
        String inPath = System.getenv("DD_TXNIN");
        String outPath = System.getenv("DD_POSTOUT");
        if (inPath == null || inPath.isEmpty()) {
            wsTxnStatus = "35";
            System.out.println("TXNPOST OPEN TXNIN FAILED " + wsTxnStatus);
            System.exit(16);
            return;
        }
        try {
            txnReader = new BufferedReader(new InputStreamReader(new FileInputStream(inPath), StandardCharsets.ISO_8859_1));
            wsTxnStatus = "00";
        } catch (Exception e) {
            wsTxnStatus = "35";
            System.out.println("TXNPOST OPEN TXNIN FAILED " + wsTxnStatus);
            System.exit(16);
            return;
        }
        if (outPath == null || outPath.isEmpty()) {
            wsPostStatus = "30";
            System.out.println("TXNPOST OPEN POSTOUT FAILED " + wsPostStatus);
            System.exit(16);
            return;
        }
        try {
            postOut = new BufferedOutputStream(new FileOutputStream(outPath));
            wsPostStatus = "00";
        } catch (Exception e) {
            wsPostStatus = "30";
            System.out.println("TXNPOST OPEN POSTOUT FAILED " + wsPostStatus);
            System.exit(16);
            return;
        }
        p8000Read();
    }

    private void p2000Process() throws Exception {
        wsCharge = new BigDecimal("0.00");
        wsNewBal = storeTrunc(txnBalance, 9, 2);
        if (txnType == 'P') {
            p3000PostPayment();
        } else if (txnType == 'F') {
            BigDecimal v = txnBalance.subtract(txnAmount);
            wsNewBal = storeTrunc(v, 9, 2);
            postStatus = "FE";
        } else if (txnType == 'I') {
            wsCredit = divideRounded(txnAmount, BigDecimal.valueOf(12), 7, 2);
            BigDecimal v = txnBalance.add(wsCredit);
            wsNewBal = storeTrunc(v, 9, 2);
            postStatus = "IC";
        } else {
            postStatus = "RJ";
        }
        if (postStatus.equals("RJ") || postStatus.equals("NS")) {
            wsRejectCnt = (wsRejectCnt + 1) % 10000000;
        } else {
            wsPostedCnt = (wsPostedCnt + 1) % 10000000;
        }
        p5000WriteResult();
        p8000Read();
    }

    private void p3000PostPayment() {
        if (txnAmount.compareTo(BigDecimal.ZERO) <= 0) {
            postStatus = "RJ";
        } else {
            BigDecimal prod = txnAmount.multiply(wsSurchargeRate);
            wsCharge = storeTrunc(prod, 3, 2);
            if (wsCharge.compareTo(wsSurchargeCap) > 0) {
                wsCharge = new BigDecimal("15.00");
            }
            BigDecimal v = txnBalance.subtract(txnAmount).subtract(wsCharge);
            wsNewBal = storeTrunc(v, 9, 2);
            BigDecimal od = BigDecimal.valueOf((long) txnOdLimit).setScale(2);
            BigDecimal f = BigDecimal.ZERO.subtract(od);
            wsFloor = storeTrunc(f, 9, 2);
            if (wsNewBal.compareTo(wsFloor) < 0) {
                wsNewBal = storeTrunc(txnBalance, 9, 2);
                wsCharge = new BigDecimal("0.00");
                postStatus = "NS";
            } else {
                postStatus = "OK";
            }
        }
    }

    private void p5000WriteResult() throws Exception {
        postTxnId = txnId;
        postChargeVal = storeTrunc(wsCharge, 3, 2);
        postNewBalVal = storeTrunc(wsNewBal, 9, 2);
        String id = postTxnId;
        if (id.length() < 9) {
            id = id + " ".repeat(9 - id.length());
        } else if (id.length() > 9) {
            id = id.substring(0, 9);
        }
        String chargeStr = formatSignedSeparate(postChargeVal, 3, 2);
        String balStr = formatSignedSeparate(postNewBalVal, 9, 2);
        String rec = id + chargeStr + balStr + postStatus;
        byte[] b = rec.getBytes(StandardCharsets.ISO_8859_1);
        postOut.write(b);
        postOut.write('\n');
    }

    private void p8000Read() throws Exception {
        String line = txnReader.readLine();
        if (line == null) {
            endOfTxns = true;
            return;
        }
        if (line.length() < 37) {
            line = line + " ".repeat(37 - line.length());
        } else if (line.length() > 37) {
            line = line.substring(0, 37);
        }
        txnId = line.substring(0, 9);
        txnType = line.charAt(9);
        String amountStr = line.substring(10, 20);
        String balanceStr = line.substring(20, 32);
        String odStr = line.substring(32, 37);
        txnAmount = parseSignedSeparate(amountStr, 2);
        txnBalance = parseSignedSeparate(balanceStr, 2);
        txnOdLimit = parseUnsigned(odStr);
    }

    private void p9000Term() throws Exception {
        try {
            if (txnReader != null) {
                txnReader.close();
            }
        } catch (Exception e) {
        }
        try {
            if (postOut != null) {
                postOut.flush();
                postOut.close();
            }
        } catch (Exception e) {
        }
        String posted = String.format("%07d", wsPostedCnt);
        String rejected = String.format("%07d", wsRejectCnt);
        System.out.println("TXNPOST POSTED=" + posted + " REJECTED=" + rejected);
    }

    private static BigDecimal storeTrunc(BigDecimal v, int intDigits, int fracDigits) {
        BigDecimal s = v.setScale(fracDigits, RoundingMode.DOWN);
        BigDecimal m = BigDecimal.TEN.pow(intDigits);
        BigDecimal r = s.remainder(m);
        return r.setScale(fracDigits, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal divideRounded(BigDecimal num, BigDecimal den, int intDigits, int fracDigits) {
        BigDecimal q = num.divide(den, fracDigits, RoundingMode.HALF_UP);
        BigDecimal m = BigDecimal.TEN.pow(intDigits);
        BigDecimal r = q.remainder(m);
        return r.setScale(fracDigits, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal parseSignedSeparate(String field, int fracDigits) {
        if (field.isEmpty()) {
            return BigDecimal.ZERO.setScale(fracDigits);
        }
        char c0 = field.charAt(0);
        if (c0 == '+' || c0 == '-') {
            String digits = field.substring(1);
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
                sb.append('0');
            }
            BigDecimal unscaled = new BigDecimal(sb.toString());
            BigDecimal v = unscaled.movePointLeft(fracDigits);
            if (c0 == '-') {
                v = v.negate();
            }
            return v.setScale(fracDigits, RoundingMode.UNNECESSARY);
        } else {
            StringBuilder sb = new StringBuilder();
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
            if (sb.length() == 0) {
                sb.append('0');
            }
            BigDecimal unscaled = new BigDecimal(sb.toString());
            BigDecimal v = unscaled.movePointLeft(fracDigits);
            return v.setScale(fracDigits, RoundingMode.UNNECESSARY);
        }
    }

    private static int parseUnsigned(String field) {
        String t = field.trim();
        if (t.isEmpty()) {
            return 0;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            }
        }
        if (sb.length() == 0) {
            return 0;
        }
        try {
            return Integer.parseInt(sb.toString());
        } catch (Exception e) {
            BigInteger bi = new BigInteger(sb.toString());
            bi = bi.remainder(BigInteger.valueOf(100000));
            return bi.intValue();
        }
    }

    private static String formatSignedSeparate(BigDecimal v, int intDigits, int fracDigits) {
        boolean neg = v.signum() < 0;
        BigDecimal a = v.abs().setScale(fracDigits, RoundingMode.UNNECESSARY);
        BigInteger unscaled = a.movePointRight(fracDigits).toBigIntegerExact();
        String d = unscaled.toString();
        int total = intDigits + fracDigits;
        if (d.length() < total) {
            d = "0".repeat(total - d.length()) + d;
        } else if (d.length() > total) {
            d = d.substring(d.length() - total);
        }
        char s = neg ? '-' : '+';
        return s + d;
    }
}
