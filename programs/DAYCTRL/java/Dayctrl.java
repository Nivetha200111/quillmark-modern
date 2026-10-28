import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class Dayctrl {
    private BufferedReader dayReader;
    private BufferedWriter ctlWriter;
    private String wsDayStatus = "00";
    private String wsCtlStatus = "00";
    private boolean endOfAccounts = false;
    private String dayAcctId = "          ";
    private BigDecimal dayPrincipal = new BigDecimal("0.00");
    private BigDecimal dayRate = new BigDecimal("0.0000");
    private BigDecimal dayDays = BigDecimal.ZERO;
    private BigDecimal wsInterest = new BigDecimal("0.00");
    private BigDecimal wsCtlTotal = new BigDecimal("0.00");
    private char ctlFlag = '-';
    private long wsReadCnt = 0;
    private long wsLostCnt = 0;
    private static final BigDecimal MOD_INTEREST = new BigDecimal("10000000");
    private static final BigDecimal MOD_TOTAL = new BigDecimal("100000000000");
    private static final BigDecimal DAYS_365 = new BigDecimal("365");

    public static void main(String[] args) throws Exception {
        Dayctrl prog = new Dayctrl();
        prog.paragraph0000Main();
    }

    private void paragraph0000Main() throws Exception {
        paragraph1000Init();
        while (!endOfAccounts) {
            paragraph2000Process();
        }
        paragraph9000Term();
    }

    private void paragraph1000Init() throws Exception {
        String dayPath = System.getenv("DD_DAYIN");
        String ctlPath = System.getenv("DD_CTLOUT");
        if (dayPath == null) {
            wsDayStatus = "35";
            System.out.print("DAYCTRL OPEN DAYIN FAILED " + wsDayStatus + "\n");
            System.exit(16);
        }
        try {
            dayReader = Files.newBufferedReader(Paths.get(dayPath), StandardCharsets.ISO_8859_1);
            wsDayStatus = "00";
        } catch (Exception e) {
            wsDayStatus = "35";
            System.out.print("DAYCTRL OPEN DAYIN FAILED " + wsDayStatus + "\n");
            System.exit(16);
        }
        if (ctlPath == null) {
            wsCtlStatus = "35";
            System.out.print("DAYCTRL OPEN CTLOUT FAILED " + wsCtlStatus + "\n");
            System.exit(16);
        }
        try {
            ctlWriter = Files.newBufferedWriter(Paths.get(ctlPath), StandardCharsets.ISO_8859_1, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            wsCtlStatus = "00";
        } catch (Exception e) {
            wsCtlStatus = "35";
            System.out.print("DAYCTRL OPEN CTLOUT FAILED " + wsCtlStatus + "\n");
            System.exit(16);
        }
        paragraph8000Read();
    }

    private void paragraph2000Process() throws Exception {
        wsReadCnt = (wsReadCnt + 1) % 10000000L;
        paragraph3000Accrue();
        paragraph5000WriteResult();
        paragraph8000Read();
    }

    private void paragraph3000Accrue() {
        BigDecimal numer = dayPrincipal.multiply(dayRate).multiply(dayDays);
        BigDecimal interest = numer.divide(DAYS_365, 2, RoundingMode.HALF_UP);
        if (interest.abs().compareTo(MOD_INTEREST) >= 0) {
            interest = interest.remainder(MOD_INTEREST);
            interest = interest.setScale(2, RoundingMode.UNNECESSARY);
        }
        wsInterest = interest;
        wsCtlTotal = wsCtlTotal.add(wsInterest);
        if (wsCtlTotal.abs().compareTo(MOD_TOTAL) >= 0) {
            wsCtlTotal = wsCtlTotal.remainder(MOD_TOTAL);
            wsCtlTotal = wsCtlTotal.setScale(2, RoundingMode.UNNECESSARY);
        }
        if (wsInterest.compareTo(BigDecimal.ZERO) == 0
                && dayPrincipal.compareTo(BigDecimal.ZERO) != 0
                && dayRate.compareTo(BigDecimal.ZERO) != 0
                && dayDays.compareTo(BigDecimal.ZERO) != 0) {
            ctlFlag = 'Z';
            wsLostCnt = (wsLostCnt + 1) % 10000000L;
        } else {
            ctlFlag = '-';
        }
    }

    private void paragraph5000WriteResult() throws Exception {
        String acct = dayAcctId;
        if (acct.length() < 10) {
            acct = acct + " ".repeat(10 - acct.length());
        } else if (acct.length() > 10) {
            acct = acct.substring(0, 10);
        }
        String interestField = formatSigned(wsInterest, 7);
        String totalField = formatSigned(wsCtlTotal, 11);
        String rec = acct + interestField + totalField + ctlFlag;
        ctlWriter.write(rec);
        ctlWriter.write("\n");
    }

    private void paragraph8000Read() throws Exception {
        String line = null;
        try {
            line = dayReader.readLine();
        } catch (IOException e) {
            line = null;
        }
        if (line == null) {
            endOfAccounts = true;
            return;
        }
        if (line.length() < 30) {
            line = line + " ".repeat(30 - line.length());
        } else if (line.length() > 30) {
            line = line.substring(0, 30);
        }
        dayAcctId = line.substring(0, 10);
        String principalField = line.substring(10, 22);
        String rateField = line.substring(22, 27);
        String daysField = line.substring(27, 30);
        boolean neg = false;
        String digits;
        char c0 = principalField.charAt(0);
        if (c0 == '-' || c0 == '+') {
            neg = (c0 == '-');
            digits = principalField.substring(1);
        } else {
            digits = principalField;
        }
        digits = digits.trim();
        if (digits.isEmpty()) {
            digits = "0";
        }
        BigDecimal mag = new BigDecimal(digits).movePointLeft(2);
        if (neg) {
            mag = mag.negate();
        }
        dayPrincipal = mag;
        String rs = rateField.trim();
        if (rs.isEmpty()) {
            rs = "0";
        }
        dayRate = new BigDecimal(rs).movePointLeft(4);
        String ds = daysField.trim();
        if (ds.isEmpty()) {
            ds = "0";
        }
        dayDays = new BigDecimal(ds);
    }

    private void paragraph9000Term() throws Exception {
        try {
            if (dayReader != null) {
                dayReader.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (ctlWriter != null) {
                ctlWriter.flush();
                ctlWriter.close();
            }
        } catch (IOException ignored) {
        }
        String edited = formatEdited(wsCtlTotal);
        String readStr = String.format("%010d", Long.valueOf(wsReadCnt));
        String lostStr = String.format("%010d", Long.valueOf(wsLostCnt));
        String msg = "DAYCTRL ACCOUNTS=" + readStr + " LOST=" + lostStr + " CONTROL=" + edited;
        System.out.print(msg + "\n");
    }

    static String formatSigned(BigDecimal v, int intDigits) {
        char sign = v.signum() < 0 ? '-' : '+';
        BigDecimal a = v.abs().setScale(2, RoundingMode.UNNECESSARY);
        String plain = a.toPlainString();
        int dot = plain.indexOf('.');
        String intPart = plain.substring(0, dot);
        String fracPart = plain.substring(dot + 1);
        if (intPart.length() < intDigits) {
            intPart = "0".repeat(intDigits - intPart.length()) + intPart;
        } else if (intPart.length() > intDigits) {
            intPart = intPart.substring(intPart.length() - intDigits);
        }
        if (fracPart.length() < 2) {
            fracPart = fracPart + "0".repeat(2 - fracPart.length());
        }
        return sign + intPart + fracPart;
    }

    static String formatEdited(BigDecimal total) {
        boolean neg = total.signum() < 0;
        BigDecimal a = total.abs().setScale(2, RoundingMode.UNNECESSARY);
        String plain = a.toPlainString();
        int dot = plain.indexOf('.');
        String intPart = plain.substring(0, dot);
        String fracPart = plain.substring(dot + 1);
        String padded;
        if (intPart.length() < 12) {
            padded = "0".repeat(12 - intPart.length()) + intPart;
        } else {
            padded = intPart.substring(intPart.length() - 12);
        }
        char[] arr = padded.toCharArray();
        int first = -1;
        for (int i = 0; i < 12; i++) {
            if (arr[i] != '0') {
                first = i;
                break;
            }
        }
        if (first == -1) {
            first = 11;
        }
        if (neg) {
            if (first > 0) {
                for (int i = 0; i < first - 1; i++) {
                    arr[i] = ' ';
                }
                arr[first - 1] = '-';
            }
        } else {
            for (int i = 0; i < first; i++) {
                arr[i] = ' ';
            }
        }
        return new String(arr) + "." + fracPart;
    }
}
