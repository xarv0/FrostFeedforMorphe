# FrostFeed for Morphe

[![GitHub Workflow Status (with event)](https://img.shields.io/github/actions/workflow/status/xarv0/FrostFeedforMorphe/release.yml)][gh-actions]
[![GPLv3 License](https://img.shields.io/badge/License-GPL%20v3-yellow.svg)][gpl-3.0]
![Android](https://img.shields.io/badge/Android-3DDC84?logo=android&logoColor=white)

Morphe patches for the official Reddit Android app (`com.reddit.frontpage`) that port [**FrostFeed**](https://github.com/xarv0/FrostFeed) — a native **Archive** profile viewer powered by the public [Arctic Shift](https://arctic-shift.photon-reddit.com) API — built on top of [**Adobo**](https://github.com/jkennethcarino/adobo)'s ad, tracker, and telemetry removal patches.

<a href="https://morphe.software/add-source?github=xarv0/FrostFeedforMorphe">
  <img src=".github/assets/add-to-morphe.svg" alt="Add to Morphe" height="48" />
</a>

## One-Click Add to Morphe

Tap here on Android with **Morphe Manager** installed to add this patch source automatically:

👉 **[Add FrostFeed for Morphe to Morphe Manager](https://morphe.software/add-source?github=xarv0/FrostFeedforMorphe)**

## FrostFeed Features (`Archive profile view (FrostFeed)`)

- **Native Archive View on User Profiles**: Adds a floating **Archive** button on Reddit user profile screens (including curated, hidden, or suspended profiles) matching Reddit's native light and dark themes.
- **Privacy-First Data Layer**: Queries only the public Arctic Shift API (`https://arctic-shift.photon-reddit.com/api`) — never calls Reddit's API or sends your Reddit session/cookies.
- **Overview, Posts & Comments**:
  - **Overview** interleaves posts and comments chronologically using independent dual-cursor stream merging.
  - **Posts** and **Comments** views with infinite scroll pagination, 5-minute in-memory caching, and automatic retry on 429/5xx.
- **Rich Native Rendering**:
  - **Sort** (`New` / `Old`) and **View** (`Card` / `Compact`, persisted).
  - Subreddit & user profile icons, `OP` / `MOD` / `ADMIN` badges, custom flairs with rich flair emojis, NSFW/Spoiler blur overlays, images, galleries, videos, crossposts, link cards, and Reddit Markdown (including tap-to-reveal inline spoilers).
  - Tapping any archived post or comment opens the real thread directly inside the Reddit app.
- **All Adobo Patches Included**: Includes `Remove ads and telemetry`, `Sanitize share links`, `Open external links directly`, and all other Adobo Reddit & privacy patches.

## Patches

<!-- PATCHES_START -->
> Built on top of **Adobo** + **Archive profile view (FrostFeed)** — see [PATCHES.md](PATCHES.md) for the full list.
<!-- PATCHES_END -->

## Getting Started

You'll need [Morphe Manager](https://github.com/MorpheApp/morphe-manager) or [Morphe Desktop](https://github.com/MorpheApp/morphe-desktop).

### Morphe Manager

#### Option A: One-click import

Tap **[this link](https://morphe.software/add-source?github=xarv0/FrostFeedforMorphe)** to import **FrostFeed for Morphe** directly into Morphe Manager.

#### Option B: Manual import

1. Open **Morphe Manager**.
2. Switch to the **Sources** tab, then tap the **+** icon in the top-right corner.
3. Paste the following into the _Source URL_ field:
   ```
   https://github.com/xarv0/FrostFeedforMorphe
   ```
4. Tap **Add** to import this patch source.

## Credits & License

- **FrostFeed** by [xarv0](https://github.com/xarv0/FrostFeed)
- Base ad/tracker-removal patches forked from [jkennethcarino/adobo](https://github.com/jkennethcarino/adobo)
- Licensed under the **GNU General Public License v3.0** (see [LICENSE](LICENSE))

[gh-actions]: https://github.com/xarv0/FrostFeedforMorphe/actions/workflows/release.yml
[gpl-3.0]: https://www.gnu.org/licenses/gpl-3.0.en.html
