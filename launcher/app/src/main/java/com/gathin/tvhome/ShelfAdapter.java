package com.gathin.tvhome;

import android.graphics.Color;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * One card row of a dock shelf. Cards behave like the app tiles: they scale up a little when
 * focused (no spring, no glass — a row is secondary), and clicking one follows its deep link.
 */
final class ShelfAdapter extends RecyclerView.Adapter<ShelfAdapter.Holder> {

    interface Listener {
        void onItemFocused(int position);

        void onItemClicked(TvShelf.Item item);

        /** A D-pad key on a focused card; return true when the shelf handled it. */
        boolean onItemKey(int position, int keyCode);
    }

    private final List<TvShelf.Item> items;
    private final PosterLoader posters;       // null when poster downloads are off
    private final boolean reducedMotion;
    private final Listener listener;
    private final int cardWidthPx;
    private final int cardHeightPx;
    private final int posterWidthPx;
    private final int posterHeightPx;
    private final int cornerRadiusPx;

    ShelfAdapter(List<TvShelf.Item> items, PosterLoader posters, boolean reducedMotion, int cornerRadiusPx,
                 int cardWidthPx, int cardHeightPx, Listener listener) {
        this.items = items;
        this.posters = posters;
        this.reducedMotion = reducedMotion;
        this.listener = listener;
        this.cornerRadiusPx = cornerRadiusPx;
        this.cardWidthPx = cardWidthPx;
        this.cardHeightPx = cardHeightPx;
        // Posters decode a little larger than the card so the focus zoom stays sharp.
        this.posterWidthPx = Math.round(cardWidthPx * 1.1f);
        this.posterHeightPx = Math.round(cardHeightPx * 1.1f);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Holder holder = new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_shelf, parent, false));
        // The layout carries one design (a 96×144 dp Stremio poster); a source with other artwork
        // (a square Spotify cover, a 16:9 YouTube thumbnail) resizes the card and the title here.
        ViewGroup.LayoutParams cardLp = holder.card.getLayoutParams();
        cardLp.width = cardWidthPx;
        cardLp.height = cardHeightPx;
        holder.card.setLayoutParams(cardLp);
        ViewGroup.LayoutParams titleLp = holder.title.getLayoutParams();
        titleLp.width = cardWidthPx;
        holder.title.setLayoutParams(titleLp);
        holder.letter.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.min(cardWidthPx, cardHeightPx) * 0.28f);
        holder.card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadiusPx);
            }
        });
        holder.card.setClipToOutline(true);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        TvShelf.Item item = items.get(position);
        h.title.setText(item.title);
        if (item.badge == null) {
            h.badge.setVisibility(View.GONE);
        } else {
            h.badge.setText(item.badge);
            h.badge.setVisibility(View.VISIBLE);
        }
        if (item.progress > 0) {
            h.progress.setProgress(item.progress);
            h.progress.setVisibility(View.VISIBLE);
        } else {
            h.progress.setVisibility(View.GONE);
        }
        // Placeholder: a dim colour from the title plus its first letter, so a poster that does not
        // load (offline, dead link) still reads as a card.
        h.letter.setText(item.title.isEmpty() ? "" : item.title.substring(0, 1));
        h.letter.setVisibility(View.VISIBLE);
        h.poster.setImageDrawable(null);
        h.poster.setTag(null);                     // no stale poster for a recycled view
        h.poster.setBackgroundColor(placeholderColor(item.title));
        if (posters != null && item.posterUri != null) {
            posters.into(item.posterUri, h.poster, posterWidthPx, posterHeightPx,
                    () -> h.letter.setVisibility(View.GONE));
        }
        h.itemView.setOnFocusChangeListener((v, focused) -> {
            animate(h, focused);
            if (focused && listener != null) listener.onItemFocused(h.getBindingAdapterPosition());
        });
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClicked(item);
        });
        h.itemView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN || listener == null) return false;
            int at = h.getBindingAdapterPosition();
            return at != RecyclerView.NO_POSITION && listener.onItemKey(at, keyCode);
        });
    }

    private void animate(Holder h, boolean focused) {
        h.title.setAlpha(focused ? 1f : 0.7f);
        h.card.setForeground(focused ? h.card.getContext().getDrawable(R.drawable.shelf_card_focus) : null);
        float scale = focused ? 1.06f : 1f;
        if (reducedMotion) {
            h.card.setScaleX(scale);
            h.card.setScaleY(scale);
        } else {
            h.card.animate().scaleX(scale).scaleY(scale)
                    .setDuration(focused ? 160 : 120)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    /** A dim, title-derived colour behind a poster that has not loaded yet. */
    static int placeholderColor(String title) {
        float hue = title == null || title.isEmpty() ? 20f : Math.floorMod(title.hashCode(), 360);
        return Color.HSVToColor(new float[]{hue, 0.35f, 0.20f});
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final FrameLayout card;
        final ImageView poster;
        final TextView letter, badge, title;
        final ProgressBar progress;

        Holder(View v) {
            super(v);
            card = v.findViewById(R.id.shelfCard);
            poster = v.findViewById(R.id.shelfPoster);
            letter = v.findViewById(R.id.shelfLetter);
            badge = v.findViewById(R.id.shelfBadge);
            title = v.findViewById(R.id.shelfTitle);
            progress = v.findViewById(R.id.shelfProgress);
        }
    }
}
