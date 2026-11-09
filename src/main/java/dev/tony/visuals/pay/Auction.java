package dev.tony.visuals.pay;

import dev.tony.visuals.Config;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * DonutSMP auction: start one for an item, and every "Name paid you $X" before the countdown ends is a bid (several
 * payments from the same player add up). When time runs out the highest total wins: "<IGN> wins the Netherite Ingot!",
 * and you get a private list of who to refund. Nothing is ever paid automatically.
 */
public final class Auction {
    private Auction() {}

    public record Bid(String player, double total) {}

    private static String item;
    private static long endsAt, startedAt;
    private static final Map<String, Bid> BIDS = new LinkedHashMap<>();
    /** Last finished auction, shown on the HUD for a few seconds. */
    private static String result;
    private static long resultUntil;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (item != null && System.currentTimeMillis() >= endsAt) finish();
        });
    }

    public static boolean active() {
        return item != null;
    }

    public static String item() {
        return item;
    }

    public static float secondsLeft() {
        return item == null ? 0 : Math.max(0, (endsAt - System.currentTimeMillis()) / 1000f);
    }

    /** 1 at the start, 0 when time is up. */
    public static float fraction() {
        return item == null ? 0 : Math.max(0, Math.min(1, (endsAt - System.currentTimeMillis()) / (float) Math.max(1, endsAt - startedAt)));
    }

    public static String result() {
        return System.currentTimeMillis() < resultUntil ? result : null;
    }

    /** Highest first. */
    public static synchronized List<Bid> ranking() {
        List<Bid> out = new ArrayList<>(BIDS.values());
        out.sort((a, b) -> Double.compare(b.total(), a.total()));
        return out;
    }

    public static synchronized void start(String what) {
        if (what == null || what.isBlank()) return;
        item = what.trim();
        BIDS.clear();
        startedAt = System.currentTimeMillis();
        endsAt = startedAt + Config.get().auction.seconds * 1000L;
        result = null;
        local(Component.literal("Auction started: ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(item).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
            .append(Component.literal(" · " + Config.get().auction.seconds + "s").withStyle(ChatFormatting.GRAY)));
        if (Config.get().auction.announce) {
            say("Auction: " + item + "! Pay me within " + Config.get().auction.seconds + "s, highest total wins.");
        }
    }

    /** Ends early with no winner. */
    public static synchronized void cancel() {
        if (item == null) return;
        local(Component.literal("Auction for " + item + " cancelled.").withStyle(ChatFormatting.RED));
        refunds(ranking(), null);
        item = null;
        BIDS.clear();
    }

    public static synchronized void bid(String player, double amount) {
        if (item == null) return;
        String key = player.toLowerCase(Locale.ROOT);
        Bid b = BIDS.get(key);
        BIDS.put(key, new Bid(player, (b == null ? 0 : b.total()) + amount));
    }

    private static synchronized void finish() {
        if (item == null) return;
        List<Bid> r = ranking();
        String what = item;
        item = null;
        BIDS.clear();
        if (r.isEmpty()) {
            result = "No bids for the " + what;
            local(Component.literal(result + ".").withStyle(ChatFormatting.GRAY));
        } else {
            Bid win = r.get(0);
            result = win.player() + " wins the " + what + "!";
            local(Component.literal(win.player()).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                .append(Component.literal(" wins the ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(what).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal("! ($" + Payments.money(win.total()) + ")").withStyle(ChatFormatting.WHITE)));
            if (Config.get().auction.announce) say(result);
            refunds(r, win);
        }
        resultUntil = System.currentTimeMillis() + 8000;
    }

    /** Private list of everyone who didn't win and what they paid. */
    private static void refunds(List<Bid> bids, Bid winner) {
        for (Bid b : bids) {
            if (b == winner) continue;
            local(Component.literal("  Refund ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(b.player()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" $" + Payments.money(b.total())).withStyle(ChatFormatting.YELLOW)));
        }
    }

    private static void local(MutableComponent c) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.gui.getChat().addMessage(Component.literal("[Auction] ").withStyle(ChatFormatting.LIGHT_PURPLE).append(c)));
    }

    private static void say(String s) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null && mc.getConnection() != null) mc.getConnection().sendChat(s);
        });
    }
}
