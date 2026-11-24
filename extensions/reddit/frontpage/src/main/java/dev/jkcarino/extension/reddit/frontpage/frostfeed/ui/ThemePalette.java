package dev.jkcarino.extension.reddit.frontpage.frostfeed.ui;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.util.TypedValue;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Light/Dark theme palette and UI helpers matching the official Reddit Android app.
 */
public final class ThemePalette {

    public final boolean dark;
    public final int bg;
    public final int surface;
    public final int fg;
    public final int strong;
    public final int weak;
    public final int border;
    public final int borderStrong;
    public final int btnBg;
    public final int selectedBg;
    public final int link;
    public final int orange;
    public final int admin;
    public final int mod;
    public final int op;
    public final int nsfw;
    public final int nsfwBg;
    public final int lock;
    public final int skel;
    public final int badgeBg;
    public final int spoilerBg;

    private ThemePalette(boolean dark) {
        this.dark = dark;
        if (dark) {
            this.bg = 0xFF0E1113;
            this.surface = 0xFF181C1F;
            this.fg = 0xFFB7CAD4;
            this.strong = 0xFFEEF1F3;
            this.weak = 0xFF8BA2AD;
            this.border = 0x1AFFFFFF;
            this.borderStrong = 0x33FFFFFF;
            this.btnBg = 0xFF2A3236;
            this.selectedBg = 0xFF333D42;
            this.link = 0xFF648EFC;
            this.orange = 0xFFFF4500;
            this.admin = 0xFFFF585B;
            this.mod = 0xFF46D160;
            this.op = 0xFF4FBCFF;
            this.nsfw = 0xFFFF585B;
            this.nsfwBg = 0x24FF585B;
            this.lock = 0xFFF5C518;
            this.skel = 0xFF1A2125;
            this.badgeBg = 0x24FF4500;
            this.spoilerBg = 0xFF2A3236;
        } else {
            this.bg = 0xFFFFFFFF;
            this.surface = 0xFFF6F8F9;
            this.fg = 0xFF33464C;
            this.strong = 0xFF0F1A1C;
            this.weak = 0xFF576F76;
            this.border = 0x1A000000;
            this.borderStrong = 0x33000000;
            this.btnBg = 0xFFE5EBEE;
            this.selectedBg = 0xFFD7DEE2;
            this.link = 0xFF0045AC;
            this.orange = 0xFFFF4500;
            this.admin = 0xFFFF4500;
            this.mod = 0xFF008A10;
            this.op = 0xFF0079D3;
            this.nsfw = 0xFFD93900;
            this.nsfwBg = 0x24FF585B;
            this.lock = 0xFFC79100;
            this.skel = 0xFFE5EBEE;
            this.badgeBg = 0x1FFF4500;
            this.spoilerBg = 0xFFD7DEE2;
        }
    }

    public static ThemePalette from(Context context) {
        int uiMode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return new ThemePalette(uiMode == Configuration.UI_MODE_NIGHT_YES);
    }

    public static int dp(Context context, float dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics()));
    }

    public static GradientDrawable pill(int fillColor, int radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fillColor);
        d.setCornerRadius(radiusPx);
        return d;
    }

    public static GradientDrawable outlinedPill(int fillColor, int strokeColor, int strokePx, int radiusPx) {
        GradientDrawable d = pill(fillColor, radiusPx);
        d.setStroke(strokePx, strokeColor);
        return d;
    }

    public static GradientDrawable circle(int fillColor) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(fillColor);
        return d;
    }

    public static int deterministicAvatarColor(String name) {
        String clean = name == null ? "?" : name.replaceFirst("^[ru]/", "");
        int hue = 0;
        for (int i = 0; i < clean.length(); i++) {
            hue = (hue * 31 + clean.charAt(i)) % 360;
        }
        return Color.HSVToColor(new float[]{(float) hue, 0.52f, 0.58f});
    }

    public static Integer parseHexColor(String hex) {
        if (hex == null || hex.isEmpty()) return null;
        try {
            return Color.parseColor(hex.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String formatNumber(Number n) {
        if (n == null) return "?";
        long val = n.longValue();
        String sign = val < 0 ? "-" : "";
        long abs = Math.abs(val);
        if (abs >= 1_000_000L) {
            String s = String.format(Locale.US, "%.1f", abs / 1_000_000.0);
            if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
            return sign + s + "M";
        }
        if (abs >= 1_000L) {
            String s = String.format(Locale.US, "%.1f", abs / 1_000.0);
            if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
            return sign + s + "K";
        }
        return String.valueOf(val);
    }

    public static String formatAgo(long createdUtc) {
        if (createdUtc <= 0L) return "";
        long s = Math.max(0L, (System.currentTimeMillis() / 1000L) - createdUtc);
        if (s >= 31_536_000L) return (s / 31_536_000L) + " yr. ago";
        if (s >= 2_592_000L) return (s / 2_592_000L) + " mo. ago";
        if (s >= 86_400L) {
            long d = s / 86_400L;
            return d + (d == 1L ? " day ago" : " days ago");
        }
        if (s >= 3_600L) return (s / 3_600L) + " hr. ago";
        if (s >= 60L) return (s / 60L) + " min. ago";
        return "just now";
    }

    public static String formatFullDate(long createdUtc) {
        if (createdUtc <= 0L) return "";
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(createdUtc * 1000L));
    }

    public static String formatDuration(Integer sec) {
        if (sec == null || sec < 0) return "";
        int m = sec / 60;
        int s = sec % 60;
        return String.format(Locale.US, "%d:%02d", m, s);
    }

    public static String whoLabel(String subreddit, String author) {
        if (subreddit != null && subreddit.startsWith("u_")) {
            return "u/" + (author != null && !author.isEmpty() ? author : subreddit.substring(2));
        }
        return "r/" + (subreddit != null && !subreddit.isEmpty() ? subreddit : "?");
    }

    public static String redditUrl(String path) {
        if (path == null || path.isEmpty()) return "https://www.reddit.com";
        if (path.startsWith("http://") || path.startsWith("https://")) return path;
        return "https://www.reddit.com" + (path.startsWith("/") ? path : "/" + path);
    }

    public static String whoUrl(String subreddit, String author, String permalink) {
        if (subreddit != null && subreddit.startsWith("u_")) {
            String u = (author != null && !author.isEmpty()) ? author : subreddit.substring(2);
            return "https://www.reddit.com/user/" + Uri.encode(u) + "/";
        }
        if (subreddit == null || subreddit.isEmpty()) return redditUrl(permalink);
        return "https://www.reddit.com/r/" + Uri.encode(subreddit) + "/";
    }

    public static void openInRedditApp(Context context, String url) {
        if (context == null || url == null || url.isEmpty()) return;
        Uri uri = Uri.parse(url);
        Intent inApp = new Intent(Intent.ACTION_VIEW, uri);
        inApp.setPackage(context.getPackageName());
        inApp.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(inApp);
        } catch (Exception e) {
            openExternalUrl(context, url);
        }
    }

    public static void openExternalUrl(Context context, String url) {
        if (context == null || url == null || url.isEmpty()) return;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    public static Drawable createUpvoteIcon(Context ctx, int color, int sizeDp) {
        return new VectorIconDrawable(dp(ctx, sizeDp), color, VectorIconDrawable.KIND_UPVOTE);
    }

    public static Drawable createDownvoteIcon(Context ctx, int color, int sizeDp) {
        return new VectorIconDrawable(dp(ctx, sizeDp), color, VectorIconDrawable.KIND_DOWNVOTE);
    }

    public static Drawable createCommentIcon(Context ctx, int color, int sizeDp) {
        return new VectorIconDrawable(dp(ctx, sizeDp), color, VectorIconDrawable.KIND_COMMENT);
    }

    public static Drawable createShareIcon(Context ctx, int color, int sizeDp) {
        return new VectorIconDrawable(dp(ctx, sizeDp), color, VectorIconDrawable.KIND_SHARE);
    }

    private static final class VectorIconDrawable extends Drawable {
        static final int KIND_UPVOTE = 1;
        static final int KIND_DOWNVOTE = 2;
        static final int KIND_COMMENT = 3;
        static final int KIND_SHARE = 4;

        private final int sizePx;
        private final int kind;
        private final Paint paint;

        VectorIconDrawable(int sizePx, int color, int kind) {
            this.sizePx = sizePx;
            this.kind = kind;
            this.paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            this.paint.setColor(color);
            this.paint.setStyle(Paint.Style.STROKE);
            this.paint.setStrokeWidth(Math.max(1.5f, sizePx * 0.105f));
            this.paint.setStrokeCap(Paint.Cap.ROUND);
            this.paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override public int getIntrinsicWidth() { return sizePx; }
        @Override public int getIntrinsicHeight() { return sizePx; }

        @Override
        public void draw(Canvas canvas) {
            float w = getBounds().width() > 0 ? getBounds().width() : sizePx;
            float h = getBounds().height() > 0 ? getBounds().height() : sizePx;
            float sx = w / 20f;
            float sy = h / 20f;
            Path p = new Path();

            if (kind == KIND_UPVOTE) {
                // Reddit hollow upvote arrow (20x20 viewport)
                p.moveTo(10f * sx, 2.2f * sy);
                p.lineTo(17.4f * sx, 9.8f * sy);
                p.lineTo(13.2f * sx, 9.8f * sy);
                p.lineTo(13.2f * sx, 17.4f * sy);
                p.lineTo(6.8f * sx, 17.4f * sy);
                p.lineTo(6.8f * sx, 9.8f * sy);
                p.lineTo(2.6f * sx, 9.8f * sy);
                p.close();
            } else if (kind == KIND_DOWNVOTE) {
                // Reddit hollow downvote arrow (20x20 viewport)
                p.moveTo(10f * sx, 17.8f * sy);
                p.lineTo(2.6f * sx, 10.2f * sy);
                p.lineTo(6.8f * sx, 10.2f * sy);
                p.lineTo(6.8f * sx, 2.6f * sy);
                p.lineTo(13.2f * sx, 2.6f * sy);
                p.lineTo(13.2f * sx, 10.2f * sy);
                p.lineTo(17.4f * sx, 10.2f * sy);
                p.close();
            } else if (kind == KIND_COMMENT) {
                // Reddit speech bubble (20x20 viewport)
                p.moveTo(10f * sx, 2.4f * sy);
                p.cubicTo(14.6f * sx, 2.4f * sy, 18f * sx, 5.6f * sy, 18f * sx, 9.8f * sy);
                p.cubicTo(18f * sx, 14f * sy, 14.6f * sx, 17.2f * sy, 10f * sx, 17.2f * sy);
                p.lineTo(6.4f * sx, 17.2f * sy);
                p.lineTo(3.0f * sx, 18.2f * sy);
                p.lineTo(4.1f * sx, 14.9f * sy);
                p.cubicTo(2.8f * sx, 13.5f * sy, 2.0f * sx, 11.7f * sy, 2.0f * sx, 9.8f * sy);
                p.cubicTo(2.0f * sx, 5.6f * sy, 5.4f * sx, 2.4f * sy, 10f * sx, 2.4f * sy);
                p.close();
            } else if (kind == KIND_SHARE) {
                // Reddit curved forward share arrow (20x20 viewport)
                p.moveTo(17.8f * sx, 9.2f * sy);
                p.lineTo(11.6f * sx, 3.4f * sy);
                p.lineTo(11.6f * sx, 6.8f * sy);
                p.cubicTo(6.4f * sx, 7.0f * sy, 2.8f * sx, 10.1f * sy, 2.4f * sx, 16.0f * sy);
                p.cubicTo(4.7f * sx, 13.0f * sy, 7.7f * sx, 11.8f * sy, 11.6f * sx, 11.8f * sy);
                p.lineTo(11.6f * sx, 15.0f * sy);
                p.close();
            }
            canvas.drawPath(p, paint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
