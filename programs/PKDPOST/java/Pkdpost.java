import java.math.BigDecimal;
public class Pkdpost {
  private byte[] inBytes = new byte[0];
  private int inPos = 0;
  private java.io.OutputStream poOut;
  private boolean endOfAccounts = false;
  private int wsReadCnt = 0;
  private BigDecimal wsInt = BigDecimal.ZERO;
  private BigDecimal wsNewBal = BigDecimal.ZERO;
  private byte[] curPkAcct = new byte[10];
  private boolean havePk = false;
  private BigDecimal pkBal = BigDecimal.ZERO;
  private BigDecimal pkRate = BigDecimal.ZERO;
  private int pkDays = 0;
  private BigDecimal pkAdj = BigDecimal.ZERO;
  private BigDecimal pkLimit = BigDecimal.ZERO;
  private int pkItems = 0;

  public static void main(String[] args) throws Exception {
    Pkdpost p = new Pkdpost();
    p.p0000Main();
  }

  private void p0000Main() throws Exception {
    String inPath = System.getenv("DD_PKIN");
    String outPath = System.getenv("DD_PKOUT");
    if (inPath != null) {
      try { inBytes = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(inPath)); }
      catch (java.nio.file.NoSuchFileException e) { inBytes = new byte[0]; }
    }
    inPos = 0;
    java.io.OutputStream out = null;
    if (outPath != null) {
      out = java.nio.file.Files.newOutputStream(java.nio.file.Paths.get(outPath),
        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING, java.nio.file.StandardOpenOption.WRITE);
    } else {
      out = new java.io.ByteArrayOutputStream();
    }
    poOut = out;
    endOfAccounts = false;
    wsReadCnt = 0;
    p8000Read();
    while (!endOfAccounts) {
      p2000Post();
    }
    poOut.close();
    String cnt = String.format("%07d", wsReadCnt % 10000000);
    System.out.println("PKDPOST READ=" + cnt);
  }

  private void p2000Post() throws Exception {
    wsReadCnt = (wsReadCnt + 1) % 10000000;
    java.math.BigInteger balScaled = pkBal.movePointRight(2).toBigIntegerExact();
    java.math.BigInteger rateScaled = pkRate.movePointRight(4).toBigIntegerExact();
    java.math.BigInteger n = balScaled.multiply(rateScaled).multiply(java.math.BigInteger.valueOf((long) pkDays));
    BigDecimal rawInt = new BigDecimal(n).divide(new BigDecimal(365000000L), 2, java.math.RoundingMode.HALF_UP);
    BigDecimal wsIntTr = truncateOverflow(rawInt, 7, 2);
    wsInt = wsIntTr;
    BigDecimal sum = pkBal.add(wsInt).add(pkAdj);
    wsNewBal = truncateOverflow(sum, 9, 2);
    byte[] poAcct = java.util.Arrays.copyOf(curPkAcct, 10);
    BigDecimal poIntV = wsInt;
    BigDecimal poNewBalV = wsNewBal;
    BigDecimal headRaw = pkLimit.subtract(wsNewBal);
    BigDecimal poHeadroomV = truncateOverflow(headRaw, 7, 2);
    long rawUnits = (long) pkItems * 1000L;
    long unitsTr = rawUnits % 1000000000L;
    int poItemUnitsV = (int) unitsTr;
    BigDecimal poReversalV = pkAdj.negate();
    poReversalV = truncateOverflow(poReversalV, 5, 2);
    byte[] rec = new byte[42];
    System.arraycopy(poAcct, 0, rec, 0, 10);
    packComp3(rec, 10, 5, poIntV, 7, 2, true);
    packComp3(rec, 15, 6, poNewBalV, 9, 2, true);
    packTrailingSeparate(rec, 21, poHeadroomV, 7, 2);
    putBigEndian(rec, 31, 4, poItemUnitsV);
    packOverpunch(rec, 35, 7, poReversalV, 5, 2);
    poOut.write(rec);
    p8000Read();
  }

  private void p8000Read() {
    if (inPos + 33 <= inBytes.length) {
      int o = inPos;
      inPos += 33;
      System.arraycopy(inBytes, o, curPkAcct, 0, 10);
      havePk = true;
      pkBal = unpackComp3(inBytes, o + 10, 6, 9, 2, true);
      pkRate = unpackComp3(inBytes, o + 16, 3, 1, 4, false);
      pkDays = getBigEndian(inBytes, o + 19, 2);
      pkAdj = unpackOverpunch(inBytes, o + 21, 7, 5, 2);
      pkLimit = unpackPscaled(inBytes, o + 28, 3, 2);
      pkItems = getLittleEndian(inBytes, o + 31, 2);
    } else {
      endOfAccounts = true;
      havePk = false;
    }
  }

  private static BigDecimal truncateOverflow(BigDecimal v, int intDigits, int scale) {
    BigDecimal s = v.setScale(scale, java.math.RoundingMode.DOWN);
    java.math.BigInteger scaled = s.movePointRight(scale).toBigIntegerExact();
    java.math.BigInteger mod = java.math.BigInteger.TEN.pow(intDigits + scale);
    java.math.BigInteger r = scaled.remainder(mod);
    return new BigDecimal(r, scale);
  }

  private static int nibble(byte[] d, int off, int idx) {
    int b = d[off + idx / 2] & 0xFF;
    if ((idx & 1) == 0) return (b >> 4) & 0x0F;
    else return b & 0x0F;
  }

  private static BigDecimal unpackComp3(byte[] d, int off, int len, int intDigits, int scale, boolean signed) {
    int totalNibbles = len * 2;
    int sign = d[off + len - 1] & 0x0F;
    boolean negative = false;
    if (signed) negative = (sign == 0x0D);
    int totalDigits = intDigits + scale;
    StringBuilder sb = new StringBuilder(totalDigits);
    int start = totalNibbles - 1 - totalDigits;
    for (int i = 0; i < totalDigits; i++) sb.append((char) ('0' + nibble(d, off, start + i)));
    java.math.BigInteger mag = new java.math.BigInteger(sb.toString());
    if (negative) mag = mag.negate();
    return new BigDecimal(mag, scale);
  }

  private static void packComp3(byte[] d, int off, int len, BigDecimal v, int intDigits, int scale, boolean signed) {
    BigDecimal s = v.setScale(scale, java.math.RoundingMode.DOWN);
    boolean negative = s.signum() < 0;
    if (s.signum() == 0) {
      try { negative = s.toPlainString().startsWith("-"); } catch (Exception e) { negative = false; }
    }
    java.math.BigInteger scaled = s.movePointRight(scale).toBigIntegerExact().abs();
    int totalDigits = intDigits + scale;
    String str = scaled.toString();
    if (str.length() > totalDigits) str = str.substring(str.length() - totalDigits);
    StringBuilder pad = new StringBuilder();
    for (int i = str.length(); i < totalDigits; i++) pad.append('0');
    pad.append(str);
    String digits = pad.toString();
    int totalNibbles = len * 2;
    int[] nb = new int[totalNibbles];
    int signNib;
    if (!signed) signNib = 0x0F;
    else signNib = negative ? 0x0D : 0x0C;
    nb[totalNibbles - 1] = signNib;
    for (int i = 0; i < totalDigits; i++) nb[totalNibbles - 2 - (totalDigits - 1 - i)] = digits.charAt(i) - '0';
    for (int i = 0; i < len; i++) d[off + i] = (byte) ((nb[2 * i] << 4) | nb[2 * i + 1]);
  }

  private static int getBigEndian(byte[] d, int off, int len) {
    if (len == 2) {
      int v = ((d[off] & 0xFF) << 8) | (d[off + 1] & 0xFF);
      if ((v & 0x8000) != 0) v -= 0x10000;
      return v;
    } else {
      int v = ((d[off] & 0xFF) << 24) | ((d[off + 1] & 0xFF) << 16) | ((d[off + 2] & 0xFF) << 8) | (d[off + 3] & 0xFF);
      return v;
    }
  }

  private static void putBigEndian(byte[] d, int off, int len, int v) {
    if (len == 2) {
      d[off] = (byte) ((v >> 8) & 0xFF);
      d[off + 1] = (byte) (v & 0xFF);
    } else {
      d[off] = (byte) ((v >> 24) & 0xFF);
      d[off + 1] = (byte) ((v >> 16) & 0xFF);
      d[off + 2] = (byte) ((v >> 8) & 0xFF);
      d[off + 3] = (byte) (v & 0xFF);
    }
  }

  private static int getLittleEndian(byte[] d, int off, int len) {
    int v = 0;
    for (int i = len - 1; i >= 0; i--) v = (v << 8) | (d[off + i] & 0xFF);
    if (len == 2 && (v & 0x8000) != 0) v -= 0x10000;
    return v;
  }

  private static BigDecimal unpackOverpunch(byte[] d, int off, int len, int intDigits, int scale) {
    int totalDigits = intDigits + scale;
    StringBuilder sb = new StringBuilder(totalDigits);
    boolean negative = false;
    for (int i = 0; i < len; i++) {
      int b = d[off + i] & 0xFF;
      if (i == len - 1) {
        if (b >= '0' && b <= '9') sb.append((char) b);
        else if ((b >= 'p' && b <= 'y') || (b >= 'P' && b <= 'Y')) {
          int lower = b | 0x20;
          sb.append((char) (lower - 0x40));
          negative = true;
        } else if (b == '}' || b == 'J' || b == 'K' || b == 'L' || b == 'M' || b == 'N' || b == 'O' || b == 'P' || b == 'Q' || b == 'R') {
          char[] map = {'0','1','2','3','4','5','6','7','8','9'};
          int idx = -1;
          char[] neg = {'}', 'J','K','L','M','N','O','P','Q','R'};
          for (int k = 0; k < neg.length; k++) if (b == neg[k]) idx = k;
          sb.append(map[idx]);
          negative = true;
        } else if (b == '{' || (b >= 'A' && b <= 'I')) {
          char[] pos = {'{','A','B','C','D','E','F','G','H','I'};
          char[] map = {'0','1','2','3','4','5','6','7','8','9'};
          int idx = 0;
          for (int k = 0; k < pos.length; k++) if (b == pos[k]) idx = k;
          sb.append(map[idx]);
        } else {
          sb.append((char) b);
        }
      } else {
        if (b == ' ') sb.append('0');
        else sb.append((char) b);
      }
    }
    java.math.BigInteger mag = new java.math.BigInteger(sb.toString());
    if (negative) mag = mag.negate();
    return new BigDecimal(mag, scale);
  }

  private static void packOverpunch(byte[] d, int off, int len, BigDecimal v, int intDigits, int scale) {
    BigDecimal s = v.setScale(scale, java.math.RoundingMode.DOWN);
    boolean negative = s.signum() < 0;
    if (s.signum() == 0) {
      try { negative = s.toPlainString().startsWith("-"); } catch (Exception e) { negative = false; }
    }
    java.math.BigInteger scaled = s.movePointRight(scale).toBigIntegerExact().abs();
    int totalDigits = intDigits + scale;
    String str = scaled.toString();
    if (str.length() > totalDigits) str = str.substring(str.length() - totalDigits);
    StringBuilder pad = new StringBuilder();
    for (int i = str.length(); i < totalDigits; i++) pad.append('0');
    pad.append(str);
    String digits = pad.toString();
    for (int i = 0; i < len - 1; i++) d[off + i] = (byte) digits.charAt(i);
    char last = digits.charAt(totalDigits - 1);
    if (negative) d[off + len - 1] = (byte) (last + 0x40);
    else d[off + len - 1] = (byte) last;
  }

  private static BigDecimal unpackPscaled(byte[] d, int off, int len, int pCount) {
    StringBuilder sb = new StringBuilder(len);
    for (int i = 0; i < len; i++) sb.append((char) (d[off + i] & 0xFF));
    java.math.BigInteger mag = new java.math.BigInteger(sb.toString());
    BigDecimal v = new BigDecimal(mag);
    for (int i = 0; i < pCount; i++) v = v.multiply(BigDecimal.TEN);
    return v;
  }

  private static void packTrailingSeparate(byte[] d, int off, BigDecimal v, int intDigits, int scale) {
    BigDecimal s = v.setScale(scale, java.math.RoundingMode.DOWN);
    boolean negative = s.signum() < 0;
    if (s.signum() == 0) {
      try { negative = s.toPlainString().startsWith("-"); } catch (Exception e) { negative = false; }
    }
    java.math.BigInteger scaled = s.movePointRight(scale).toBigIntegerExact().abs();
    int totalDigits = intDigits + scale;
    String str = scaled.toString();
    if (str.length() > totalDigits) str = str.substring(str.length() - totalDigits);
    StringBuilder pad = new StringBuilder();
    for (int i = str.length(); i < totalDigits; i++) pad.append('0');
    pad.append(str);
    String digits = pad.toString();
    for (int i = 0; i < totalDigits; i++) d[off + i] = (byte) digits.charAt(i);
    d[off + totalDigits] = (byte) (negative ? '-' : '+');
  }
}
