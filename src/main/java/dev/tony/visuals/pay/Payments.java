package dev.tony.visuals.pay;

import dev.tony.visuals.Config;
import dev.tony.visuals.mixin.ChatComponentAccessor;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DonutSMP bet helper. When someone pays you at least your minimum ("Name paid you $5M."), the chat line gets
 * [✔] and [✖] buttons and, for amounts ending in .1 / .2, a [Paper] / [BJ] tag. The same person paying the same
 * amount again stacks onto one line: "Name paid you [5] $5M". Click ✔ when they win: the line turns lime and shows what
 * to pay back (double their bet); for a stack you pick how many they won first. Click ✖ when they lose: the line turns
 * red. Everything happens on your screen only.
 */
public final class Payments {
    private Payments() {}

    /**
     * "Name paid you $5M" / "$1.1m" / "$750K" / "$1,250,000", found anywhere in the line, so prefixes, symbols, ranks,
     * small-caps lettering, extra spaces and full stops don't stop it from matching.
     */
    private static final Pattern PAID = Pattern.compile("([A-Za-z0-9_.]{2,17})[^A-Za-z0-9_.$]{0,4}\\s*paid\\s+you\\s*:?\\s*\\$\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([KkMmBbTt]?)(?![A-Za-z])", Pattern.CASE_INSENSITIVE);

    /** "You paid Name $ 10.2M": money going out, for the tracker. */
    private static final Pattern PAID_OUT = Pattern.compile("you\\s+paid\\s+([A-Za-z0-9_.]{2,17})\\s*:?\\s*\\$\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([KkMmBbTt]?)(?![A-Za-z])", Pattern.CASE_INSENSITIVE);

    private static double amount(String number, String suffix) {
        double mult = switch (suffix.toUpperCase(Locale.ROOT)) {
            case "K" -> 1e3;
            case "M" -> 1e6;
            case "B" -> 1e9;
            case "T" -> 1e12;
            default -> 1;
        };
        return Double.parseDouble(number.replace(",", "")) * mult;
    }

    /** The last few lines the server sent, shown on the Gamble page so a mismatch is easy to spot. */
    private static final java.util.Deque<String> RECENT = new java.util.ArrayDeque<>();
    private static volatile String lastMatch = "";

    public static String recent() {
        synchronized (RECENT) {
            return RECENT.isEmpty() ? "nothing yet" : RECENT.peekLast();
        }
    }

    public static String lastMatch() {
        return lastMatch;
    }

    /** Turns fancy small-caps / full-width letters into normal ones and tidies spaces. */
    static String normalise(String s) {
        String from = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";
        String to = "abcdefghijklmnopqrstuvwxyz";
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int k = from.indexOf(c);
            if (k >= 0) c = to.charAt(k);
            else if (c >= '\uFF01' && c <= '\uFF5E') c = (char) (c - 0xFEE0);   // full-width
            else if (c == '\u00A0' || c == '\u2009' || c == '\u202F') c = ' ';
            else if (c == '\uFE69') c = '$';   // small dollar sign
            b.append(c);
        }
        return b.toString().replaceAll("\\s+", " ").trim();
    }

    /** One payment line (possibly stacked). */
    static final class Entry {
        final int id;
        final String player;
        final String shown;      // amount as DonutSMP wrote it, e.g. "5.1M"
        final double bet;        // bet without the Paper / BJ marker, e.g. 5,000,000 (used for the minimum / maximum)
        final double win;        // what a win is doubled from: the full amount for Paper / BJ (5.1M pays 10.2M), else the bet
        final String tag;        // "Paper", "BJ" or ""
        int count = 1;
        GuiMessage line;

        Entry(int id, String player, String shown, double bet, double win, String tag) {
            this.id = id;
            this.player = player;
            this.shown = shown;
            this.bet = bet;
            this.win = win;
            this.tag = tag;
        }
    }

    private static final Map<Integer, Entry> ENTRIES = new HashMap<>();
    private static int nextId = 1;
    /** Latest still-open line per player + amount, for stacking. */
    private static final Map<String, Entry> OPEN = new HashMap<>();

    public static void init() {
        Auction.init();
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (overlay) return true;
            try {
                return !handle(message);
            } catch (Throwable e) {
                return true;   // never lose a chat message because of us
            }
        });
        // Some servers send it as a chat message instead of a system message.
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, time) -> {
            try {
                return !handle(message);
            } catch (Throwable e) {
                return true;
            }
        });
    }

    /** Returns true when the message was a payment we took over. Public for the automated test. */
    public static boolean handle(Component message) {
        Config.Gamble cfg = Config.get().gamble;
        String raw = message.getString();
        synchronized (RECENT) {
            RECENT.addLast(raw);
            while (RECENT.size() > 5) RECENT.removeFirst();
        }
        String clean = normalise(raw);
        Matcher out = PAID_OUT.matcher(clean);
        if (out.find()) {
            Tracker.add(-amount(out.group(2), out.group(3)));
            return false;
        }
        Matcher m = PAID.matcher(clean);
        if (!m.find()) return false;
        String player = m.group(1);
        if (player.equalsIgnoreCase("you")) return false;
        double paid = amount(m.group(2), m.group(3));
        Tracker.add(paid);
        // During an auction every payment is a bid, not a bet.
        if (Auction.active()) {
            Auction.bid(player, paid);
            lastMatch = player + " bid $" + m.group(2) + m.group(3) + " on the " + Auction.item();
            return false;
        }
        if (!cfg.enabled) return false;
        String number = m.group(2).replace(",", "");
        String suffix = m.group(3).toUpperCase(Locale.ROOT);
        double mult = switch (suffix) {
            case "K" -> 1e3;
            case "M" -> 1e6;
            case "B" -> 1e9;
            case "T" -> 1e12;
            default -> 1;
        };
        String tag = "";
        String whole = number;
        int dot = number.indexOf('.');
        if (dot >= 0) {
            String decimals = number.substring(dot + 1);
            // The single decimal that marks the game (set on the Gamble page; .1 Paper and .2 BJ by default).
            if (decimals.equals(String.valueOf(cfg.paperTag))) tag = "Paper";
            else if (decimals.equals(String.valueOf(cfg.bjTag))) tag = "BJ";
            if (!tag.isEmpty()) whole = number.substring(0, dot);
        }
        double bet = Double.parseDouble(whole) * mult;
        boolean over = cfg.maximum > 0 && bet > cfg.maximum;
        lastMatch = player + " paid $" + m.group(2) + m.group(3) + (bet < cfg.minimum ? " (below your minimum)" : over ? " (above your maximum)" : "");
        if (bet < cfg.minimum || over) return false;
        String shown = m.group(2) + m.group(3);

        Minecraft mc = Minecraft.getInstance();
        String key = player.toLowerCase(Locale.ROOT) + "|" + shown;
        Entry open = cfg.stack ? OPEN.get(key) : null;
        if (open != null && ENTRIES.containsKey(open.id)) {
            open.count++;
            remove(open);
            open.line = add(open, build(open));
            return true;
        }
        boolean tagged = cfg.tags && !tag.isEmpty();
        // A Paper / BJ win pays double the whole amount, marker included: 5.1M [Paper] pays 10.2M.
        double win = tagged ? Double.parseDouble(number) * mult : bet;
        Entry e = new Entry(nextId++, player, shown, bet, win, tagged ? tag : "");
        ENTRIES.put(e.id, e);
        OPEN.put(key, e);
        e.line = add(e, build(e));
        return true;
    }

    // ------------------------------------------------------------ chat lines

    private static MutableComponent text(String s, ChatFormatting... f) {
        return Component.literal(s).withStyle(f);
    }

    private static Style button(int id, String action, String hover) {
        return Style.EMPTY
            .withClickEvent(new ClickEvent.Custom(Identifier.fromNamespaceAndPath("flopvisuals", "pay/" + id + "/" + action), Optional.empty()))
            .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover)));
    }

    /** Open line: "Name paid you [5] $5M [Paper] [✔] [✖]". */
    private static Component build(Entry e) {
        MutableComponent c = Component.empty();
        c.append(text(e.player, ChatFormatting.WHITE)).append(text(" paid you ", ChatFormatting.GRAY));
        if (e.count > 1) c.append(text("[" + e.count + "] ", ChatFormatting.YELLOW, ChatFormatting.BOLD));
        c.append(text("$" + e.shown, ChatFormatting.GREEN));
        if (!e.tag.isEmpty()) c.append(text(" [" + e.tag + "]", e.tag.equals("BJ") ? ChatFormatting.AQUA : ChatFormatting.GOLD));
        double pay = e.win * Config.get().gamble.payout;
        String winHover = e.count > 1 ? "They won some: pick how many" : "They won: pay $" + money(pay);
        c.append(Component.literal(" "));
        c.append(Component.literal("[✔]").withStyle(button(e.id, "win", winHover).withColor(ChatFormatting.GREEN).withBold(true)));
        c.append(Component.literal(" "));
        c.append(Component.literal("[✖]").withStyle(button(e.id, "lose", "They lost").withColor(ChatFormatting.RED).withBold(true)));
        return c;
    }

    /** Settled line: the same text, buttons gone, lime (won) or red (lost). */
    private static Component settled(Entry e, int wins) {
        boolean won = wins > 0;
        ChatFormatting col = won ? ChatFormatting.GREEN : ChatFormatting.RED;
        StringBuilder s = new StringBuilder(e.player).append(" paid you ");
        if (e.count > 1) s.append("[").append(e.count).append("] ");
        s.append("$").append(e.shown);
        if (!e.tag.isEmpty()) s.append(" [").append(e.tag).append("]");
        MutableComponent c = Component.literal(s.toString()).withStyle(col);
        if (won) {
            double pay = e.win * Config.get().gamble.payout * wins;
            String how = e.count > 1 ? " [" + wins + "/" + e.count + " won]" : "";
            c.append(Component.literal(how + " [Pay $" + money(pay) + "]").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        }
        return c;
    }

    private static GuiMessage add(Entry e, Component c) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.getChat().addMessage(c);
        List<GuiMessage> all = ((ChatComponentAccessor) mc.gui.getChat()).flopvisuals$allMessages();
        return all.isEmpty() ? null : all.get(0);
    }

    private static void remove(Entry e) {
        if (e.line == null) return;
        var chat = (ChatComponentAccessor) Minecraft.getInstance().gui.getChat();
        if (chat.flopvisuals$allMessages().remove(e.line)) chat.flopvisuals$refresh();
    }

    private static void replace(Entry e, Component c) {
        var chat = (ChatComponentAccessor) Minecraft.getInstance().gui.getChat();
        List<GuiMessage> all = chat.flopvisuals$allMessages();
        int i = e.line == null ? -1 : all.indexOf(e.line);
        GuiMessage next = new GuiMessage(e.line == null ? Minecraft.getInstance().gui.getGuiTicks() : e.line.addedTime(), c, null, null);
        if (i >= 0) all.set(i, next);
        else all.add(0, next);
        e.line = next;
        chat.flopvisuals$refresh();
    }

    // ------------------------------------------------------------ clicks

    /** A chat button was clicked. Returns true if it was one of ours. */
    public static boolean click(Identifier id) {
        if (!id.getNamespace().equals("flopvisuals") || !id.getPath().startsWith("pay/")) return false;
        String[] parts = id.getPath().split("/");
        if (parts.length < 3) return true;
        Entry e;
        try {
            e = ENTRIES.get(Integer.parseInt(parts[1]));
        } catch (NumberFormatException ex) {
            return true;
        }
        if (e == null) return true;
        if (parts[2].equals("lose")) {
            settle(e, 0);
        } else if (e.count > 1) {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new WinsScreen(mc.screen, e.player, e.count, e.win * Config.get().gamble.payout, wins -> settle(e, wins)));
        } else {
            settle(e, 1);
        }
        return true;
    }

    static void settle(Entry e, int wins) {
        if (!ENTRIES.containsKey(e.id)) return;
        ENTRIES.remove(e.id);
        OPEN.values().removeIf(x -> x == e);
        replace(e, settled(e, wins));
    }

    /** 10000000 → "10M", 2500000 → "2.5M", 750000 → "750K". */
    public static String money(double v) {
        String[] units = {"", "K", "M", "B", "T"};
        int u = 0;
        while (Math.abs(v) >= 1000 && u < units.length - 1) {
            v /= 1000;
            u++;
        }
        String s = String.format(Locale.ROOT, "%.2f", v).replaceAll("\\.?0+$", "");
        return s + units[u];
    }

    /** "1M", "500k", "2.5b", "1000000" → number, or -1. */
    public static double parse(String s) {
        Matcher m = Pattern.compile("^\\s*\\$?([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([KkMmBbTt]?)\\s*$").matcher(s);
        if (!m.matches()) return -1;
        double v = Double.parseDouble(m.group(1).replace(",", ""));
        return switch (m.group(2).toUpperCase(Locale.ROOT)) {
            case "K" -> v * 1e3;
            case "M" -> v * 1e6;
            case "B" -> v * 1e9;
            case "T" -> v * 1e12;
            default -> v;
        };
    }
}
