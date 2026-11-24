package dev.jkcarino.extension.reddit.frontpage.frostfeed;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

import dev.jkcarino.extension.reddit.frontpage.frostfeed.ui.FrostFeedDialogFragment;
import dev.jkcarino.extension.reddit.frontpage.frostfeed.ui.ThemePalette;

/**
 * Bytecode injection entry points for the FrostFeed Archive Profile View patch.
 */
@SuppressWarnings("unused")
public final class FrostFeedHook {

    private static final String ARCHIVE_PILL_TAG = "frostfeed_archive_pill";
    private static final Map<Object, String> PROFILE_SCREEN_USERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static WeakReference<View> activePillRef = new WeakReference<>(null);
    private static volatile String lastViewedUsername = null;

    private FrostFeedHook() {
    }

    public static boolean isValidUsername(String username) {
        if (username == null) return false;
        String clean = stripUserPrefix(username);
        if (clean.isEmpty()) return false;
        String lower = clean.toLowerCase(Locale.US);
        return !"me".equals(lower) && !"anonymous".equals(lower) && !"[deleted]".equals(lower);
    }

    public static String stripUserPrefix(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.startsWith("u/") || s.startsWith("U/")) s = s.substring(2);
        if (s.startsWith("/u/") || s.startsWith("/U/")) s = s.substring(3);
        if (s.startsWith("u_")) s = s.substring(2);
        return s.trim();
    }

    /**
     * Injected into ProfileDetailsScreen(Bundle args) constructor.
     */
    public static void registerProfileScreen(Object screen, Bundle args) {
        if (screen == null || args == null) return;
        String raw = args.getString("args_username");
        String username = stripUserPrefix(raw);
        if (isValidUsername(username)) {
            PROFILE_SCREEN_USERS.put(screen, username);
        }
    }

    /**
     * Injected into BaseScreen.onAttach(View).
     */
    public static void onScreenAttached(Object screen, View view) {
        if (screen == null || view == null) return;
        String username = PROFILE_SCREEN_USERS.get(screen);
        if (isValidUsername(username)) {
            onProfileViewAttached(view, username);
        }
    }

    /**
     * Injected into BaseScreen.onDetach(View).
     */
    public static void onScreenDetached(Object screen, View view) {
        if (screen == null) return;
        if (PROFILE_SCREEN_USERS.containsKey(screen)) {
            onProfileViewDetached();
        }
    }

    /**
     * Injected into ComposeScreen view creation (LayoutInflater, ViewGroup) -> View.
     * Automatically attaches/detaches the native Archive pill when a ProfileDetailsScreen
     * view enters or leaves the window.
     */
    public static void onComposeScreenViewCreated(Object screen, View view) {
        if (screen == null || view == null) return;
        final String username = PROFILE_SCREEN_USERS.get(screen);
        if (!isValidUsername(username)) return;

        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                onProfileViewAttached(v, username);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                onProfileViewDetached();
            }
        });
        if (view.isAttachedToWindow()) {
            onProfileViewAttached(view, username);
        }
    }

    /**
     * Direct hook entry point when opening the Archive view for a given username.
     */
    public static void openArchive(Context context, String rawUsername) {
        String username = stripUserPrefix(rawUsername);
        if (!isValidUsername(username)) return;
        Activity activity = findActivity(context);
        if (activity != null) {
            FrostFeedDialogFragment.show(activity, username);
        }
    }

    /**
     * Records the username currently being viewed on a profile screen and attaches
     * a native Reddit-styled Archive pill button to the host root container.
     */
    public static void onProfileViewAttached(View rootView, String rawUsername) {
        if (rootView == null) return;
        String username = stripUserPrefix(rawUsername);
        if (!isValidUsername(username)) {
            detachArchivePill();
            return;
        }
        lastViewedUsername = username;

        Activity activity = findActivity(rootView.getContext());
        if (activity == null) return;

        rootView.post(() -> attachArchivePill(activity, username));
    }

    /**
     * Removes the Archive pill when leaving the profile screen.
     */
    public static void onProfileViewDetached() {
        detachArchivePill();
        lastViewedUsername = null;
    }

    public static String getLastViewedUsername() {
        return lastViewedUsername;
    }

    private static void attachArchivePill(Activity activity, String username) {
        if (activity == null || activity.isFinishing()) return;
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        if (decor == null) return;

        View existing = decor.findViewWithTag(ARCHIVE_PILL_TAG);
        if (existing instanceof TextView) {
            existing.setVisibility(View.VISIBLE);
            existing.setOnClickListener(v -> FrostFeedDialogFragment.show(activity, username));
            activePillRef = new WeakReference<>(existing);
            return;
        }

        ThemePalette theme = ThemePalette.from(activity);
        TextView pill = new TextView(activity);
        pill.setTag(ARCHIVE_PILL_TAG);
        pill.setText("Archive");
        pill.setTextColor(Color.WHITE);
        pill.setTextSize(13f);
        pill.setTypeface(Typeface.DEFAULT_BOLD);
        pill.setPadding(
                ThemePalette.dp(activity, 16),
                ThemePalette.dp(activity, 9),
                ThemePalette.dp(activity, 16),
                ThemePalette.dp(activity, 9)
        );
        pill.setBackground(ThemePalette.pill(theme.orange, ThemePalette.dp(activity, 999)));
        pill.setElevation(ThemePalette.dp(activity, 6));
        pill.setOnClickListener(v -> FrostFeedDialogFragment.show(activity, username));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END
        );
        lp.setMargins(
                0,
                0,
                ThemePalette.dp(activity, 16),
                ThemePalette.dp(activity, 116)
        );
        decor.addView(pill, lp);
        activePillRef = new WeakReference<>(pill);
    }

    private static void detachArchivePill() {
        View pill = activePillRef.get();
        if (pill != null) {
            pill.post(() -> {
                ViewGroup parent = (ViewGroup) pill.getParent();
                if (parent != null) {
                    parent.removeView(pill);
                }
            });
            activePillRef.clear();
        }
    }

    private static Activity findActivity(Context context) {
        Context cur = context;
        while (cur instanceof ContextWrapper) {
            if (cur instanceof Activity) return (Activity) cur;
            cur = ((ContextWrapper) cur).getBaseContext();
        }
        return null;
    }
}
