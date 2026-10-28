public class Feeschd {
    private static java.io.BufferedReader feeReader;
    private static java.io.BufferedWriter chgWriter;
    private static String wsFeeStatus = "00";
    private static String wsChgStatus = "00";
    private static boolean eofFlag = false;
    private static java.math.BigDecimal wsWaiverBal = new java.math.BigDecimal("5000.00");
    private static java.math.BigDecimal wsPerTxn = new java.math.BigDecimal("0.35");
    private static java.math.BigDecimal wsOdRate = new java.math.BigDecimal("0.1899");
    private static java.math.BigDecimal wsCap = new java.math.BigDecimal("40.00");
    private static java.math.BigDecimal wsBase = new java.math.BigDecimal("0.00");
    private static int wsFreeTxns = 0;
    private static int wsExcess = 0;
    private static java.math.BigDecimal wsTxnFee = new java.math.BigDecimal("0.00");
    private static java.math.BigDecimal wsOdInt = new java.math.BigDecimal("0.00");
    private static java.math.BigDecimal wsTotal = new java.math.BigDecimal("0.00");
    private static String wsCode = "OK";
    private static long wsReadCnt = 0;
    private static long wsCappedCnt = 0;
    private static String feeAcctId = "          ";
    private static String feeProduct = " ";
    private static java.math.BigDecimal feeAvgBal = new java.math.BigDecimal("0.00");
    private static int feeTxnCount = 0;
    private static int feeMonthDays = 0;
    private static final java.math.BigDecimal ZERO2 = new java.math.BigDecimal("0.00");
    public static void main(String[] args) throws Exception {
        p0000Main();
    }
    private static void p0000Main() throws Exception {
        p1000Init();
        while (!eofFlag) {
            p2000Process();
        }
        p9000Term();
    }
    private static void p1000Init() throws Exception {
        String feePath = System.getenv("DD_FEEIN");
        if (feePath == null) feePath = "FEEIN";
        String chgPath = System.getenv("DD_CHGOUT");
        if (chgPath == null) chgPath = "CHGOUT";
        try {
            feeReader = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(feePath), java.nio.charset.StandardCharsets.ISO_8859_1));
            wsFeeStatus = "00";
        } catch (java.io.IOException e) {
            wsFeeStatus = "35";
            System.out.println("FEESCHD OPEN FEEIN FAILED " + wsFeeStatus);
            System.exit(16);
            return;
        }
        try {
            chgWriter = new java.io.BufferedWriter(new java.io.OutputStreamWriter(new java.io.FileOutputStream(chgPath), java.nio.charset.StandardCharsets.ISO_8859_1));
            wsChgStatus = "00";
        } catch (java.io.IOException e) {
            wsChgStatus = "35";
            System.out.println("FEESCHD OPEN CHGOUT FAILED " + wsChgStatus);
            try { feeReader.close(); } catch (Exception ex) {}
            System.exit(16);
            return;
        }
        p8000Read();
    }
    private static void p2000Process() throws Exception {
        wsReadCnt = (wsReadCnt + 1) % 10000000L;
        wsCode = "OK";
        p3000BaseFee();
        p4000TxnFee();
        p5000OdInterest();
        p6000Total();
        p7000WriteResult();
        p8000Read();
    }
    private static void p3000BaseFee() {
        if (feeProduct.equals("A")) {
            wsBase = new java.math.BigDecimal("6.00");
            wsFreeTxns = 10;
        } else if (feeProduct.equals("B")) {
            wsBase = new java.math.BigDecimal("3.50");
            wsFreeTxns = 25;
        } else {
            wsBase = new java.math.BigDecimal("0.00");
            wsFreeTxns = 9999;
        }
        if (feeAvgBal.compareTo(wsWaiverBal) >= 0 && wsBase.compareTo(java.math.BigDecimal.ZERO) > 0) {
            wsBase = new java.math.BigDecimal("0.00");
            wsCode = "WV";
        }
    }
    private static void p4000TxnFee() {
        long raw = (long) feeTxnCount - (long) wsFreeTxns;
        long mod = 100000L;
        long r = raw % mod;
        wsExcess = (int) r;
        if (wsExcess > 0) {
            java.math.BigDecimal prod = java.math.BigDecimal.valueOf((long) wsExcess).multiply(wsPerTxn);
            wsTxnFee = storeTrunc(prod, 3, 2);
        } else {
            wsTxnFee = new java.math.BigDecimal("0.00");
        }
    }
    private static void p5000OdInterest() {
        if (feeAvgBal.compareTo(java.math.BigDecimal.ZERO) < 0) {
            java.math.BigDecimal negBal = feeAvgBal.negate();
            java.math.BigDecimal prod = negBal.multiply(wsOdRate).multiply(java.math.BigDecimal.valueOf((long) feeMonthDays));
            java.math.BigDecimal high = prod.divide(new java.math.BigDecimal(365), 30, java.math.RoundingMode.HALF_UP);
            wsOdInt = storeRounded(high, 5, 2);
        } else {
            wsOdInt = new java.math.BigDecimal("0.00");
        }
    }
    private static void p6000Total() {
        java.math.BigDecimal sum = wsBase.add(wsTxnFee).add(wsOdInt);
        wsTotal = storeTrunc(sum, 3, 2);
        if (!feeProduct.equals("C") && wsTotal.compareTo(wsCap) > 0) {
            wsTotal = wsCap;
            wsCode = "CP";
            wsCappedCnt = (wsCappedCnt + 1) % 10000000L;
        }
    }
    private static void p7000WriteResult() throws Exception {
        String baseStr = formatSigned(wsBase, 3, 2);
        String txnStr = formatSigned(wsTxnFee, 3, 2);
        String odStr = formatSigned(wsOdInt, 5, 2);
        String totStr = formatSigned(wsTotal, 3, 2);
        String acct = feeAcctId;
        if (acct.length() > 10) acct = acct.substring(0, 10);
        else if (acct.length() < 10) acct = acct + "          ".substring(0, 10 - acct.length());
        String code = wsCode;
        if (code.length() > 2) code = code.substring(0, 2);
        else if (code.length() < 2) code = (code + "  ").substring(0, 2);
        String rec = acct + baseStr + txnStr + odStr + totStr + code;
        chgWriter.write(rec);
        chgWriter.write("\n");
    }
    private static void p8000Read() throws Exception {
        String line;
        try {
            line = feeReader.readLine();
        } catch (java.io.IOException e) {
            eofFlag = true;
            return;
        }
        if (line == null) {
            eofFlag = true;
            return;
        }
        if (line.length() > 29) line = line.substring(0, 29);
        else if (line.length() < 29) {
            StringBuilder sb = new StringBuilder(line);
            while (sb.length() < 29) sb.append(' ');
            line = sb.toString();
        }
        feeAcctId = line.substring(0, 10);
        feeProduct = line.substring(10, 11);
        String balStr = line.substring(11, 23);
        String txnStr = line.substring(23, 27);
        String daysStr = line.substring(27, 29);
        feeAvgBal = parseSignedSeparate(balStr, 9, 2);
        feeTxnCount = parseUnsigned(txnStr);
        feeMonthDays = parseUnsigned(daysStr);
    }
    private static void p9000Term() throws Exception {
        try { if (feeReader != null) feeReader.close(); } catch (Exception e) {}
        try { if (chgWriter != null) chgWriter.close(); } catch (Exception e) {}
        String rc = String.format("%07d", wsReadCnt);
        String cc = String.format("%07d", wsCappedCnt);
        System.out.println("FEESCHD ACCOUNTS=" + rc + " CAPPED=" + cc);
    }
    private static java.math.BigDecimal parseSignedSeparate(String f, int intD, int decD) {
        if (f.trim().isEmpty()) return new java.math.BigDecimal("0.00");
        char c0 = f.charAt(0);
        int sign = 1;
        String numPart;
        if (c0 == '+' || c0 == '-') {
            sign = (c0 == '-' ? -1 : 1);
            numPart = f.substring(1);
        } else if (c0 == ' ') {
            sign = 1;
            numPart = f.substring(1);
        } else {
            sign = 1;
            numPart = f;
        }
        String t = numPart.trim();
        if (t.isEmpty()) return new java.math.BigDecimal("0.00");
        StringBuilder dig = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c >= '0' && c <= '9') dig.append(c);
            else if (c == ' ') continue;
            else if (c == '+' || c == '-') continue;
        }
        if (dig.length() == 0) return new java.math.BigDecimal("0.00");
        java.math.BigInteger bi = new java.math.BigInteger(dig.toString());
        if (sign < 0) bi = bi.negate();
        return new java.math.BigDecimal(bi, decD);
    }
    private static int parseUnsigned(String f) {
        String t = f.trim();
        if (t.isEmpty()) return 0;
        StringBuilder dig = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c >= '0' && c <= '9') dig.append(c);
        }
        if (dig.length() == 0) return 0;
        try { return Integer.parseInt(dig.toString()); } catch (NumberFormatException e) { return 0; }
    }
    private static java.math.BigDecimal storeTrunc(java.math.BigDecimal v, int intD, int decD) {
        java.math.BigDecimal t = v.setScale(decD, java.math.RoundingMode.DOWN);
        return truncHigh(t, intD, decD);
    }
    private static java.math.BigDecimal storeRounded(java.math.BigDecimal v, int intD, int decD) {
        java.math.BigDecimal t = v.setScale(decD, java.math.RoundingMode.HALF_UP);
        return truncHigh(t, intD, decD);
    }
    private static java.math.BigDecimal truncHigh(java.math.BigDecimal t, int intD, int decD) {
        java.math.BigInteger bi = t.unscaledValue();
        int total = intD + decD;
        java.math.BigInteger mod = java.math.BigInteger.TEN.pow(total);
        java.math.BigInteger r = bi.remainder(mod);
        return new java.math.BigDecimal(r, decD);
    }
    private static String formatSigned(java.math.BigDecimal v, int intD, int decD) {
        java.math.BigDecimal t = v.setScale(decD, java.math.RoundingMode.DOWN);
        int sign = t.signum();
        java.math.BigInteger abs = t.unscaledValue().abs();
        String digits = abs.toString();
        int total = intD + decD;
        if (digits.length() > total) digits = digits.substring(digits.length() - total);
        else if (digits.length() < total) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < total - digits.length(); i++) sb.append('0');
            sb.append(digits);
            digits = sb.toString();
        }
        char s = (sign < 0 ? '-' : '+');
        return s + digits;
    }
}
