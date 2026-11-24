package cn.blockforge.generated.fabric263wishesfabri.game;

import cn.blockforge.generated.fabric263wishesfabri.config.LotteryConfig;
import cn.blockforge.generated.fabric263wishesfabri.data.PlayerData;
import cn.blockforge.generated.fabric263wishesfabri.data.PlayerStore;
import cn.blockforge.generated.fabric263wishesfabri.economy.Economies;
import cn.blockforge.generated.fabric263wishesfabri.economy.EconomyBridge;
import cn.blockforge.generated.fabric263wishesfabri.fx.LotteryFx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 抽奖主流程：先扣费 → 播动画（环形粒子 + 三段音效）→ 开奖发奖 → 记保底计数。
 */
public final class DrawManager {

    private static final Logger LOG = LoggerFactory.getLogger("luckydraw");
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** 正在等扣费结果的玩家，防止快速连点重复扣钱。 */
    private static final java.util.Set<UUID> PENDING = new java.util.HashSet<>();
    private static final Random RANDOM = new Random();

    private DrawManager() {
    }

    private static final class Session {
        final UUID player;
        final boolean ten;
        final int totalTicks;
        final BigDecimal price;
        int ticksLeft;

        Session(UUID player, boolean ten, int totalTicks, BigDecimal price) {
            this.player = player;
            this.ten = ten;
            this.totalTicks = totalTicks;
            this.price = price;
            this.ticksLeft = totalTicks;
        }
    }

    public static boolean isDrawing(UUID player) {
        return SESSIONS.containsKey(player);
    }

    /** 玩家点了单抽 / 十连。 */
    public static void requestDraw(ServerPlayer player, boolean ten) {
        LotteryConfig cfg = LotteryConfig.get();
        if (!cfg.enabled) {
            message(player, "抽奖机现在关着，问一下管理员吧。");
            return;
        }
        UUID id = player.getUUID();
        if (SESSIONS.containsKey(id) || PENDING.contains(id)) {
            message(player, "上一轮还在转，等它开完奖。");
            return;
        }
        List<LotteryConfig.Prize> pool = ten ? cfg.pools.ten : cfg.pools.single;
        if (pool == null || pool.isEmpty()) {
            message(player, "奖池是空的，请管理员检查配置。");
            return;
        }

        BigDecimal price = ten ? cfg.price.ten : cfg.price.single;
        MinecraftServer server = player.level().getServer();
        EconomyBridge economy = Economies.get();
        PENDING.add(id);

        economy.charge(id, price).whenComplete((paid, error) -> server.executeIfPossible(() -> {
            PENDING.remove(id);
            ServerPlayer online = server.getPlayerList().getPlayer(id);
            if (error != null) {
                LOG.error("[抽奖机] 扣费失败：{}", error.toString());
                if (online != null) message(online, "扣费出错了，这次没有抽，请稍后再试。");
                return;
            }
            if (!Boolean.TRUE.equals(paid)) {
                if (online != null) {
                    message(online, "余额不足，这次需要 " + economy.format(price) + "。");
                    if (economy.isInternal()) {
                        BigDecimal balance = economy.cachedBalance(id);
                        if (balance != null) {
                            message(online, "内置钱包余额 " + economy.format(balance)
                                    + "，管理员可以用 /lottery give 发币。");
                        }
                    }
                }
                return;
            }
            if (online == null || online.isRemoved()) {
                // 扣完钱人掉线了，退回去，避免白扣
                economy.grant(id, price);
                LOG.info("[抽奖机] 玩家在扣费后掉线，已退还 {}。", price.toPlainString());
                return;
            }
            SESSIONS.put(id, new Session(id, ten, Math.max(1, cfg.effects.animationTicks), price));
            online.closeContainer();
            LotteryFx.start(online);
        }));
    }

    /** 每个服务器 tick 推进一次动画。 */
    public static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        Iterator<Session> iterator = SESSIONS.values().iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(session.player);
            if (player == null || player.isRemoved()) {
                iterator.remove();
                refund(session);
                continue;
            }
            session.ticksLeft--;
            int elapsed = session.totalTicks - session.ticksLeft;
            LotteryFx.ring(player, elapsed);
            LotteryFx.rollTick(player, elapsed, session.ticksLeft, session.totalTicks);
            if (session.ticksLeft <= 0) {
                iterator.remove();
                finish(server, player, session);
            }
        }
    }

    /** 服务器关闭前把还在转的抽奖立刻结算，或者退款，别让玩家的钱白扣。 */
    public static void flush(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        for (Session session : new ArrayList<>(SESSIONS.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(session.player);
            if (player != null && !player.isRemoved()) {
                finish(server, player, session);
            } else {
                refund(session);
            }
        }
        SESSIONS.clear();
    }

    private static void refund(Session session) {
        Economies.get().grant(session.player, session.price);
        LOG.info("[抽奖机] 玩家 {} 在开奖前离开，已退还 {}。",
                session.player, session.price.toPlainString());
    }

    private static void finish(MinecraftServer server, ServerPlayer player, Session session) {
        LotteryConfig cfg = LotteryConfig.get();
        List<LotteryConfig.Prize> pool = session.ten ? cfg.pools.ten : cfg.pools.single;
        PlayerData data = PlayerStore.get(player.getUUID());
        int rolls = session.ten ? 10 : 1;

        List<LotteryConfig.Prize> results = new ArrayList<>(rolls);
        for (int i = 0; i < rolls; i++) {
            LotteryConfig.Prize prize = rollOne(pool, data, cfg);
            if (prize != null) results.add(prize);
        }
        data.setTotalDraws(data.getTotalDraws() + results.size());
        PlayerStore.save();

        for (LotteryConfig.Prize prize : results) {
            grant(server, player, prize);
        }
        boolean anyRare = results.stream().anyMatch(prize -> prize.rare);
        LotteryFx.finish(player, anyRare);
        sendResults(player, results, session.ten, cfg);
        if (anyRare && cfg.pity.announce) {
            announce(server, player, results);
        }
    }

    /** 抽一次：保底到点就只在稀有奖里挑。 */
    private static LotteryConfig.Prize rollOne(List<LotteryConfig.Prize> pool, PlayerData data, LotteryConfig cfg) {
        if (pool == null || pool.isEmpty()) return null;
        boolean forceRare = cfg.pity.enabled && cfg.pity.threshold > 0
                && data.getPity() + 1 >= cfg.pity.threshold;
        List<LotteryConfig.Prize> candidates = pool;
        if (forceRare) {
            List<LotteryConfig.Prize> rares = pool.stream().filter(prize -> prize.rare).toList();
            if (!rares.isEmpty()) candidates = rares;
        }
        LotteryConfig.Prize picked = weightedPick(candidates);
        if (picked == null) return null;
        if (picked.rare) {
            data.setPity(0);
        } else {
            data.setPity(data.getPity() + 1);
        }
        return picked;
    }

    private static LotteryConfig.Prize weightedPick(List<LotteryConfig.Prize> pool) {
        double total = PrizeText.totalWeight(pool);
        if (total <= 0) {
            return pool.get(RANDOM.nextInt(pool.size()));
        }
        double roll = RANDOM.nextDouble() * total;
        for (LotteryConfig.Prize prize : pool) {
            roll -= Math.max(0.0D, prize.weight);
            if (roll <= 0.0D) return prize;
        }
        return pool.get(pool.size() - 1);
    }

    private static void grant(MinecraftServer server, ServerPlayer player, LotteryConfig.Prize prize) {
        if (prize.money != null && prize.money.signum() > 0) {
            Economies.get().grant(player.getUUID(), prize.money);
        }
        ItemStack stack = PrizeText.itemStack(prize);
        if (!stack.isEmpty() && !player.getInventory().add(stack)) {
            player.spawnAtLocation(player.level(), stack);
        }
        if (prize.commands != null) {
            boolean allGood = true;
            for (String raw : prize.commands) {
                if (raw == null || raw.isBlank()) continue;
                // 借用管理员权限执行，普通玩家不用 OP 也能拿到命令类奖品
                if (!PrizeCommands.run(server, player, raw, prize.commandAs)) {
                    allGood = false;
                }
            }
            if (!allGood) {
                String name = PrizeText.name(prize).getString();
                message(player, "「" + name + "」的命令奖励没发成功，已经记进服务器日志，请联系管理员。");
            }
        }
    }

    private static void sendResults(ServerPlayer player, List<LotteryConfig.Prize> results, boolean ten, LotteryConfig cfg) {
        if (results.isEmpty()) {
            message(player, "这次什么都没抽到，请管理员检查奖池配置。");
            return;
        }
        player.sendSystemMessage(Component.literal("━━━━━━ 抽奖结果 ━━━━━━")
                .withStyle(ChatFormatting.DARK_GRAY));
        if (!ten) {
            player.sendSystemMessage(Component.empty()
                    .append(Component.literal("恭喜获得：").withStyle(ChatFormatting.GOLD))
                    .append(PrizeText.nameWithCount(results.get(0))));
        } else {
            int index = 1;
            for (LotteryConfig.Prize prize : results) {
                player.sendSystemMessage(Component.empty()
                        .append(Component.literal(String.format("%2d. ", index++)).withStyle(ChatFormatting.GRAY))
                        .append(PrizeText.nameWithCount(prize)));
            }
        }
        if (cfg.pity.enabled) {
            PlayerData data = PlayerStore.get(player.getUUID());
            int left = Math.max(0, cfg.pity.threshold - data.getPity());
            player.sendSystemMessage(Component.literal("保底进度 " + data.getPity() + "/" + cfg.pity.threshold
                            + "，再 " + left + " 抽必出稀有").withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    private static void announce(MinecraftServer server, ServerPlayer player, List<LotteryConfig.Prize> results) {
        List<LotteryConfig.Prize> rares = results.stream().filter(prize -> prize.rare).toList();
        if (rares.isEmpty()) return;
        Component message = Component.empty()
                .append(Component.literal("[抽奖机] ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(player.getName().getString()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" 抽中了稀有奖励：").withStyle(ChatFormatting.GRAY))
                .append(PrizeText.nameWithCount(rares.get(0)));
        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    public static void message(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.empty()
                .append(Component.literal("[抽奖机] ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(text).withStyle(ChatFormatting.WHITE)));
    }
}
