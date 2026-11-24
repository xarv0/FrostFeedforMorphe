package dev.jkcarino.extension.reddit.frontpage.frostfeed.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.QuoteSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders Reddit Markdown into Android SpannableStringBuilder with
 * headings, quotes, lists, code blocks, links, r/u mentions, and tap-to-reveal spoilers.
 */
public final class MarkdownRenderer {

    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+(.*)$");
    private static final Pattern QUOTE = Pattern.compile("^>\\s?(.*)$");
    private static final Pattern UL = Pattern.compile("^\\s*[-*+]\\s+(.*)$");
    private static final Pattern OL = Pattern.compile("^\\s*(\\d+[.)])\\s+(.*)$");

    private static final Pattern INLINE_TOKEN = Pattern.compile(
            "`([^`]+)`"
                    + "|>!([^!\\n]{1,400})!<"
                    + "|\\[([^\\]]{1,300})\\]\\((https?://[^\\s)]{1,500}|/[^\\s)]{1,500})\\)"
                    + "|\\*\\*([^*\\n]{1,400})\\*\\*"
                    + "|~~([^~\\n]{1,400})~~"
                    + "|(?<=^|[\\s(])\\*([^*\\n]{1,400})\\*(?=[\\s).,!?:;]|$)"
                    + "|(?<=^|[\\s(])(https?://[^\\s<)\"']{4,500})"
                    + "|(?<=^|[\\s(])/?([ru]/[A-Za-z0-9_-]{2,24})\\b"
    );

    private MarkdownRenderer() {
    }

    public static CharSequence render(Context context, String raw, ThemePalette theme) {
        if (raw == null || raw.trim().isEmpty()) return "";

        SpannableStringBuilder out = new SpannableStringBuilder();
        String[] lines = raw.trim().split("\\r?\\n");
        boolean inCode = false;
        List<String> codeLines = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().startsWith("```")) {
                if (inCode) {
                    appendCodeBlock(out, codeLines, theme);
                    codeLines.clear();
                    inCode = false;
                } else {
                    inCode = true;
                }
                continue;
            }
            if (inCode) {
                codeLines.add(line);
                continue;
            }

            if (line.trim().isEmpty()) {
                if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') {
                    out.append('\n');
                }
                continue;
            }

            if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') {
                out.append('\n');
            }

            Matcher qm = QUOTE.matcher(line);
            if (qm.matches()) {
                int start = out.length();
                appendInline(context, out, qm.group(1), theme);
                out.setSpan(new QuoteSpan(theme.borderStrong), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new ForegroundColorSpan(theme.weak), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.append('\n');
                continue;
            }

            Matcher hm = HEADING.matcher(line);
            if (hm.matches()) {
                int start = out.length();
                appendInline(context, out, hm.group(1), theme);
                out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new ForegroundColorSpan(theme.strong), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.append('\n');
                continue;
            }

            Matcher ulm = UL.matcher(line);
            if (ulm.matches()) {
                out.append("• ");
                appendInline(context, out, ulm.group(1), theme);
                out.append('\n');
                continue;
            }

            Matcher olm = OL.matcher(line);
            if (olm.matches()) {
                out.append(olm.group(1)).append(' ');
                appendInline(context, out, olm.group(2), theme);
                out.append('\n');
                continue;
            }

            appendInline(context, out, line, theme);
            out.append('\n');
        }

        if (inCode && !codeLines.isEmpty()) {
            appendCodeBlock(out, codeLines, theme);
        }

        // Trim trailing newlines.
        while (out.length() > 0 && out.charAt(out.length() - 1) == '\n') {
            out.delete(out.length() - 1, out.length());
        }
        return out;
    }

    private static void appendCodeBlock(SpannableStringBuilder out, List<String> lines, ThemePalette theme) {
        if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') {
            out.append('\n');
        }
        int start = out.length();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) out.append('\n');
            out.append(lines.get(i));
        }
        out.setSpan(new TypefaceSpan("monospace"), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new BackgroundColorSpan(theme.btnBg), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.append('\n');
    }

    private static void appendInline(
            Context context,
            SpannableStringBuilder out,
            String text,
            ThemePalette theme
    ) {
        if (text == null || text.isEmpty()) return;
        Matcher m = INLINE_TOKEN.matcher(text);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) {
                out.append(text, last, m.start());
            }
            int spanStart = out.length();
            if (m.group(1) != null) {
                // Inline code
                out.append(m.group(1));
                out.setSpan(new TypefaceSpan("monospace"), spanStart, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new BackgroundColorSpan(theme.btnBg), spanStart, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (m.group(2) != null) {
                // Inline spoiler >!text!<
                out.append(m.group(2));
                out.setSpan(new SpoilerSpan(theme), spanStart, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (m.group(3) != null && m.group(4) != null) {
                // Markdown link [label](url)
                String label = m.group(3);
                String rawUrl = m.group(4);
                String targetUrl = rawUrl.startsWith("/") ? "https://www.reddit.com" + rawUrl : rawUrl;
                boolean isReddit = targetUrl.contains("reddit.com/");
                out.append(label);
                out.setSpan(new LinkSpan(context, targetUrl, isReddit, theme.link),
                        spanStart, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (m.group(5) != null) {
                // Bold
                out.append(m.group(5));
                out.setSpan(new StyleSpan(Typeface.BOLD), spanStart, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (m.group(6) != null) {
                // Strikethrough
                out.append(m.group(6));
                out.setSpan(new StrikethroughSpan(), spanStart, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (m.group(7) != null) {
                // Italic
                out.append(m.group(7));
                out.setSpan(new StyleSpan(Typeface.ITALIC), spanStart, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (m.group(8) != null) {
                // Bare URL
                String url = m.group(8);
                boolean isReddit = url.contains("reddit.com/");
                out.append(url);
                out.setSpan(new LinkSpan(context, url, isReddit, theme.link),
                        spanStart, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (m.group(9) != null) {
                // r/sub or u/user
                String ref = m.group(9);
                String url = "https://www.reddit.com/" + ref + "/";
                out.append(ref);
                out.setSpan(new LinkSpan(context, url, true, theme.link),
                        spanStart, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            last = m.end();
        }
        if (last < text.length()) {
            out.append(text, last, text.length());
        }
    }

    private static final class LinkSpan extends ClickableSpan {
        private final Context context;
        private final String url;
        private final boolean inApp;
        private final int color;

        LinkSpan(Context context, String url, boolean inApp, int color) {
            this.context = context;
            this.url = url;
            this.inApp = inApp;
            this.color = color;
        }

        @Override
        public void onClick(View widget) {
            if (inApp) {
                ThemePalette.openInRedditApp(context, url);
            } else {
                ThemePalette.openExternalUrl(context, url);
            }
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            ds.setColor(color);
            ds.setUnderlineText(false);
        }
    }

    private static final class SpoilerSpan extends ClickableSpan {
        private final ThemePalette theme;
        private boolean revealed = false;

        SpoilerSpan(ThemePalette theme) {
            this.theme = theme;
        }

        @Override
        public void onClick(View widget) {
            revealed = !revealed;
            widget.invalidate();
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            ds.bgColor = theme.spoilerBg;
            ds.setColor(revealed ? theme.fg : theme.spoilerBg);
            ds.setUnderlineText(false);
        }
    }
}
