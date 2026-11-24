package dev.jkcarino.extension.reddit.frontpage.frostfeed.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.jkcarino.extension.reddit.frontpage.frostfeed.api.ArcticShiftClient;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.data.DualCursorStreamMerger;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.model.ArchiveModels.ArchiveItem;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.model.ArchiveModels.FeedSection;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.model.ArchiveModels.SortOrder;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.model.ArchiveModels.SubredditInfo;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.model.ArchiveModels.UserStats;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.model.ArchiveModels.ViewDensity;

/**
 * Full-screen native Android Archive profile viewer powered by Arctic Shift.
 */
@SuppressWarnings("deprecation")
public final class FrostFeedDialogFragment extends DialogFragment {

    private static final String TAG = "frostfeed_archive_dialog";
    private static final String ARG_USERNAME = "username";
    private static final String PREFS_NAME = "frostfeed_prefs";
    private static final String KEY_DENSITY = "density";

    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<String> pendingSubIds = new HashSet<>();

    private String username;
    private ThemePalette theme;
    private FeedSection currentSection = FeedSection.OVERVIEW;
    private SortOrder currentSort = SortOrder.NEW;
    private ViewDensity currentDensity = ViewDensity.CARD;

    private DualCursorStreamMerger merger;
    private ArchiveFeedAdapter adapter;
    private int generation = 0;
    private boolean loading = false;
    private boolean hasMore = true;
    private boolean fetchedOnce = false;

    private TextView statsTv;
    private TextView sortBtn;
    private TextView densityBtn;
    private final List<TextView> tabButtons = new ArrayList<>();
    private FrameLayout footerContainer;

    public static void show(Activity activity, String username) {
        if (activity == null || activity.isFinishing() || username == null || username.isEmpty()) {
            return;
        }
        FragmentManager fm = activity.getFragmentManager();
        if (fm == null) return;
        if (fm.findFragmentByTag(TAG) != null) return;

        FrostFeedDialogFragment fragment = new FrostFeedDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_USERNAME, username);
        fragment.setArguments(args);
        try {
            fragment.show(fm, TAG);
        } catch (IllegalStateException ignored) {
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Context ctx = getActivity();
        Bundle args = getArguments();
        this.username = args != null ? args.getString(ARG_USERNAME, "") : "";
        this.theme = ThemePalette.from(ctx);
        this.currentDensity = loadDensity(ctx);

        int style = theme.dark
                ? android.R.style.Theme_DeviceDefault_NoActionBar
                : android.R.style.Theme_DeviceDefault_Light_NoActionBar;
        Dialog dialog = new Dialog(ctx, style);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View root = buildRootView(ctx);
        dialog.setContentView(root);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setStatusBarColor(theme.bg);
            window.setNavigationBarColor(theme.bg);
        }

        loadUserStats();
        resetAndLoad();
        return dialog;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private View buildRootView(Context ctx) {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(theme.bg);

        // 1. Top App Bar
        root.addView(buildTopBar(ctx));

        // 2. Section Tabs: Overview | Posts | Comments
        root.addView(buildTabStrip(ctx));

        // 3. Controls bar: Sort (New/Old), View (Card/Compact), Stats
        root.addView(buildControlsBar(ctx));

        // 4. Feed ListView
        ListView listView = new ListView(ctx);
        listView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setBackgroundColor(theme.bg);

        footerContainer = new FrameLayout(ctx);
        listView.addFooterView(footerContainer, null, false);

        adapter = new ArchiveFeedAdapter(ctx, theme, currentDensity);
        listView.setAdapter(adapter);

        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisible, int visibleCount, int totalCount) {
                if (!loading && hasMore && totalCount > 0
                        && (firstVisible + visibleCount) >= (totalCount - 6)) {
                    loadNextPage();
                }
            }
        });

        root.addView(listView);
        return root;
    }

    private View buildTopBar(Context ctx) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 14), dp(ctx, 10));
        bar.setBackgroundColor(theme.bg);

        TextView back = new TextView(ctx);
        back.setText("←");
        back.setTextColor(theme.strong);
        back.setTextSize(20f);
        back.setPadding(dp(ctx, 6), dp(ctx, 4), dp(ctx, 12), dp(ctx, 4));
        back.setOnClickListener(v -> dismissAllowingStateLoss());
        bar.addView(back);

        LinearLayout titleBox = new LinearLayout(ctx);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView userTv = new TextView(ctx);
        userTv.setText("u/" + username);
        userTv.setTextColor(theme.strong);
        userTv.setTextSize(16f);
        userTv.setTypeface(Typeface.DEFAULT_BOLD);
        userTv.setSingleLine(true);
        userTv.setEllipsize(TextUtils.TruncateAt.END);
        titleBox.addView(userTv);

        statsTv = new TextView(ctx);
        statsTv.setTextColor(theme.weak);
        statsTv.setTextSize(11.5f);
        statsTv.setVisibility(View.GONE);
        titleBox.addView(statsTv);
        bar.addView(titleBox);

        TextView badge = new TextView(ctx);
        badge.setText("Archive");
        badge.setTextColor(theme.admin);
        badge.setTextSize(11.5f);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setPadding(dp(ctx, 10), dp(ctx, 4), dp(ctx, 10), dp(ctx, 4));
        badge.setBackground(ThemePalette.pill(theme.badgeBg, dp(ctx, 999)));
        badge.setOnClickListener(v -> new AlertDialog.Builder(ctx)
                .setTitle("FrostFeed Archive")
                .setMessage("Archived posts and comments served from arctic-shift.photon-reddit.com. "
                        + "No Reddit API calls or session cookies are used. "
                        + "Tapping any item opens the thread in Reddit.")
                .setPositiveButton("OK", null)
                .show());
        bar.addView(badge);

        return bar;
    }

    private View buildTabStrip(Context ctx) {
        LinearLayout strip = new LinearLayout(ctx);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setPadding(dp(ctx, 12), dp(ctx, 4), dp(ctx, 12), dp(ctx, 6));
        strip.setBackgroundColor(theme.bg);

        tabButtons.clear();
        for (FeedSection section : FeedSection.values()) {
            TextView tab = new TextView(ctx);
            tab.setText(section.label);
            tab.setTextSize(13.5f);
            tab.setTypeface(Typeface.DEFAULT_BOLD);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(ctx, 14), dp(ctx, 7), dp(ctx, 14), dp(ctx, 7));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(ctx, 8);
            tab.setLayoutParams(lp);
            tab.setTag(section);
            tab.setOnClickListener(v -> {
                if (currentSection != section) {
                    currentSection = section;
                    syncTabHighlight(ctx);
                    resetAndLoad();
                }
            });
            tabButtons.add(tab);
            strip.addView(tab);
        }
        syncTabHighlight(ctx);
        return strip;
    }

    private void syncTabHighlight(Context ctx) {
        for (TextView tab : tabButtons) {
            boolean active = tab.getTag() == currentSection;
            tab.setTextColor(active ? theme.strong : theme.weak);
            tab.setBackground(active
                    ? ThemePalette.pill(theme.btnBg, dp(ctx, 999))
                    : null);
        }
    }

    private View buildControlsBar(Context ctx) {
        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.VERTICAL);

        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(ctx, 14), dp(ctx, 6), dp(ctx, 14), dp(ctx, 8));

        sortBtn = new TextView(ctx);
        sortBtn.setTextSize(12.5f);
        sortBtn.setTypeface(Typeface.DEFAULT_BOLD);
        sortBtn.setTextColor(theme.strong);
        sortBtn.setPadding(dp(ctx, 12), dp(ctx, 6), dp(ctx, 12), dp(ctx, 6));
        sortBtn.setBackground(ThemePalette.pill(theme.btnBg, dp(ctx, 999)));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.rightMargin = dp(ctx, 8);
        sortBtn.setLayoutParams(slp);
        sortBtn.setOnClickListener(v -> {
            currentSort = (currentSort == SortOrder.NEW) ? SortOrder.OLD : SortOrder.NEW;
            updateControlLabels();
            resetAndLoad();
        });
        bar.addView(sortBtn);

        densityBtn = new TextView(ctx);
        densityBtn.setTextSize(12.5f);
        densityBtn.setTypeface(Typeface.DEFAULT_BOLD);
        densityBtn.setTextColor(theme.strong);
        densityBtn.setPadding(dp(ctx, 12), dp(ctx, 6), dp(ctx, 12), dp(ctx, 6));
        densityBtn.setBackground(ThemePalette.pill(theme.btnBg, dp(ctx, 999)));
        densityBtn.setOnClickListener(v -> {
            currentDensity = (currentDensity == ViewDensity.CARD)
                    ? ViewDensity.COMPACT
                    : ViewDensity.CARD;
            saveDensity(ctx, currentDensity);
            updateControlLabels();
            if (adapter != null) adapter.setDensity(currentDensity);
        });
        bar.addView(densityBtn);

        updateControlLabels();
        wrap.addView(bar);

        View hr = new View(ctx);
        hr.setBackgroundColor(theme.border);
        hr.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(ctx, 1))));
        wrap.addView(hr);
        return wrap;
    }

    private void updateControlLabels() {
        if (sortBtn != null) {
            sortBtn.setText("Sort: " + currentSort.label);
        }
        if (densityBtn != null) {
            densityBtn.setText("View: " + currentDensity.label);
        }
    }

    // ------------------------------------------------------------------ //
    // Data Loading & Footer States
    // ------------------------------------------------------------------ //

    private void resetAndLoad() {
        generation++;
        loading = false;
        hasMore = true;
        fetchedOnce = false;
        merger = new DualCursorStreamMerger(
                ArcticShiftClient.getInstance(), username, currentSection, currentSort);
        if (adapter != null) adapter.clearItems();
        loadNextPage();
    }

    private void loadNextPage() {
        if (loading || !hasMore || merger == null) return;
        loading = true;
        final int gen = generation;
        final DualCursorStreamMerger activeMerger = merger;
        renderFooterLoading();

        executor.execute(() -> {
            DualCursorStreamMerger.PageResult res = activeMerger.loadNextPage();
            mainHandler.post(() -> {
                if (gen != generation || !isAdded()) return;
                loading = false;
                fetchedOnce = true;
                hasMore = res.hasMore;

                if (!res.newItems.isEmpty()) {
                    adapter.addItems(res.newItems);
                    hydrateSubIcons(res.newItems);
                }

                if (res.error != null) {
                    renderFooterError(res.error);
                } else if (adapter.getCount() == 0 && fetchedOnce) {
                    renderFooterEmpty();
                } else if (!hasMore && adapter.getCount() > 0) {
                    renderFooterEnd();
                } else {
                    footerContainer.removeAllViews();
                }
            });
        });
    }

    private void loadUserStats() {
        executor.execute(() -> {
            try {
                UserStats stats = ArcticShiftClient.getInstance().fetchUserStats(username);
                if (stats == null) return;
                mainHandler.post(() -> {
                    if (!isAdded() || statsTv == null) return;
                    List<String> bits = new ArrayList<>();
                    if (stats.posts != null) bits.add(ThemePalette.formatNumber(stats.posts) + " posts");
                    if (stats.comments != null) bits.add(ThemePalette.formatNumber(stats.comments) + " comments");
                    Long earliest = null;
                    if (stats.firstPost != null && stats.firstComment != null) {
                        earliest = Math.min(stats.firstPost, stats.firstComment);
                    } else if (stats.firstPost != null) {
                        earliest = stats.firstPost;
                    } else if (stats.firstComment != null) {
                        earliest = stats.firstComment;
                    }
                    if (earliest != null && earliest > 0) {
                        Calendar cal = Calendar.getInstance();
                        cal.setTimeInMillis(earliest * 1000L);
                        bits.add("since " + cal.get(Calendar.YEAR));
                    }
                    if (!bits.isEmpty()) {
                        statsTv.setText(TextUtils.join(" · ", bits));
                        statsTv.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception ignored) {
                // Stats are optional.
            }
        });
    }

    private void hydrateSubIcons(List<ArchiveItem> items) {
        List<String> toFetch = new ArrayList<>();
        for (ArchiveItem item : items) {
            String subId = item.getSubredditId();
            if (subId == null || subId.isEmpty()) continue;
            String key = subId.toLowerCase(Locale.US);
            SubredditInfo cached = ArcticShiftClient.getInstance().getCachedSubreddit(key);
            if (cached != null) {
                Map<String, SubredditInfo> single = new java.util.HashMap<>();
                single.put(key, cached);
                adapter.putSubredditInfo(single);
            } else if (pendingSubIds.add(key)) {
                toFetch.add(key);
            }
        }
        if (toFetch.isEmpty()) return;

        executor.execute(() -> {
            Map<String, SubredditInfo> map = ArcticShiftClient.getInstance().fetchSubreddits(toFetch);
            mainHandler.post(() -> {
                for (String id : toFetch) pendingSubIds.remove(id);
                if (!isAdded() || map == null || map.isEmpty()) return;
                adapter.putSubredditInfo(map);
            });
        });
    }

    private void renderFooterLoading() {
        Context ctx = getActivity();
        if (ctx == null || footerContainer == null) return;
        footerContainer.removeAllViews();

        if (adapter != null && adapter.getCount() == 0) {
            LinearLayout skels = new LinearLayout(ctx);
            skels.setOrientation(LinearLayout.VERTICAL);
            for (int i = 0; i < 4; i++) {
                LinearLayout row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14));
                row.addView(skelBlock(ctx, dp(ctx, 140), dp(ctx, 12), 0));
                row.addView(skelBlock(ctx, ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 18), dp(ctx, 8)));
                row.addView(skelBlock(ctx, dp(ctx, 220), dp(ctx, 14), dp(ctx, 6)));
                skels.addView(row);
            }
            footerContainer.addView(skels);
        } else {
            LinearLayout box = new LinearLayout(ctx);
            box.setOrientation(LinearLayout.HORIZONTAL);
            box.setGravity(Gravity.CENTER);
            box.setPadding(dp(ctx, 16), dp(ctx, 18), dp(ctx, 16), dp(ctx, 18));
            ProgressBar pb = new ProgressBar(ctx);
            LinearLayout.LayoutParams pblp = new LinearLayout.LayoutParams(dp(ctx, 20), dp(ctx, 20));
            pblp.rightMargin = dp(ctx, 10);
            pb.setLayoutParams(pblp);
            TextView tv = new TextView(ctx);
            tv.setText("Loading more…");
            tv.setTextColor(theme.weak);
            tv.setTextSize(13f);
            box.addView(pb);
            box.addView(tv);
            footerContainer.addView(box);
        }
    }

    private View skelBlock(Context ctx, int w, int h, int topMargin) {
        View v = new View(ctx);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.topMargin = topMargin;
        v.setLayoutParams(lp);
        v.setBackground(ThemePalette.pill(theme.skel, dp(ctx, 6)));
        return v;
    }

    private void renderFooterError(String message) {
        Context ctx = getActivity();
        if (ctx == null || footerContainer == null) return;
        footerContainer.removeAllViews();

        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(ctx, 24), dp(ctx, 24), dp(ctx, 24), dp(ctx, 24));

        TextView msg = new TextView(ctx);
        msg.setText(message);
        msg.setTextColor(theme.fg);
        msg.setTextSize(13.5f);
        msg.setGravity(Gravity.CENTER);
        box.addView(msg);

        TextView retry = new TextView(ctx);
        retry.setText("Retry");
        retry.setTextColor(Color.WHITE);
        retry.setTextSize(13f);
        retry.setTypeface(Typeface.DEFAULT_BOLD);
        retry.setPadding(dp(ctx, 18), dp(ctx, 8), dp(ctx, 18), dp(ctx, 8));
        retry.setBackground(ThemePalette.pill(theme.orange, dp(ctx, 999)));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(ctx, 12);
        retry.setLayoutParams(rlp);
        retry.setOnClickListener(v -> {
            hasMore = true;
            loadNextPage();
        });
        box.addView(retry);
        footerContainer.addView(box);
    }

    private void renderFooterEmpty() {
        Context ctx = getActivity();
        if (ctx == null || footerContainer == null) return;
        footerContainer.removeAllViews();

        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(ctx, 28), dp(ctx, 40), dp(ctx, 28), dp(ctx, 40));

        TextView title = new TextView(ctx);
        title.setText("u/" + username + " hasn't posted yet");
        title.setTextColor(theme.strong);
        title.setTextSize(15f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        box.addView(title);

        TextView sub = new TextView(ctx);
        sub.setText("No archived " + (currentSection == FeedSection.OVERVIEW
                ? "posts or comments" : currentSection.label.toLowerCase(Locale.US))
                + " found on Arctic Shift.");
        sub.setTextColor(theme.weak);
        sub.setTextSize(13f);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(ctx, 6), 0, 0);
        box.addView(sub);

        footerContainer.addView(box);
    }

    private void renderFooterEnd() {
        Context ctx = getActivity();
        if (ctx == null || footerContainer == null) return;
        footerContainer.removeAllViews();

        TextView tv = new TextView(ctx);
        tv.setText("You've reached the end of the archive.");
        tv.setTextColor(theme.weak);
        tv.setTextSize(12.5f);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(ctx, 16), dp(ctx, 20), dp(ctx, 16), dp(ctx, 28));
        footerContainer.addView(tv);
    }

    private static ViewDensity loadDensity(Context ctx) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            return ViewDensity.fromKey(sp.getString(KEY_DENSITY, "card"));
        } catch (Exception e) {
            return ViewDensity.CARD;
        }
    }

    private static void saveDensity(Context ctx, ViewDensity density) {
        try {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_DENSITY, density.key)
                    .apply();
        } catch (Exception ignored) {
        }
    }

    private static int dp(Context ctx, float v) {
        return ThemePalette.dp(ctx, v);
    }
}
