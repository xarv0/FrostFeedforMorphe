package dev.jkcarino.extension.reddit.frontpage.frostfeed.ui;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ImageSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import dev.jkcarino.extension.reddit.frontpage.frostfeed.model.ArchiveModels.ArchiveItem;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.model.ArchiveModels.ArchivedComment;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.model.ArchiveModels.ArchivedPost;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.model.ArchiveModels.CrosspostInfo;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.model.ArchiveModels.FlairPart;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.model.ArchiveModels.GalleryItem;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.model.ArchiveModels.SubredditInfo;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.model.ArchiveModels.ViewDensity;

/**
 * ListView adapter rendering archived Reddit posts and comments in native Android views.
 */
public final class ArchiveFeedAdapter extends BaseAdapter {

    private static final int TYPE_POST = 0;
    private static final int TYPE_COMMENT = 1;
    private static final Pattern DIRECT_IMAGE_URL = Pattern.compile(
            "^https?://i\\.redd\\.it/|\\.(?:jpe?g|png|gif|webp)(?:[?#].*)?$",
            Pattern.CASE_INSENSITIVE
    );

    private final Context context;
    private final ThemePalette theme;
    private final List<ArchiveItem> items = new ArrayList<>();
    private final Map<String, SubredditInfo> subInfoMap = new HashMap<>();
    private final Set<String> revealedSensitiveIds = new HashSet<>();
    private final Map<String, Integer> galleryIndices = new HashMap<>();
    private ViewDensity density;

    public ArchiveFeedAdapter(Context context, ThemePalette theme, ViewDensity density) {
        this.context = context;
        this.theme = theme;
        this.density = density;
    }

    public void setDensity(ViewDensity density) {
        this.density = density;
        notifyDataSetChanged();
    }

    public void clearItems() {
        items.clear();
        notifyDataSetChanged();
    }

    public void addItems(List<ArchiveItem> newItems) {
        if (newItems == null || newItems.isEmpty()) return;
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    public void putSubredditInfo(Map<String, SubredditInfo> map) {
        if (map == null || map.isEmpty()) return;
        subInfoMap.putAll(map);
        notifyDataSetChanged();
    }

    @Override public int getCount() { return items.size(); }
    @Override public ArchiveItem getItem(int position) { return items.get(position); }
    @Override public long getItemId(int position) { return position; }
    @Override public int getViewTypeCount() { return 2; }
    @Override public int getItemViewType(int position) {
        return items.get(position) instanceof ArchivedComment ? TYPE_COMMENT : TYPE_POST;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ArchiveItem item = items.get(position);
        if (item instanceof ArchivedComment) {
            return buildCommentCard((ArchivedComment) item);
        }
        return buildPostCard((ArchivedPost) item);
    }

    // ------------------------------------------------------------------ //
    // Post Card
    // ------------------------------------------------------------------ //

    private View buildPostCard(ArchivedPost p) {
        int dp4 = dp(4);
        int dp6 = dp(6);
        int dp8 = dp(8);
        int dp12 = dp(12);
        int dp16 = dp(16);

        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setBackgroundColor(theme.bg);

        LinearLayout inner = new LinearLayout(context);
        inner.setOrientation(density == ViewDensity.COMPACT ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        inner.setPadding(dp16, dp12, dp16, dp12);
        String threadUrl = ThemePalette.redditUrl(p.permalink);
        inner.setOnClickListener(v -> ThemePalette.openInRedditApp(context, threadUrl));

        if (density == ViewDensity.COMPACT) {
            inner.addView(buildCompactThumb(p));
        }

        LinearLayout mainCol = new LinearLayout(context);
        mainCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mainLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (density == ViewDensity.CARD) {
            mainLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        mainCol.setLayoutParams(mainLp);

        // Credit row: avatar + r/sub + badge + dot + time + edited + status icons
        LinearLayout credit = new LinearLayout(context);
        credit.setOrientation(LinearLayout.HORIZONTAL);
        credit.setGravity(Gravity.CENTER_VERTICAL);
        String label = ThemePalette.whoLabel(p.subreddit, p.author);
        String whoHref = ThemePalette.whoUrl(p.subreddit, p.author, p.permalink);

        credit.addView(buildAvatar(label, p.subredditId, whoHref));

        TextView subTv = new TextView(context);
        subTv.setText(label);
        subTv.setTextColor(theme.strong);
        subTv.setTextSize(12f);
        subTv.setTypeface(Typeface.DEFAULT_BOLD);
        subTv.setPadding(dp6, 0, dp4, 0);
        subTv.setOnClickListener(v -> ThemePalette.openInRedditApp(context, whoHref));
        credit.addView(subTv);

        if (p.distinguished != null) {
            credit.addView(buildDistBadge(p.distinguished));
        }

        StringBuilder metaSuffix = new StringBuilder();
        metaSuffix.append("• ").append(ThemePalette.formatAgo(p.createdUtc));
        if (p.edited) metaSuffix.append(" (edited)");
        if (p.stickied) metaSuffix.append(" 📌");
        if (p.locked) metaSuffix.append(" 🔒");
        if (p.archived) metaSuffix.append(" 📦");

        TextView timeTv = new TextView(context);
        timeTv.setText(metaSuffix.toString());
        timeTv.setTextColor(theme.weak);
        timeTv.setTextSize(12f);
        timeTv.setSingleLine(true);
        timeTv.setEllipsize(TextUtils.TruncateAt.END);
        credit.addView(timeTv);
        mainCol.addView(credit);

        // Title
        TextView titleTv = new TextView(context);
        titleTv.setText(p.title != null && !p.title.isEmpty() ? p.title : "(untitled)");
        titleTv.setTextColor(theme.strong);
        titleTv.setTextSize(density == ViewDensity.COMPACT ? 15f : 17f);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        titleTv.setPadding(0, dp6, 0, dp4);
        mainCol.addView(titleTv);

        // Tags row: NSFW, SPOILER, Flair
        LinearLayout tagsRow = buildTagsRow(
                p.over18, p.spoiler, p.flair, p.flairBg, p.flairTextColor, p.flairRich);
        if (tagsRow != null) {
            mainCol.addView(tagsRow);
        }

        // Media (only in Card density)
        if (density == ViewDensity.CARD) {
            View media = buildPostMedia(p);
            if (media != null) {
                LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                mlp.topMargin = dp6;
                mlp.bottomMargin = dp4;
                media.setLayoutParams(mlp);
                mainCol.addView(media);
            }
        }

        // Body snippet
        if (density == ViewDensity.CARD && p.body != null && !p.body.isEmpty()) {
            TextView bodyTv = new TextView(context);
            bodyTv.setTextSize(14f);
            bodyTv.setPadding(0, dp4, 0, dp6);
            if (p.removed) {
                bodyTv.setText(p.body.trim());
                bodyTv.setTextColor(theme.weak);
                bodyTv.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
            } else {
                String raw = p.body.length() > 700
                        ? p.body.substring(0, 700).replaceFirst("\\s+\\S*$", "") + "…"
                        : p.body;
                bodyTv.setText(MarkdownRenderer.render(context, raw, theme));
                bodyTv.setTextColor(theme.fg);
                bodyTv.setMaxLines(6);
                bodyTv.setEllipsize(TextUtils.TruncateAt.END);
                bodyTv.setMovementMethod(LinkMovementMethod.getInstance());
                bodyTv.setFocusable(false);
            }
            mainCol.addView(bodyTv);
        }

        // Action pills row
        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actLp.topMargin = dp8;
        actions.setLayoutParams(actLp);

        if (p.score != null) {
            actions.addView(buildVotePill(p.score, false));
        }
        if (p.numComments != null) {
            actions.addView(buildIconActionPill(
                    ThemePalette.createCommentIcon(context, theme.strong, 15),
                    ThemePalette.formatNumber(p.numComments),
                    false,
                    v -> ThemePalette.openInRedditApp(context, threadUrl)
            ));
        }
        actions.addView(buildSharePill(threadUrl, false));
        mainCol.addView(actions);

        inner.addView(mainCol);
        wrapper.addView(inner);
        wrapper.addView(buildDivider());
        return wrapper;
    }

    // ------------------------------------------------------------------ //
    // Comment Card
    // ------------------------------------------------------------------ //

    private View buildCommentCard(ArchivedComment c) {
        int dp4 = dp(4);
        int dp6 = dp(6);
        int dp8 = dp(8);
        int dp12 = dp(12);
        int dp16 = dp(16);

        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setBackgroundColor(theme.bg);

        LinearLayout inner = new LinearLayout(context);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp16, dp12, dp16, dp12);
        String threadUrl = ThemePalette.redditUrl(c.permalink);
        inner.setOnClickListener(v -> ThemePalette.openInRedditApp(context, threadUrl));

        // Context header: avatar + 2 lines
        LinearLayout ctx = new LinearLayout(context);
        ctx.setOrientation(LinearLayout.HORIZONTAL);
        ctx.setGravity(Gravity.TOP);

        String label = ThemePalette.whoLabel(c.subreddit, c.author);
        String whoHref = ThemePalette.whoUrl(c.subreddit, c.author, c.permalink);
        ctx.addView(buildAvatar(label, c.subredditId, whoHref));

        LinearLayout ctxMain = new LinearLayout(context);
        ctxMain.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ctxLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ctxLp.leftMargin = dp8;
        ctxMain.setLayoutParams(ctxLp);

        // Line 1: r/sub • linkTitle
        LinearLayout line1 = new LinearLayout(context);
        line1.setOrientation(LinearLayout.HORIZONTAL);
        line1.setGravity(Gravity.CENTER_VERTICAL);

        TextView subTv = new TextView(context);
        subTv.setText(label);
        subTv.setTextColor(theme.strong);
        subTv.setTextSize(12f);
        subTv.setTypeface(Typeface.DEFAULT_BOLD);
        subTv.setOnClickListener(v -> ThemePalette.openInRedditApp(context, whoHref));
        line1.addView(subTv);

        if (c.linkTitle != null && !c.linkTitle.isEmpty()) {
            TextView titleTv = new TextView(context);
            titleTv.setText(" • " + c.linkTitle);
            titleTv.setTextColor(theme.fg);
            titleTv.setTextSize(12f);
            titleTv.setSingleLine(true);
            titleTv.setEllipsize(TextUtils.TruncateAt.END);
            String postUrl = ThemePalette.redditUrl(
                    c.linkPermalink != null ? c.linkPermalink : c.permalink);
            titleTv.setOnClickListener(v -> ThemePalette.openInRedditApp(context, postUrl));
            line1.addView(titleTv);
        }
        ctxMain.addView(line1);

        // Line 2: author + OP + MOD + verb + time + edited + status
        LinearLayout line2 = new LinearLayout(context);
        line2.setOrientation(LinearLayout.HORIZONTAL);
        line2.setGravity(Gravity.CENTER_VERTICAL);
        line2.setPadding(0, dp(2), 0, 0);

        TextView authorTv = new TextView(context);
        authorTv.setText(c.author != null && !c.author.isEmpty() ? c.author : "?");
        authorTv.setTextColor(theme.strong);
        authorTv.setTextSize(12f);
        authorTv.setTypeface(Typeface.DEFAULT_BOLD);
        line2.addView(authorTv);

        if (c.isSubmitter) {
            TextView opTv = new TextView(context);
            opTv.setText(" OP");
            opTv.setTextColor(theme.op);
            opTv.setTextSize(11f);
            opTv.setTypeface(Typeface.DEFAULT_BOLD);
            line2.addView(opTv);
        }

        if (c.distinguished != null) {
            line2.addView(buildDistBadge(c.distinguished));
        }

        String verb = (c.parentId != null && c.parentId.startsWith("t1_")) ? " replied" : " commented";
        StringBuilder meta = new StringBuilder(verb)
                .append(" • ")
                .append(ThemePalette.formatAgo(c.createdUtc));
        if (c.edited) meta.append(" (edited)");
        if (c.stickied) meta.append(" 📌");
        if (c.locked) meta.append(" 🔒");
        if (c.archived) meta.append(" 📦");

        TextView verbTv = new TextView(context);
        verbTv.setText(meta.toString());
        verbTv.setTextColor(theme.weak);
        verbTv.setTextSize(12f);
        verbTv.setSingleLine(true);
        verbTv.setEllipsize(TextUtils.TruncateAt.END);
        line2.addView(verbTv);
        ctxMain.addView(line2);

        // Optional author flair
        View flair = buildFlairView(c.authorFlair, c.authorFlairBg, c.authorFlairColor, c.authorFlairRich);
        if (flair != null) {
            LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            flp.topMargin = dp4;
            flair.setLayoutParams(flp);
            ctxMain.addView(flair);
        }

        ctx.addView(ctxMain);
        inner.addView(ctx);

        // Comment body
        TextView bodyTv = new TextView(context);
        bodyTv.setTextSize(14f);
        bodyTv.setPadding(dp(32), dp6, 0, dp6);
        if (c.removed) {
            bodyTv.setText(c.body.trim());
            bodyTv.setTextColor(theme.weak);
            bodyTv.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
        } else {
            bodyTv.setText(MarkdownRenderer.render(context, c.body, theme));
            bodyTv.setTextColor(theme.fg);
            bodyTv.setMovementMethod(LinkMovementMethod.getInstance());
            bodyTv.setFocusable(false);
        }
        inner.addView(bodyTv);

        // Comment actions
        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(dp(32), dp4, 0, 0);

        if (c.score != null) {
            actions.addView(buildVotePill(c.score, true));
        }
        actions.addView(buildIconActionPill(
                ThemePalette.createCommentIcon(context, theme.weak, 14),
                "View thread",
                true,
                v -> ThemePalette.openInRedditApp(context, threadUrl)
        ));
        actions.addView(buildSharePill(threadUrl, true));
        inner.addView(actions);

        wrapper.addView(inner);
        wrapper.addView(buildDivider());
        return wrapper;
    }

    // ------------------------------------------------------------------ //
    // Media Builders (Crosspost, Gallery, Video, Image, Link Card)
    // ------------------------------------------------------------------ //

    private View buildPostMedia(ArchivedPost p) {
        if (p.removed) return null;

        // 1. Crosspost card
        if (p.crosspost != null) {
            return buildCrosspostCard(p.crosspost);
        }

        // 2. Gallery carousel
        if (p.gallery != null && !p.gallery.isEmpty()) {
            return wrapSensitive(buildGalleryView(p), p);
        }

        // 3. Video
        if (p.isVideo && p.videoUrl != null) {
            return wrapSensitive(buildVideoCard(p), p);
        }

        // 4. Single image
        String imgSrc = ("image".equals(p.postHint) || isDirectImageUrl(p.url))
                ? (p.previewUrl != null ? p.previewUrl : p.url)
                : null;
        if (imgSrc != null) {
            return wrapSensitive(buildSingleImageView(p, imgSrc), p);
        }

        // 5. External link card
        if (p.url != null && !p.isSelf) {
            return buildLinkCard(p);
        }

        return null;
    }

    private View buildCrosspostCard(CrosspostInfo xp) {
        int dp10 = dp(10);
        int dp12 = dp(12);
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp12, dp10, dp12, dp10);
        box.setBackground(ThemePalette.outlinedPill(theme.surface, theme.border, dp(1), dp(12)));
        String xpUrl = ThemePalette.redditUrl(xp.permalink);
        box.setOnClickListener(v -> ThemePalette.openInRedditApp(context, xpUrl));

        TextView head = new TextView(context);
        head.setText(ThemePalette.whoLabel(xp.subreddit, xp.author) + " • " + ThemePalette.formatAgo(xp.createdUtc));
        head.setTextColor(theme.weak);
        head.setTextSize(12f);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(head);

        TextView title = new TextView(context);
        title.setText(xp.title != null && !xp.title.isEmpty() ? xp.title : "(untitled)");
        title.setTextColor(theme.strong);
        title.setTextSize(14f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, dp(4), 0, dp(4));
        box.addView(title);

        TextView meta = new TextView(context);
        meta.setText(ThemePalette.formatNumber(xp.score) + " upvotes · "
                + ThemePalette.formatNumber(xp.numComments) + " comments");
        meta.setTextColor(theme.weak);
        meta.setTextSize(12f);
        box.addView(meta);
        return box;
    }

    private FrameLayout buildGalleryView(ArchivedPost p) {
        final List<GalleryItem> gallery = p.gallery;
        final int total = gallery.size();
        Integer stored = galleryIndices.get(p.id);
        final int[] idx = {stored != null && stored >= 0 && stored < total ? stored : 0};

        FrameLayout frame = createMediaFrame();
        ImageView img = new ImageView(context);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        img.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(280)));
        frame.addView(img);

        TextView counter = new TextView(context);
        counter.setTextColor(Color.WHITE);
        counter.setTextSize(12f);
        counter.setTypeface(Typeface.DEFAULT_BOLD);
        counter.setPadding(dp(10), dp(4), dp(10), dp(4));
        counter.setBackground(ThemePalette.pill(0xB0000000, dp(16)));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        clp.setMargins(0, dp(10), dp(10), 0);
        counter.setLayoutParams(clp);
        frame.addView(counter);

        TextView caption = new TextView(context);
        caption.setTextColor(Color.WHITE);
        caption.setTextSize(12f);
        caption.setPadding(dp(12), dp(6), dp(12), dp(6));
        caption.setBackgroundColor(0xA6000000);
        FrameLayout.LayoutParams capLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        caption.setLayoutParams(capLp);
        frame.addView(caption);

        TextView prev = buildGalleryNavButton("‹", Gravity.START | Gravity.CENTER_VERTICAL);
        TextView next = buildGalleryNavButton("›", Gravity.END | Gravity.CENTER_VERTICAL);
        frame.addView(prev);
        frame.addView(next);

        Runnable renderSlide = () -> {
            GalleryItem cur = gallery.get(idx[0]);
            galleryIndices.put(p.id, idx[0]);
            counter.setText((idx[0] + 1) + " / " + total);
            prev.setVisibility(idx[0] > 0 ? View.VISIBLE : View.GONE);
            next.setVisibility(idx[0] < total - 1 ? View.VISIBLE : View.GONE);
            if (cur.caption != null && !cur.caption.isEmpty()) {
                caption.setText(cur.caption);
                caption.setVisibility(View.VISIBLE);
            } else {
                caption.setVisibility(View.GONE);
            }
            AsyncImageLoader.getInstance().loadInto(img, cur.url, null, 1080, false, null);
        };

        prev.setOnClickListener(v -> {
            if (idx[0] > 0) {
                idx[0]--;
                renderSlide.run();
            }
        });
        next.setOnClickListener(v -> {
            if (idx[0] < total - 1) {
                idx[0]++;
                renderSlide.run();
            }
        });
        renderSlide.run();
        return frame;
    }

    private TextView buildGalleryNavButton(String symbol, int gravity) {
        TextView btn = new TextView(context);
        btn.setText(symbol);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(20f);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(ThemePalette.circle(0x9E000000));
        int size = dp(36);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size, gravity);
        lp.setMargins(dp(8), 0, dp(8), 0);
        btn.setLayoutParams(lp);
        return btn;
    }

    private FrameLayout buildVideoCard(ArchivedPost p) {
        FrameLayout frame = createMediaFrame();
        ImageView poster = new ImageView(context);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(240)));
        String posterUrl = p.previewUrl != null ? p.previewUrl : p.thumbnail;
        if (posterUrl != null) {
            AsyncImageLoader.getInstance().loadInto(poster, posterUrl, null, 960, false, null);
        }
        frame.addView(poster);

        TextView playBtn = new TextView(context);
        playBtn.setText("▶");
        playBtn.setTextColor(Color.WHITE);
        playBtn.setTextSize(22f);
        playBtn.setGravity(Gravity.CENTER);
        playBtn.setBackground(ThemePalette.circle(0xB8000000));
        int size = dp(56);
        playBtn.setLayoutParams(new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        frame.addView(playBtn);

        if (p.videoDuration != null) {
            TextView dur = new TextView(context);
            dur.setText(ThemePalette.formatDuration(p.videoDuration));
            dur.setTextColor(Color.WHITE);
            dur.setTextSize(11f);
            dur.setTypeface(Typeface.DEFAULT_BOLD);
            dur.setPadding(dp(6), dp(2), dp(6), dp(2));
            dur.setBackground(ThemePalette.pill(0xB8000000, dp(4)));
            FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.END);
            dlp.setMargins(0, 0, dp(10), dp(10));
            dur.setLayoutParams(dlp);
            frame.addView(dur);
        }

        frame.setOnClickListener(v -> showVideoPlayerDialog(p.videoUrl));
        return frame;
    }

    private void showVideoPlayerDialog(String videoUrl) {
        Dialog dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Color.BLACK);

        VideoView videoView = new VideoView(context);
        videoView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        MediaController controller = new MediaController(context);
        controller.setAnchorView(videoView);
        videoView.setMediaController(controller);
        videoView.setVideoURI(Uri.parse(videoUrl));
        videoView.setOnPreparedListener(mp -> videoView.start());
        videoView.setOnErrorListener((mp, what, extra) -> {
            Toast.makeText(context, "Unable to play archived video.", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            return true;
        });
        root.addView(videoView);

        TextView close = new TextView(context);
        close.setText("✕");
        close.setTextColor(Color.WHITE);
        close.setTextSize(18f);
        close.setGravity(Gravity.CENTER);
        close.setBackground(ThemePalette.circle(0x99000000));
        int sz = dp(40);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(sz, sz, Gravity.TOP | Gravity.END);
        clp.setMargins(0, dp(16), dp(16), 0);
        close.setLayoutParams(clp);
        close.setOnClickListener(v -> dialog.dismiss());
        root.addView(close);

        dialog.setContentView(root);
        dialog.show();
    }

    private FrameLayout buildSingleImageView(ArchivedPost p, String imgSrc) {
        FrameLayout frame = createMediaFrame();
        ImageView img = new ImageView(context);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        img.setAdjustViewBounds(true);
        img.setMaxHeight(dp(360));
        img.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        AsyncImageLoader.getInstance().loadInto(img, imgSrc, p.url, 1080, false, null);
        frame.addView(img);
        return frame;
    }

    private View buildLinkCard(ArchivedPost p) {
        String host = p.domain;
        if (host == null || host.isEmpty()) {
            try {
                host = URI.create(p.url).getHost();
                if (host != null && host.startsWith("www.")) host = host.substring(4);
            } catch (Exception ignored) {
            }
        }
        if (host == null || host.isEmpty()) host = "external link";

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(ThemePalette.outlinedPill(theme.surface, theme.border, dp(1), dp(12)));
        card.setOnClickListener(v -> ThemePalette.openExternalUrl(context, p.url));

        LinearLayout info = new LinearLayout(context);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView hostTv = new TextView(context);
        hostTv.setText(host + " ↗");
        hostTv.setTextColor(theme.strong);
        hostTv.setTextSize(13f);
        hostTv.setTypeface(Typeface.DEFAULT_BOLD);
        info.addView(hostTv);

        TextView urlTv = new TextView(context);
        urlTv.setText(p.url);
        urlTv.setTextColor(theme.weak);
        urlTv.setTextSize(12f);
        urlTv.setSingleLine(true);
        urlTv.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(urlTv);
        card.addView(info);

        String thumb = p.thumbnail != null ? p.thumbnail : p.previewUrl;
        if (thumb != null) {
            ImageView iv = new ImageView(context);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(64), dp(64));
            ilp.leftMargin = dp(10);
            iv.setLayoutParams(ilp);
            iv.setBackground(ThemePalette.pill(theme.btnBg, dp(8)));
            iv.setClipToOutline(true);
            AsyncImageLoader.getInstance().loadInto(iv, thumb, null, 256, false,
                    () -> iv.setVisibility(View.GONE));
            card.addView(iv);
        }
        return card;
    }

    private View buildCompactThumb(ArchivedPost p) {
        FrameLayout box = new FrameLayout(context);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(76), dp(58));
        lp.rightMargin = dp(12);
        box.setLayoutParams(lp);
        box.setBackground(ThemePalette.pill(theme.btnBg, dp(8)));
        box.setClipToOutline(true);

        String src = p.thumbnail;
        if (src == null) src = p.previewUrl;
        if (src == null && p.gallery != null && !p.gallery.isEmpty()) src = p.gallery.get(0).url;
        if (src == null && isDirectImageUrl(p.url)) src = p.url;

        if (src != null && !p.removed) {
            ImageView iv = new ImageView(context);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if ((p.over18 || p.spoiler) && !revealedSensitiveIds.contains(p.id)) {
                applyBlurIfSupported(iv);
            }
            AsyncImageLoader.getInstance().loadInto(iv, src, null, 256, false, null);
            box.addView(iv);
        } else {
            TextView icon = new TextView(context);
            icon.setText(p.isVideo ? "▶" : (p.isSelf ? "≡" : "🔗"));
            icon.setTextColor(theme.weak);
            icon.setTextSize(18f);
            icon.setGravity(Gravity.CENTER);
            icon.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            box.addView(icon);
        }
        return box;
    }

    private FrameLayout createMediaFrame() {
        FrameLayout frame = new FrameLayout(context);
        frame.setBackground(ThemePalette.outlinedPill(Color.BLACK, theme.border, dp(1), dp(14)));
        frame.setClipToOutline(true);
        return frame;
    }

    private View wrapSensitive(FrameLayout mediaFrame, ArchivedPost p) {
        if ((!p.over18 && !p.spoiler) || revealedSensitiveIds.contains(p.id)) {
            return mediaFrame;
        }
        for (int i = 0; i < mediaFrame.getChildCount(); i++) {
            applyBlurIfSupported(mediaFrame.getChildAt(i));
        }

        FrameLayout overlay = new FrameLayout(context);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.setBackgroundColor(Build.VERSION.SDK_INT >= 31 ? 0x73000000 : 0xE6101417);

        TextView btn = new TextView(context);
        btn.setText(p.over18 ? "View NSFW content" : "View spoiler");
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(13f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setPadding(dp(16), dp(8), dp(16), dp(8));
        btn.setBackground(ThemePalette.outlinedPill(0xD10F1A1C, 0x59FFFFFF, dp(1), dp(999)));
        btn.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        overlay.addView(btn);

        overlay.setOnClickListener(v -> {
            revealedSensitiveIds.add(p.id);
            notifyDataSetChanged();
        });
        mediaFrame.addView(overlay);
        return mediaFrame;
    }

    private static void applyBlurIfSupported(View view) {
        if (Build.VERSION.SDK_INT >= 31) {
            view.setRenderEffect(RenderEffect.createBlurEffect(40f, 40f, Shader.TileMode.CLAMP));
        }
    }

    // ------------------------------------------------------------------ //
    // Shared UI Helpers (Avatars, Flairs, Badges, Action Pills)
    // ------------------------------------------------------------------ //

    private View buildAvatar(String label, String subredditId, String href) {
        int size = dp(24);
        FrameLayout container = new FrameLayout(context);
        container.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        container.setOnClickListener(v -> ThemePalette.openInRedditApp(context, href));

        boolean isUser = label.startsWith("u/");
        String clean = label.replaceFirst("^[ru]/", "");
        String ch = isUser
                ? (clean.isEmpty() ? "U" : clean.substring(0, 1).toUpperCase(Locale.US))
                : "r/";

        SubredditInfo subInfo = subredditId != null
                ? subInfoMap.get(subredditId.toLowerCase(Locale.US))
                : null;
        Integer parsedColor = subInfo != null ? ThemePalette.parseHexColor(subInfo.color) : null;
        int bgColor = parsedColor != null ? parsedColor : ThemePalette.deterministicAvatarColor(clean);

        container.setBackground(ThemePalette.circle(bgColor));

        TextView fallback = new TextView(context);
        fallback.setText(ch);
        fallback.setTextColor(Color.WHITE);
        fallback.setTextSize(10f);
        fallback.setTypeface(Typeface.DEFAULT_BOLD);
        fallback.setGravity(Gravity.CENTER);
        fallback.setLayoutParams(new FrameLayout.LayoutParams(size, size));
        container.addView(fallback);

        if (subInfo != null && subInfo.icon != null) {
            ImageView iv = new ImageView(context);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setLayoutParams(new FrameLayout.LayoutParams(size, size));
            AsyncImageLoader.getInstance().loadInto(iv, subInfo.icon, subInfo.fallback, 96, true,
                    () -> iv.setVisibility(View.GONE));
            container.addView(iv);
        }
        return container;
    }

    private View buildDistBadge(String kind) {
        TextView tv = new TextView(context);
        String text = "admin".equals(kind) ? "ADMIN"
                : "moderator".equals(kind) ? "MOD"
                : kind.toUpperCase(Locale.US);
        tv.setText(" " + text + " ");
        tv.setTextSize(10.5f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor("moderator".equals(kind) ? theme.mod : theme.admin);
        return tv;
    }

    private LinearLayout buildTagsRow(
            boolean over18,
            boolean spoiler,
            String flairText,
            String flairBg,
            String flairTextColor,
            List<FlairPart> flairRich
    ) {
        View flair = buildFlairView(flairText, flairBg, flairTextColor, flairRich);
        if (!over18 && !spoiler && flair == null) return null;

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = dp(4);
        row.setLayoutParams(rlp);

        if (over18) {
            TextView nsfw = new TextView(context);
            nsfw.setText("NSFW");
            nsfw.setTextColor(theme.nsfw);
            nsfw.setTextSize(10.5f);
            nsfw.setTypeface(Typeface.DEFAULT_BOLD);
            nsfw.setPadding(dp(7), dp(2), dp(7), dp(2));
            nsfw.setBackground(ThemePalette.pill(theme.nsfwBg, dp(999)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(6);
            nsfw.setLayoutParams(lp);
            row.addView(nsfw);
        }

        if (spoiler) {
            TextView sp = new TextView(context);
            sp.setText("SPOILER");
            sp.setTextColor(theme.weak);
            sp.setTextSize(10.5f);
            sp.setTypeface(Typeface.DEFAULT_BOLD);
            sp.setPadding(dp(7), dp(1), dp(7), dp(1));
            sp.setBackground(ThemePalette.outlinedPill(Color.TRANSPARENT, theme.borderStrong, dp(1), dp(999)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(6);
            sp.setLayoutParams(lp);
            row.addView(sp);
        }

        if (flair != null) {
            row.addView(flair);
        }
        return row;
    }

    private View buildFlairView(
            String text,
            String bgHex,
            String textColorMode,
            List<FlairPart> richtext
    ) {
        boolean hasRich = richtext != null && !richtext.isEmpty();
        if ((text == null || text.isEmpty()) && !hasRich) return null;

        Integer parsedBg = ThemePalette.parseHexColor(bgHex);
        int bgColor = parsedBg != null ? parsedBg : theme.btnBg;
        int fgColor = parsedBg != null
                ? ("light".equals(textColorMode) ? Color.WHITE : 0xFF0F1A1C)
                : theme.strong;

        TextView tv = new TextView(context);
        tv.setTextSize(11.5f);
        tv.setTextColor(fgColor);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setPadding(dp(8), dp(2), dp(8), dp(2));
        tv.setBackground(ThemePalette.pill(bgColor, dp(999)));

        if (!hasRich) {
            tv.setText(text);
            return tv;
        }

        SpannableStringBuilder ssb = new SpannableStringBuilder();
        int emojiPx = dp(15);
        for (FlairPart part : richtext) {
            if (part.isEmoji && part.url != null) {
                int start = ssb.length();
                String placeholder = (part.text != null && !part.text.isEmpty()) ? part.text : "□";
                ssb.append(placeholder).append(' ');
                int end = start + placeholder.length();
                AsyncImageLoader.getInstance().loadBitmap(part.url, 64, bmp -> {
                    if (bmp == null) return;
                    BitmapDrawable drawable = new BitmapDrawable(context.getResources(), bmp);
                    drawable.setBounds(0, 0, emojiPx, emojiPx);
                    ssb.setSpan(new ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM),
                            start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    tv.setText(ssb);
                });
            } else if (!part.isEmoji && part.text != null) {
                String clean = part.text.trim();
                if (!clean.isEmpty()) {
                    if (ssb.length() > 0 && ssb.charAt(ssb.length() - 1) != ' ') ssb.append(' ');
                    ssb.append(clean);
                }
            }
        }
        if (ssb.length() == 0) {
            if (text == null || text.isEmpty()) return null;
            tv.setText(text);
        } else {
            tv.setText(ssb);
        }
        return tv;
    }

    private View buildVotePill(int score, boolean plain) {
        int fg = plain ? theme.weak : theme.strong;
        int iconDp = plain ? 14 : 15;
        LinearLayout box = createPillContainer(plain, dp(10), dp(10));

        ImageView up = new ImageView(context);
        up.setImageDrawable(ThemePalette.createUpvoteIcon(context, fg, iconDp));
        up.setLayoutParams(new LinearLayout.LayoutParams(dp(iconDp), dp(iconDp)));
        box.addView(up);

        TextView num = new TextView(context);
        num.setText(ThemePalette.formatNumber(score));
        num.setTextColor(fg);
        num.setTextSize(12.5f);
        num.setTypeface(Typeface.DEFAULT_BOLD);
        num.setIncludeFontPadding(false);
        num.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.leftMargin = dp(6);
        nlp.rightMargin = dp(6);
        num.setLayoutParams(nlp);
        box.addView(num);

        ImageView down = new ImageView(context);
        down.setImageDrawable(ThemePalette.createDownvoteIcon(context, fg, iconDp));
        down.setLayoutParams(new LinearLayout.LayoutParams(dp(iconDp), dp(iconDp)));
        box.addView(down);
        return box;
    }

    private View buildIconActionPill(
            android.graphics.drawable.Drawable iconDrawable,
            String label,
            boolean plain,
            View.OnClickListener listener
    ) {
        int fg = plain ? theme.weak : theme.strong;
        int iconDp = plain ? 14 : 15;
        LinearLayout box = createPillContainer(plain, dp(11), dp(11));

        ImageView iv = new ImageView(context);
        iv.setImageDrawable(iconDrawable);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(iconDp), dp(iconDp)));
        box.addView(iv);

        TextView tv = new TextView(context);
        tv.setText(label);
        tv.setTextColor(fg);
        tv.setTextSize(12.5f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setIncludeFontPadding(false);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = dp(6);
        tv.setLayoutParams(tlp);
        box.addView(tv);

        if (listener != null) box.setOnClickListener(listener);
        return box;
    }

    private View buildSharePill(String url, boolean plain) {
        int fg = plain ? theme.weak : theme.strong;
        int iconDp = plain ? 14 : 15;
        LinearLayout box = createPillContainer(plain, dp(11), dp(11));

        ImageView iv = new ImageView(context);
        iv.setImageDrawable(ThemePalette.createShareIcon(context, fg, iconDp));
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(iconDp), dp(iconDp)));
        box.addView(iv);

        TextView tv = new TextView(context);
        tv.setText("Share");
        tv.setTextColor(fg);
        tv.setTextSize(12.5f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setIncludeFontPadding(false);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = dp(6);
        tv.setLayoutParams(tlp);
        box.addView(tv);

        box.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("Reddit link", url));
            }
            tv.setText("Copied!");
            tv.postDelayed(() -> tv.setText("Share"), 1600L);
        });
        return box;
    }

    private LinearLayout createPillContainer(boolean plain, int padLeft, int padRight) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        int height = plain ? dp(28) : dp(32);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, height);
        lp.rightMargin = dp(8);
        box.setLayoutParams(lp);
        if (!plain) {
            box.setPadding(padLeft, 0, padRight, 0);
            box.setBackground(ThemePalette.pill(theme.btnBg, dp(999)));
        } else {
            box.setPadding(dp(4), 0, dp(8), 0);
        }
        return box;
    }

    private View buildDivider() {
        View hr = new View(context);
        hr.setBackgroundColor(theme.border);
        hr.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
        return hr;
    }

    private static boolean isDirectImageUrl(String url) {
        return url != null && DIRECT_IMAGE_URL.matcher(url).find();
    }

    private int dp(float v) {
        return ThemePalette.dp(context, v);
    }
}
