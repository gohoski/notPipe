package io.github.gohoski.notpipe;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;

import android.content.Context;
import android.content.res.Resources;

import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Gleb on 21.01.2026.
 * Utility methods
 */
public class Utils {
    private static List<String> HYPE_PLAYLISTS = Arrays.asList("OLXuPDPDe3URMWBdmS4_jxdqVF08DAlAXWQ", "OLd7LoUR1ndxIsyTJ_5pK4tvrWBcvkewBLg",
            "OLNUaVf-BhE20xpEEdOmnxF6oyKQwpN1tBQ", "OLUZ2nAWnPVwwoXomaTvbFi1Cje8td4Z0zg", "OLewrgOzMLeOADYzQ1ewNeqtPlJP20RE_Zg",
            "OLbXum44nJ19cZmR7GxDm5rF_Nj_coRoO5g", "OLPPB3977IfFUtPW8213fFOsf2lmAWfahDg", "OLZyZj8vWFMkIOGlFzyKuFlLA2PY42VhRuA");

    private static Boolean isV7 = null;

    public static boolean isV7() {
        if (isV7 == null) {
            try {
                isV7 = "armeabi-v7a".equals(Build.class.getField("CPU_ABI").get(null));
            } catch (Exception ignored) {
                isV7 = false;
            }
        }
        return isV7;
    }

    /**
     * Parses and combines a base URL with a given URL.
     * 
     * Examples:
     * - ("http://0.0.0.0", "/videoplayback?id=abc") -> "http://0.0.0.0/videoplayback?id=abc"
     * - ("http://0.0.0.0", "//yt3.ggpht.com/abc") -> "http://0.0.0.0/abc"
     * - ("http://0.0.0.0", "http://0.0.0.0/abc") -> "http://0.0.0.0/abc"
     * - ("http://0.0.0.0", "https://some.domain/abc") -> "http://0.0.0.0/abc"
     * - ("http://0.0.0.0", "http://128.128.128.128/abc") -> "http://0.0.0.0/abc"
     * - ("http://0.0.0.0", "HTTP://147.45.70.51:8082/videoplayback") -> "http://0.0.0.0/videoplayback"
     * 
     * @param baseUrl The base URL to use
     * @param url The URL to parse and combine
     * @return The combined URL
     */
    public static String parseUrl(String baseUrl, String url) {
        if (url == null) return "";
        if (url.startsWith("//")) {
            url = "https:" + url;
        }
        if (url.startsWith("/")) {
            return baseUrl + url;
        }
        // regionMatches with ignoreCase avoids allocating a lower-cased copy
        // while handling uppercase schemes like "HTTP://147.45.70.51:8082/videoplayback".
        if (url.regionMatches(true, 0, "http://", 0, 7) || url.regionMatches(true, 0, "https://", 0, 8)) {
            int pathStart = url.indexOf('/', url.indexOf("://") + 3);
            if (pathStart != -1) {
                return baseUrl + url.substring(pathStart);
            } else {
                return baseUrl;
            }
        }
        return url;
    }

    /**
     * Formats duration in seconds to a human-readable format.
     * 
     * Examples:
     * - 90 -> "1:30"
     * - 3663 -> "1:01:03"
     * 
     * @param totalSeconds The total duration in seconds
     * @return Formatted duration string
     */
    public static String formatDuration(int totalSeconds) {
        if (totalSeconds < 1) return "";
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;

        if (hours > 0) {
            // Format: H:MM:SS (e.g., 1:52:03)
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            // Format: M:SS (e.g., 1:30)
            return String.format("%d:%02d", minutes, seconds);
        }
    }

    /**
     * Parses an Invidious text count string to an integer.
     *
     * Examples:
     * - "1.29K" -> 1290
     * - "2.44M" -> 2440000
     * - "500" -> 500
     * - "2K" -> 2000
     *
     * @param subscriberCount The subscriber count string (e.g., "1.29K", "2.44M", "500")
     * @return The subscriber count as an integer
     */
    public static long parseTextCount(String subscriberCount) {
        if (subscriberCount == null || subscriberCount.length() == 0) {
            return 0;
        }
        String countStr = subscriberCount.trim();
        int multiplier = 1;
        if (countStr.endsWith("K")) {
            multiplier = 1000;
            countStr = countStr.substring(0, countStr.length() - 1);
        } else if (countStr.endsWith("M")) {
            multiplier = 1000000;
            countStr = countStr.substring(0, countStr.length() - 1);
        } else if (countStr.endsWith("B")) {
            multiplier = 1000000000;
            countStr = countStr.substring(0, countStr.length() - 1);
        }
        try {
            return Math.round(Double.parseDouble(countStr) * multiplier);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static String formatNumber(Context context, long number) {
        long absNumber = Math.abs(number);
        if (absNumber < 1000) return String.valueOf(number);

        long divisor;
        int suffixResId;
        if (absNumber >= 1000000000L) {
            divisor = 1000000000L;
            suffixResId = R.string.billions_suffix;
        } else if (absNumber >= 1000000L) {
            divisor = 1000000L;
            suffixResId = R.string.millions_suffix;
        } else {
            divisor = 1000L;
            suffixResId = R.string.thousands_suffix;
        }

        Resources res = context.getResources();
        StringBuilder sb = new StringBuilder();
        if (number < 0) sb.append('-');

        long whole = absNumber / divisor;
        sb.append(whole);

        if (whole < 10) {
            int frac = (int) (absNumber / (divisor / 100) % 100);
            if (frac > 0) {
                sb.append(res.getString(R.string.decimal_separator))
                        .append(frac / 10);
                if (frac % 10 != 0) {
                    sb.append(frac % 10);
                }
            }
        }

        return sb.append(res.getString(suffixResId)).toString();
    }

    public static Date parseRelativeDate(String relativeDate) {
        if (relativeDate == null) return null;

        String[] words = relativeDate.toLowerCase().trim().replace(" (edited)", "").split("\\s+");
        int len = words.length;
        int start = Math.max(0, len - 3);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < len; i++) {
            sb.append(words[i]).append(" ");
        }
        String input = sb.toString().trim();

        if (input.endsWith("ago"))
            input = input.substring(0, input.length() - 3).trim();
        else if (input.endsWith("назад"))
            input = input.substring(0, input.length() - 5).trim();
        else return null;

        Matcher m = Pattern.compile("^(\\d+)\\s*([a-zа-яё]+)?$").matcher(input);
        if (!m.find()) return null;

        int amount;
        try {
            amount = Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }

        String unit = m.group(2);
        if (unit == null || unit.length() == 0) return null;

        Calendar cal = Calendar.getInstance();
        switch (unit) {
            case "month": case "months": case "месяц": case "месяца": case "месяцев": case "mo":
                cal.add(Calendar.MONTH, -amount); break;
            case "year": case "years": case "год": case "года": case "лет": case "y":
                cal.add(Calendar.YEAR, -amount); break;
            case "day": case "days": case "день": case "дня": case "дней": case "d":
                cal.add(Calendar.DAY_OF_MONTH, -amount); break;
            case "week": case "weeks": case "неделю": case "недели": case "недель": case "w":
                cal.add(Calendar.WEEK_OF_YEAR, -amount); break;
            case "hour": case "hours": case "час": case "часа": case "часов": case "h":
                cal.add(Calendar.HOUR_OF_DAY, -amount); break;
            case "minute": case "minutes": case "минуту": case "минуты": case "минут": case "m":
                cal.add(Calendar.MINUTE, -amount); break;
            default:
                cal.add(Calendar.SECOND, -amount);
        }
        return cal.getTime();
    }

    /**
     * Formats a Date into a time-ago string. Made due to DateUtils unreliability
     *
     * @param context The context for accessing resources
     * @param date The date to format
     * @return A string like "50 seconds ago", "6 minutes ago", "2 years ago"
     */
    public static String formatTimeAgo(Context context, Date date) {
        if (date == null) return "";
        long diff = System.currentTimeMillis() - date.getTime();
        if (diff < 0) diff = 0;

        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        long weeks = days / 7;
        long months = days / 30;
        long years = days / 365;

        Resources res = context.getResources();
        if (years > 0) {
            return res.getQuantityString(R.plurals.years_ago, (int) years, years);
        } else if (months > 0) {
            return res.getQuantityString(R.plurals.months_ago, (int) months, months);
        } else if (weeks > 0) {
            return res.getQuantityString(R.plurals.weeks_ago, (int) weeks, weeks);
        } else if (days > 0) {
            return res.getQuantityString(R.plurals.days_ago, (int) days, days);
        } else if (hours > 0) {
            return res.getQuantityString(R.plurals.hours_ago, (int) hours, hours);
        } else if (minutes > 0) {
            return res.getQuantityString(R.plurals.minutes_ago, (int) minutes, minutes);
        } else if (seconds > 30) {
            return res.getQuantityString(R.plurals.seconds_ago, (int) seconds, seconds);
        } else {
            return context.getString(R.string.just_now);
        }
    }

    public static boolean hasConnection(Context context) {
        if (context == null) return true; // Fallback to avoid breaking
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = cm.getActiveNetworkInfo();
            return netInfo != null && netInfo.isConnected();
        } catch (Exception e) {
            return true; // Ignore if permission missing or service unavailable
        }
    }

    /**
     * Blocks the current thread until the internet is available.
     * Safe to run in AsyncTask doInBackground.
     */
    public static void waitForConnection(Context context) throws IOException {
        if (context == null) return;
        boolean waited = false;

        while (!hasConnection(context)) {
            waited = true;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // If the user closes the app, the AsyncTask gets cancelled and interrupts the sleep
                Thread.currentThread().interrupt();
                throw new IOException("Request cancelled while waiting for network");
            }
        }

        // Wait extra after network is restored
        if (waited) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static String getHypePlaylist() {
        return HYPE_PLAYLISTS.get(new Random().nextInt(HYPE_PLAYLISTS.size()));
    }

    /**
     * Determines whether an API error message indicates YouTube is blocking or
     * rate-limiting the instance (rather than the requested content being unavailable).
     * Such errors should trigger a fallback to another instance instead of being
     * reported as content unavailable.
     *
     * @param message The error message returned by the API
     * @return true if the message suggests the instance itself is blocked
     */
    public static boolean isYouTubeBlockingError(String message) {
        if (message == null) return false;
        return message.contains("bot") || message.contains("protect") || message.contains("page")
                || message.contains("Companion")
                || message.contains("YouTube is currently trying to block");
    }

    /**
     * Cleans HTML description safely and allocation-free.
     * Strips tags, converts br to newlines, expands links as "text (url)",
     * keeps hashtag and timestamp link text as-is, decodes HTML entities.
     *
     * @param html The HTML text to clean
     * @return The cleaned text
     */
    public static String cleanHtml(String html) {
        if (html == null) return null;

        StringBuilder sb = new StringBuilder();
        int i = 0;
        int len = html.length();

        while (i < len) {
            int tagStart = html.indexOf('<', i);
            if (tagStart == -1) {
                sb.append(html, i, len); // Efficient append without substring allocation
                break;
            }

            sb.append(html, i, tagStart);

            int tagEnd = html.indexOf('>', tagStart);
            if (tagEnd == -1) {
                sb.append(html, tagStart, len);
                break;
            }

            // Case-insensitive comparisons matching starting tags directly within parent string to avoid allocations
            if (html.regionMatches(true, tagStart, "<br", 0, 3)) {
                sb.append("\n");
                i = tagEnd + 1;
            } else if (html.regionMatches(true, tagStart, "<a", 0, 2)) {
                int closeTagStart = indexOfIgnoreCase(html, "</a>", tagEnd + 1, len);
                if (closeTagStart == -1) {
                    i = tagEnd + 1;
                    continue;
                }

                String href = extractAttribute(html, tagStart, tagEnd + 1, "href");
                String innerText = html.substring(tagEnd + 1, closeTagStart);

                // Recursively clean inner text
                innerText = cleanHtml(innerText);

                if (href != null && href.length() > 0) {
                    href = decodeHtmlEntities(href);

                    boolean isHashtag = href.contains("/hashtag/");
                    boolean isTimestamp = (href.contains("youtube.com") || href.contains("youtu.be"))
                            && (href.contains("t=") || href.contains("time_continue="));

                    if (isHashtag || isTimestamp) {
                        sb.append(innerText);
                    } else {
                        String cleanInnerText = innerText.trim();
                        if (isUrlOrTruncated(cleanInnerText)) {
                            sb.append(href);
                        } else {
                            sb.append(cleanInnerText).append(" (").append(href).append(")");
                        }
                    }
                } else {
                    sb.append(innerText);
                }

                i = closeTagStart + 4; // Skip past </a>
            } else {
                i = tagEnd + 1; // Strip other html tags
            }
        }

        return decodeHtmlEntities(sb.toString());
    }

    private static String extractAttribute(String html, int start, int end, String attr) {
        String attrMatch = attr + "=";
        int idx = indexOfIgnoreCase(html, attrMatch, start, end);
        if (idx == -1) return null;

        int valStart = idx + attrMatch.length();
        if (valStart >= end) return null;

        char quote = html.charAt(valStart);
        if (quote == '"' || quote == '\'') {
            int valEnd = -1;
            for (int j = valStart + 1; j < end; j++) {
                if (html.charAt(j) == quote) {
                    valEnd = j;
                    break;
                }
            }
            if (valEnd != -1) {
                return html.substring(valStart + 1, valEnd);
            }
        } else {
            int valEnd = valStart;
            while (valEnd < end) {
                char c = html.charAt(valEnd);
                if (c == ' ' || c == '>') {
                    break;
                }
                valEnd++;
            }
            return html.substring(valStart, valEnd);
        }
        return null;
    }

    private static boolean isUrlOrTruncated(String text) {
        String lower = text.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("www.")) {
            return true;
        }
        if (text.contains(".") && !text.contains(" ")) {
            return true;
        }
        if (text.contains("...")) {
            int dotDotDot = text.indexOf("...");
            String prefix = lower.substring(0, dotDotDot);
            if (prefix.contains(".") || prefix.contains("/")) {
                return true;
            }
        }
        return false;
    }

    private static int indexOfIgnoreCase(String src, String target, int start, int end) {
        int targetLen = target.length();
        int max = end - targetLen;
        for (int i = start; i <= max; i++) {
            if (src.regionMatches(true, i, target, 0, targetLen)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Decodes HTML entities in text scraped from HTML pages or HTML-escaped API responses.
     * Handles common named entities (&amp;, &lt;, &gt;, &quot;, &apos;, &nbsp;)
     * and numeric entities (e.g. &#39;, &#34;, &#x27;).
     * Unknown entities are left as-is. Single-pass, so "&amp;lt;" decodes to "&lt;".
     *
     * @param text The text possibly containing HTML entities
     * @return The decoded text
     */
    public static String decodeHtmlEntities(String text) {
        if (text == null) return null;
        if (text.indexOf('&') == -1) return text;
        int len = text.length();
        StringBuilder sb = new StringBuilder(len);
        int i = 0;
        while (i < len) {
            char c = text.charAt(i);
            if (c != '&') {
                sb.append(c);
                i++;
                continue;
            }
            int semi = text.indexOf(';', i + 1);
            if (semi == -1 || semi - i > 10) {
                sb.append(c);
                i++;
                continue;
            }
            String entity = text.substring(i + 1, semi);
            if (entity.equals("amp")) {
                sb.append('&');
                i = semi + 1;
            } else if (entity.equals("lt")) {
                sb.append('<');
                i = semi + 1;
            } else if (entity.equals("gt")) {
                sb.append('>');
                i = semi + 1;
            } else if (entity.equals("quot")) {
                sb.append('"');
                i = semi + 1;
            } else if (entity.equals("apos")) {
                sb.append('\'');
                i = semi + 1;
            } else if (entity.equals("nbsp")) {
                sb.append(' ');
                i = semi + 1;
            } else if (entity.length() > 1 && entity.charAt(0) == '#') {
                try {
                    int code;
                    if (entity.charAt(1) == 'x' || entity.charAt(1) == 'X') {
                        code = Integer.parseInt(entity.substring(2), 16);
                    } else {
                        code = Integer.parseInt(entity.substring(1));
                    }
                    sb.append((char) code);
                    i = semi + 1;
                } catch (NumberFormatException e) {
                    sb.append(text, i, semi + 1);
                    i = semi + 1;
                }
            } else {
                sb.append(text, i, semi + 1);
                i = semi + 1;
            }
        }
        return sb.toString();
    }

    /**
     * While most Android <4.1 devices and just ignore emojis and render them as square boxes,
     * for Android 2.3 MediaTek has an amusing bug that makes the application crash if it
     * contains such Unicode emoji characters. As a result it has been decided to strip emojis
     * for Android <4.1 altogether, as such devices cannot display emojis anyway, and it is a better
     * experience when there are less such unrecognized characters.
     *
     * Call this once when user-generated content enters the app (data classes in
     * io.github.gohoski.notpipe.data, search suggestions) rather than at every
     * setText call site, so no UGC path can be forgotten.
     * Only apply to UGC strings, never to URLs, hosts or API-internal values.
     * @return If Android 4.1+ it will return the original string; if <4.1 it will strip emojis
     */
    public static String stripEmoji(String s) {
        if (s == null || s.length() == 0) return "";
        if (NotPipe.SDK >= 16) return s;

        int len = s.length();
        StringBuilder sb = null;

        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            int charCount = 1;
            boolean isEmoji = false;

            // 1. UTF-16 Surrogate pairs (U+10000..U+10FFFF: modern emojis)
            if (c >= 0xD800 && c <= 0xDBFF) {
                isEmoji = true;
                if (i + 1 < len && s.charAt(i + 1) >= 0xDC00 && s.charAt(i + 1) <= 0xDFFF) {
                    charCount = 2;
                }
            } else if (c >= 0xDC00 && c <= 0xDFFF) {
                isEmoji = true; // Orphaned low surrogate
            }
            // 2. Private Use Area (E000-F8FF, carrier emojis)
            else if (c >= 0xE000 && c <= 0xF8FF) {
                isEmoji = true;
            }
            // 3. Miscellaneous Technical (2300-23FF: ⌚, ⌛, ⏩, ⏪, ⏰, ⏳)
            else if (c >= 0x2300 && c <= 0x23FF) {
                isEmoji = true;
            }
            // 4. Geometric Shapes (25A0-25FF: ▶, ◀, ◼, ◽)
            else if (c >= 0x25A0 && c <= 0x25FF) {
                isEmoji = true;
            }
            // 5. Misc Symbols & Dingbats (2600-27BF) & Misc Symbols/Arrows (2B00-2BFF)
            else if ((c >= 0x2600 && c <= 0x27BF) || (c >= 0x2B00 && c <= 0x2BFF)) {
                isEmoji = true;
            }
            // 6. Arrows (2190-21FF) & Supplemental Arrows / Symbols (2900-297F, 2460-24FF)
            else if ((c >= 0x2190 && c <= 0x21FF) || (c >= 0x2460 && c <= 0x24FF)) {
                isEmoji = true;
            }
            // 7. Variation Selectors, ZWJ, and Keycaps
            else if ((c >= 0xFE00 && c <= 0xFE0F) || c == 0x200D || c == 0x20E3) {
                isEmoji = true;
            }
            // 8. CJK symbols used as emoji (3030, 303D, 3297, 3299)
            else if (c == 0x3030 || c == 0x303D || c == 0x3297 || c == 0x3299) {
                isEmoji = true;
            }

            if (isEmoji) {
                if (sb == null) {
                    sb = new StringBuilder(len);
                    // Safe: appends only up to the START of the emoji
                    sb.append(s.substring(0, i));
                }
                if (charCount == 2) {
                    i++; // Advance past the low surrogate AFTER initializing sb
                }
            } else if (sb != null) {
                sb.append(c);
            }
        }

        return sb != null ? sb.toString() : s;
    }
}