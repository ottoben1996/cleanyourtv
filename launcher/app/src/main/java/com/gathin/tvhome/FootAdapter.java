package com.gathin.tvhome;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * One row of match cards: competition and channel, the status (kick-off time, minute, full time),
 * the two clubs and their score when there is one. Focus behaves like a shelf card; clicking opens
 * the football app.
 */
final class FootAdapter extends RecyclerView.Adapter<FootAdapter.Holder> {

    interface Listener {
        void onItemFocused(int position);

        void onItemClicked(FootBall.Match match);

        /** A D-pad key on a focused card; return true when the row handled it. */
        boolean onItemKey(int position, int keyCode);
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final List<FootBall.Match> items;
    private final boolean reducedMotion;
    private final Listener listener;
    private final int cardWidthPx;
    private final int cardHeightPx;
    private final int cornerRadiusPx;

    FootAdapter(List<FootBall.Match> items, boolean reducedMotion, int cornerRadiusPx,
                int cardWidthPx, int cardHeightPx, Listener listener) {
        this.items = items;
        this.reducedMotion = reducedMotion;
        this.listener = listener;
        this.cornerRadiusPx = cornerRadiusPx;
        this.cardWidthPx = cardWidthPx;
        this.cardHeightPx = cardHeightPx;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Holder h = new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_football, parent, false));
        ViewGroup.LayoutParams lp = h.card.getLayoutParams();
        lp.width = cardWidthPx;
        lp.height = cardHeightPx;
        h.card.setLayoutParams(lp);
        h.card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadiusPx);
            }
        });
        h.card.setClipToOutline(true);
        return h;
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Context ctx = h.itemView.getContext();
        FootBall.Match match = items.get(position);
        String competition = competition(ctx, match);
        h.competition.setText(match.channel.isEmpty()
                ? competition : competition + " · " + match.channel);

        boolean live = match.live();
        h.liveDot.setVisibility(live ? View.VISIBLE : View.GONE);
        h.status.setText(status(ctx, match));
        h.status.setTextColor(live ? 0xFFFFFFFF : 0x99FFFFFF);

        h.home.setText(match.home);
        h.away.setText(match.away);
        if (match.scored() && match.homeScore >= 0 && match.awayScore >= 0) {
            h.homeScore.setText(String.valueOf(match.homeScore));
            h.awayScore.setText(String.valueOf(match.awayScore));
            h.homeScore.setVisibility(View.VISIBLE);
            h.awayScore.setVisibility(View.VISIBLE);
        } else {
            h.homeScore.setVisibility(View.INVISIBLE);
            h.awayScore.setVisibility(View.INVISIBLE);
        }

        h.itemView.setOnFocusChangeListener((v, focused) -> {
            animate(h, focused);
            if (focused && listener != null) listener.onItemFocused(h.getBindingAdapterPosition());
        });
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClicked(match);
        });
        h.itemView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != android.view.KeyEvent.ACTION_DOWN || listener == null) return false;
            int at = h.getBindingAdapterPosition();
            return at != RecyclerView.NO_POSITION && listener.onItemKey(at, keyCode);
        });
    }

    /** The status line: how far the match is, or when it starts (with "Tomorrow" when needed). */
    private static String status(Context ctx, FootBall.Match match) {
        if ("PAUSED".equals(match.status)) return ctx.getString(R.string.football_half_time);
        if (match.live()) {
            return match.minute > 0
                    ? ctx.getString(R.string.football_minute, match.minute)
                    : ctx.getString(R.string.football_live);
        }
        if (match.finished()) return ctx.getString(R.string.football_finished);
        if ("POSTPONED".equals(match.status)) return ctx.getString(R.string.football_postponed);
        if ("SUSPENDED".equals(match.status)) return ctx.getString(R.string.football_suspended);
        if ("CANCELLED".equals(match.status)) return ctx.getString(R.string.football_cancelled);
        ZoneId zone = ZoneId.systemDefault();
        Instant at = Instant.ofEpochMilli(match.kickoff);
        String time = TIME.format(at.atZone(zone));
        LocalDate day = at.atZone(zone).toLocalDate();
        if (day.isAfter(LocalDate.now(zone))) {
            return ctx.getString(R.string.football_tomorrow, time);
        }
        return time;
    }

    /** The competition's French/English name, falling back to what the API called it. */
    private static String competition(Context ctx, FootBall.Match match) {
        switch (match.competition) {
            case "FL1":
                return ctx.getString(R.string.competition_fl1);
            case "CL":
                return ctx.getString(R.string.competition_cl);
            case "PL":
                return ctx.getString(R.string.competition_pl);
            case "PD":
                return ctx.getString(R.string.competition_pd);
            case "EL":
                return ctx.getString(R.string.competition_el);
            default:
                return match.competitionName == null || match.competitionName.isEmpty()
                        ? match.competition : match.competitionName;
        }
    }

    private void animate(Holder h, boolean focused) {
        h.card.setForeground(focused
                ? h.card.getContext().getDrawable(R.drawable.foot_card_focus) : null);
        float scale = focused ? 1.04f : 1f;
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

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final FrameLayout card;
        final TextView competition, status, home, away, homeScore, awayScore;
        final View liveDot;

        Holder(View v) {
            super(v);
            card = v.findViewById(R.id.footCard);
            competition = v.findViewById(R.id.footCompetition);
            status = v.findViewById(R.id.footStatus);
            liveDot = v.findViewById(R.id.footLiveDot);
            home = v.findViewById(R.id.footHome);
            away = v.findViewById(R.id.footAway);
            homeScore = v.findViewById(R.id.footHomeScore);
            awayScore = v.findViewById(R.id.footAwayScore);
        }
    }
}
