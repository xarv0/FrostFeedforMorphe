package dev.jkcarino.extension.reddit.frontpage.frostfeed.model;

import java.util.Collections;
import java.util.List;

/**
 * Immutable data models mirroring FrostFeed's shaped Arctic Shift objects.
 */
public final class ArchiveModels {

    private ArchiveModels() {
    }

    public enum FeedSection {
        OVERVIEW("mixed", "Overview"),
        POSTS("posts", "Posts"),
        COMMENTS("comments", "Comments");

        public final String key;
        public final String label;

        FeedSection(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    public enum SortOrder {
        NEW("desc", "New"),
        OLD("asc", "Old");

        public final String apiValue;
        public final String label;

        SortOrder(String apiValue, String label) {
            this.apiValue = apiValue;
            this.label = label;
        }
    }

    public enum ViewDensity {
        CARD("card", "Card"),
        COMPACT("compact", "Compact");

        public final String key;
        public final String label;

        ViewDensity(String key, String label) {
            this.key = key;
            this.label = label;
        }

        public static ViewDensity fromKey(String key) {
            return "compact".equals(key) ? COMPACT : CARD;
        }
    }

    public interface ArchiveItem {
        String getKind(); // "post" or "comment"
        String getId();
        long getCreatedUtc();
        String getSubreddit();
        String getSubredditId();
        String getAuthor();
        String getPermalink();

        default String getDedupeKey() {
            return getKind() + ":" + getId();
        }
    }

    public static final class FlairPart {
        public final boolean isEmoji;
        public final String text; // for text parts, or alt shortcode for emoji parts
        public final String url;  // non-null for emoji parts

        public FlairPart(boolean isEmoji, String text, String url) {
            this.isEmoji = isEmoji;
            this.text = text;
            this.url = url;
        }
    }

    public static final class GalleryItem {
        public final String url;
        public final String caption;

        public GalleryItem(String url, String caption) {
            this.url = url;
            this.caption = caption;
        }
    }

    public static final class CrosspostInfo {
        public final String subreddit;
        public final String author;
        public final String title;
        public final long createdUtc;
        public final Integer score;
        public final Integer numComments;
        public final String permalink;
        public final boolean over18;

        public CrosspostInfo(
                String subreddit,
                String author,
                String title,
                long createdUtc,
                Integer score,
                Integer numComments,
                String permalink,
                boolean over18
        ) {
            this.subreddit = subreddit;
            this.author = author;
            this.title = title;
            this.createdUtc = createdUtc;
            this.score = score;
            this.numComments = numComments;
            this.permalink = permalink;
            this.over18 = over18;
        }
    }

    public static final class ArchivedPost implements ArchiveItem {
        public final String id;
        public final String author;
        public final String title;
        public final String body;
        public final String url;
        public final long createdUtc;
        public final String subreddit;
        public final String subredditId;
        public final String permalink;
        public final Integer score;
        public final Integer numComments;
        public final boolean over18;
        public final boolean spoiler;
        public final boolean stickied;
        public final boolean locked;
        public final boolean archived;
        public final boolean edited;
        public final boolean isSelf;
        public final boolean isVideo;
        public final boolean isGallery;
        public final String postHint;
        public final String thumbnail;
        public final String previewUrl;
        public final String videoUrl;
        public final Integer videoDuration;
        public final List<GalleryItem> gallery;
        public final CrosspostInfo crosspost;
        public final String distinguished;
        public final String flair;
        public final List<FlairPart> flairRich;
        public final String flairBg;
        public final String flairTextColor;
        public final String domain;
        public final boolean removed;

        public ArchivedPost(
                String id,
                String author,
                String title,
                String body,
                String url,
                long createdUtc,
                String subreddit,
                String subredditId,
                String permalink,
                Integer score,
                Integer numComments,
                boolean over18,
                boolean spoiler,
                boolean stickied,
                boolean locked,
                boolean archived,
                boolean edited,
                boolean isSelf,
                boolean isVideo,
                boolean isGallery,
                String postHint,
                String thumbnail,
                String previewUrl,
                String videoUrl,
                Integer videoDuration,
                List<GalleryItem> gallery,
                CrosspostInfo crosspost,
                String distinguished,
                String flair,
                List<FlairPart> flairRich,
                String flairBg,
                String flairTextColor,
                String domain,
                boolean removed
        ) {
            this.id = id;
            this.author = author;
            this.title = title;
            this.body = body != null ? body : "";
            this.url = url;
            this.createdUtc = createdUtc;
            this.subreddit = subreddit;
            this.subredditId = subredditId;
            this.permalink = permalink;
            this.score = score;
            this.numComments = numComments;
            this.over18 = over18;
            this.spoiler = spoiler;
            this.stickied = stickied;
            this.locked = locked;
            this.archived = archived;
            this.edited = edited;
            this.isSelf = isSelf;
            this.isVideo = isVideo;
            this.isGallery = isGallery;
            this.postHint = postHint;
            this.thumbnail = thumbnail;
            this.previewUrl = previewUrl;
            this.videoUrl = videoUrl;
            this.videoDuration = videoDuration;
            this.gallery = gallery != null ? Collections.unmodifiableList(gallery) : null;
            this.crosspost = crosspost;
            this.distinguished = distinguished;
            this.flair = flair;
            this.flairRich = flairRich != null ? Collections.unmodifiableList(flairRich) : null;
            this.flairBg = flairBg;
            this.flairTextColor = flairTextColor;
            this.domain = domain;
            this.removed = removed;
        }

        @Override public String getKind() { return "post"; }
        @Override public String getId() { return id; }
        @Override public long getCreatedUtc() { return createdUtc; }
        @Override public String getSubreddit() { return subreddit; }
        @Override public String getSubredditId() { return subredditId; }
        @Override public String getAuthor() { return author; }
        @Override public String getPermalink() { return permalink; }
    }

    public static final class ArchivedComment implements ArchiveItem {
        public final String id;
        public final String author;
        public final String body;
        public final long createdUtc;
        public final String subreddit;
        public final String subredditId;
        public final String permalink;
        public final Integer score;
        public final String distinguished;
        public final String authorFlair;
        public final List<FlairPart> authorFlairRich;
        public final String authorFlairBg;
        public final String authorFlairColor;
        public final boolean isSubmitter;
        public final String parentId;
        public final boolean edited;
        public final boolean stickied;
        public final boolean locked;
        public final boolean archived;
        public final String linkId;
        public final String linkTitle;
        public final String linkPermalink;
        public final String linkAuthor;
        public final boolean removed;

        public ArchivedComment(
                String id,
                String author,
                String body,
                long createdUtc,
                String subreddit,
                String subredditId,
                String permalink,
                Integer score,
                String distinguished,
                String authorFlair,
                List<FlairPart> authorFlairRich,
                String authorFlairBg,
                String authorFlairColor,
                boolean isSubmitter,
                String parentId,
                boolean edited,
                boolean stickied,
                boolean locked,
                boolean archived,
                String linkId,
                String linkTitle,
                String linkPermalink,
                String linkAuthor,
                boolean removed
        ) {
            this.id = id;
            this.author = author;
            this.body = body != null ? body : "";
            this.createdUtc = createdUtc;
            this.subreddit = subreddit;
            this.subredditId = subredditId;
            this.permalink = permalink;
            this.score = score;
            this.distinguished = distinguished;
            this.authorFlair = authorFlair;
            this.authorFlairRich = authorFlairRich != null
                    ? Collections.unmodifiableList(authorFlairRich)
                    : null;
            this.authorFlairBg = authorFlairBg;
            this.authorFlairColor = authorFlairColor;
            this.isSubmitter = isSubmitter;
            this.parentId = parentId;
            this.edited = edited;
            this.stickied = stickied;
            this.locked = locked;
            this.archived = archived;
            this.linkId = linkId;
            this.linkTitle = linkTitle;
            this.linkPermalink = linkPermalink;
            this.linkAuthor = linkAuthor;
            this.removed = removed;
        }

        @Override public String getKind() { return "comment"; }
        @Override public String getId() { return id; }
        @Override public long getCreatedUtc() { return createdUtc; }
        @Override public String getSubreddit() { return subreddit; }
        @Override public String getSubredditId() { return subredditId; }
        @Override public String getAuthor() { return author; }
        @Override public String getPermalink() { return permalink; }
    }

    public static final class SearchPage {
        public final List<ArchiveItem> items;
        public final boolean hasMore;
        public final Long nextCursor;

        public SearchPage(List<ArchiveItem> items, boolean hasMore, Long nextCursor) {
            this.items = Collections.unmodifiableList(items);
            this.hasMore = hasMore;
            this.nextCursor = nextCursor;
        }
    }

    public static final class UserStats {
        public final String author;
        public final String id;
        public final Long posts;
        public final Long comments;
        public final Long totalKarma;
        public final Long firstPost;
        public final Long firstComment;

        public UserStats(
                String author,
                String id,
                Long posts,
                Long comments,
                Long totalKarma,
                Long firstPost,
                Long firstComment
        ) {
            this.author = author;
            this.id = id;
            this.posts = posts;
            this.comments = comments;
            this.totalKarma = totalKarma;
            this.firstPost = firstPost;
            this.firstComment = firstComment;
        }
    }

    public static final class SubredditInfo {
        public static final SubredditInfo EMPTY = new SubredditInfo(null, null, null);

        public final String icon;
        public final String fallback;
        public final String color;

        public SubredditInfo(String icon, String fallback, String color) {
            this.icon = icon;
            this.fallback = fallback;
            this.color = color;
        }
    }
}
