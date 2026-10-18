package io.github.chrisoo.expy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a payment notification (Google Wallet, a bank app) and extracts the amount and, when it can, the merchant.
 * Pure Java so it can be tested without Android. Canadian formats: "12,34 $", "$12.34", "1 234,56 $", "CAD 12.34".
 */
public final class PayParser {
    public static final String WALLET = "com.google.android.apps.walletnfcrel";

    public static final class Result {
        public final long cents;
        public final String merchant;
        Result(long cents, String merchant) { this.cents = cents; this.merchant = merchant; }
    }

    private static final String NUM = "(\\d{1,3}(?:[ \\u00A0\\u202F.,]\\d{3})+(?:[.,]\\d{2})?|\\d+(?:[.,]\\d{2})?)";
    private static final Pattern AMOUNT = Pattern.compile(
        "(?:(?:CA|C|US)?\\$|CAD)\\s?" + NUM + "|" + NUM + "\\s?(?:\\$|CAD)");
    private static final Pattern PAYMENT = Pattern.compile(
        "(?iu)(achat|paiement|pay[ée]|transaction|d[ée]bit|d[ée]pense|purchase|paid|payment|spent|charged|approuv|approved|autoris|authori[sz])");
    private static final Pattern NOT_SPENDING = Pattern.compile(
        "(?iu)(rembours|refund|d[ée]p[ôo]t|deposit|virement re[çc]u|re[çc]u de|received|solde|balance|limite|limit|[ée]ch[ée]ance|due|relev[ée]|statement|refus[ée]|declined)");
    private static final Pattern MERCHANT = Pattern.compile(
        "(?iu)(?:\\bchez|\\bat|\\bà|\\bau|@)\\s+([^\\n,!:;]{2,40}?)(?=\\s+(?:avec|with|le|on|pour|for|par|using|sur)\\b|\\s*(?:[,!:;\\n]|\\.(?=\\s|$))|$)");

    private PayParser() {}

    public static Result parse(String pkg, String title, String text) {
        String t = title == null ? "" : title.trim(), x = text == null ? "" : text.trim();
        String all = (t + "\n" + x).trim();
        if (all.isEmpty()) return null;
        boolean wallet = WALLET.equals(pkg);
        if (NOT_SPENDING.matcher(all).find()) return null;
        if (!wallet && !PAYMENT.matcher(all).find()) return null;
        Matcher m = AMOUNT.matcher(all);
        if (!m.find()) return null;
        long cents = toCents(m.group(1) != null ? m.group(1) : m.group(2));
        if (cents <= 0 || cents > 5_000_000) return null; // ignore 0 and anything above 50 000 $
        return new Result(cents, merchant(t, x, wallet));
    }

    static long toCents(String raw) {
        String s = raw.replaceAll("[ \\u00A0\\u202F]", "");
        int dec = -1;
        int i = Math.max(s.lastIndexOf('.'), s.lastIndexOf(','));
        if (i >= 0 && s.length() - i - 1 == 2) dec = i;
        String whole = (dec >= 0 ? s.substring(0, dec) : s).replaceAll("[.,]", "");
        String frac = dec >= 0 ? s.substring(dec + 1) : "00";
        try { return Long.parseLong(whole) * 100 + Long.parseLong(frac); } catch (NumberFormatException e) { return -1; }
    }

    static String merchant(String title, String text, boolean wallet) {
        Matcher m = MERCHANT.matcher(text + "\n" + title);
        if (m.find()) return clean(m.group(1));
        // Google Wallet titles its notification with the merchant name.
        if (wallet && !title.isEmpty() && !title.matches(".*\\d.*") && title.length() <= 40) return clean(title);
        return null;
    }

    private static String clean(String s) {
        String c = s.replaceAll("\\s+", " ").trim();
        if (c.isEmpty()) return null;
        return c.length() > 40 ? c.substring(0, 40) : c;
    }

    // Small self-test, run with: java PayParser.java (outside Android).
    public static void main(String[] a) {
        String[][] cases = {
            {WALLET, "Metro", "12,34 $ avec Visa ••1234", "1234|Metro"},
            {WALLET, "Couche-Tard", "$5.67 with Mastercard", "567|Couche-Tard"},
            {"com.bank", "Achat approuvé", "Achat de 45,00 $ chez IGA Extra le 18 oct.", "4500|IGA Extra"},
            {"com.bank", "Transaction", "Your card was charged $1,234.56 at Amazon.ca.", "123456|Amazon.ca"},
            {"com.bank", "Alerte", "Paiement de 1 234,56 $ effectué au Dépanneur Chez Paul", "123456|Dépanneur Chez Paul"},
            {"com.bank", "Dépôt", "Dépôt de 2 000,00 $ reçu", "null"},
            {"com.bank", "Solde", "Votre solde est de 342,10 $", "null"},
            {"com.bank", "Promo", "Gagnez 50 $ en points!", "null"},
            {"com.bank", "Remboursement", "Remboursement de 20,00 $ chez Metro", "null"},
            {WALLET, "Paiement refusé", "12,00 $", "null"},
            {"com.bank", "Purchase", "CAD 8.99 spent at Netflix", "899|Netflix"},
        };
        int fail = 0;
        for (String[] c : cases) {
            Result r = parse(c[0], c[1], c[2]);
            String got = r == null ? "null" : r.cents + "|" + r.merchant;
            boolean ok = got.equals(c[3]);
            if (!ok) fail++;
            System.out.println((ok ? "OK   " : "FAIL ") + c[1] + " / " + c[2] + " -> " + got + (ok ? "" : "  (attendu " + c[3] + ")"));
        }
        System.out.println(fail == 0 ? "Tous les cas passent." : fail + " cas en échec.");
        if (fail > 0) System.exit(1);
    }
}
