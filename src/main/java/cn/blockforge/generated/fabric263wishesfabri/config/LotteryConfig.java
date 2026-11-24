package cn.blockforge.generated.fabric263wishesfabri.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 抽奖机配置。文件位置：config/luckydraw/lottery.json（第一次启动自动生成）。
 * <p>
 * 服务器管理员改完 JSON 后用 /lottery reload 生效，不用重启服务器。
 */
public final class LotteryConfig {

    private static final Logger LOG = LoggerFactory.getLogger("luckydraw");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static volatile LotteryConfig instance;

    // ---------- 顶层字段 ----------

    /** 总开关，关掉后右键抽奖机只提示已关闭。 */
    public boolean enabled = true;

    public Machine machine = new Machine();
    public EconomyCfg economy = new EconomyCfg();
    public Price price = new Price();
    public Pity pity = new Pity();
    public Effects effects = new Effects();
    public CommandsCfg prizeCommands = new CommandsCfg();
    public Pools pools = new Pools();
    public Ui ui = new Ui();

    // ---------- 子配置 ----------

    /** 抽奖机的触发方式：右键指定方块，或手持指定物品右键。 */
    public static final class Machine {
        /** 右键哪个原版方块打开抽奖界面，留空 = 关闭方块触发。 */
        public String block = "minecraft:lodestone";
        /** 手持哪个原版物品右键打开抽奖界面，留空 = 关闭物品触发。 */
        public String item = "minecraft:clock";
        /** 是否必须潜行（按住 Shift）才触发。 */
        public boolean requireSneak = false;
    }

    public static final class EconomyCfg {
        /** auto = 装了经济模组就用它，否则用内置钱包；savs = 强制 Savs；internal = 只用内置钱包。 */
        public String provider = "auto";
        /** 内置钱包新玩家的初始余额（只在内置钱包模式下有意义）。 */
        public BigDecimal startingBalance = new BigDecimal("1000");
        /** 内置钱包显示的货币符号。 */
        public String symbol = "币";
        /** 符号是否放在数字前面。 */
        public boolean symbolBefore = false;
    }

    public static final class Price {
        public BigDecimal single = new BigDecimal("100");
        public BigDecimal ten = new BigDecimal("900");
    }

    public static final class Pity {
        public boolean enabled = true;
        /** 累计多少抽没出稀有后，下一抽强制稀有。 */
        public int threshold = 50;
        /** 出稀有后是否全服公告。 */
        public boolean announce = true;
    }

    public static final class Effects {
        public boolean particles = true;
        public boolean sounds = true;
        /** 开奖动画时长（tick，20 tick = 1 秒）。 */
        public int animationTicks = 40;
    }

    public static final class Pools {
        /** 单抽奖池。 */
        public List<Prize> single = defaultSingle();
        /** 十连奖池（可以和单抽完全不同）。 */
        public List<Prize> ten = defaultTen();
    }

    /** 奖品里 commands 用什么身份执行——决定普通玩家能不能拿到命令类奖品。 */
    public static final class CommandsCfg {
        /**
         * auto = 先以玩家身份借用管理员权限，命令跑不了再用控制台（默认，推荐）；
         * operator = 只以玩家身份借用管理员权限；console = 只用服务器控制台身份；
         * player = 完全按玩家本人权限（玩家不是管理员时会失败）。
         */
        public String runAs = "auto";
        /** 执行完是否把命令的回显藏起来，免得聊天栏刷一堆 "已给予玩家…"。 */
        public boolean silent = true;
    }

    public static final class Ui {
        public String title = "★ 幸运抽奖机 ★";
    }

    /** 一个奖品。item / money / commands 至少写一个。 */
    public static final class Prize {
        /** 物品 id，例如 minecraft:diamond（可省略 minecraft:）。 */
        public String item;
        /** 给几个。 */
        public int count = 1;
        /** 直接发钱（走同一个经济账户）。 */
        public BigDecimal money;
        /** 权重，越大越容易出。 */
        public double weight = 1.0D;
        /** 是否算"稀有"，保底只在稀有奖里挑。 */
        public boolean rare = false;
        /** 自定义名字，不填就用物品原名。 */
        public String name;
        /** 中奖后执行的命令，%player% 会替换成玩家名；默认借用管理员权限，普通玩家也能拿到。 */
        public List<String> commands;
        /** 单独给这条奖品指定执行身份：auto / operator / console / player，不写就跟随 prizeCommands.runAs。 */
        public String commandAs;

        public boolean valid() {
            boolean hasItem = item != null && !item.isBlank();
            boolean hasMoney = money != null && money.signum() > 0;
            boolean hasCommand = commands != null && commands.stream().anyMatch(c -> c != null && !c.isBlank());
            return hasItem || hasMoney || hasCommand;
        }

        /** 物品奖品对应的实际物品，找不到返回 null。 */
        public Item resolveItem() {
            return LotteryConfig.item(item);
        }
    }

    // ---------- 存取 ----------

    public static LotteryConfig get() {
        LotteryConfig local = instance;
        if (local == null) {
            synchronized (LotteryConfig.class) {
                local = instance;
                if (local == null) {
                    local = load();
                    instance = local;
                }
            }
        }
        return local;
    }

    /** /lottery reload 用：重新读一遍文件并替换当前配置。 */
    public static synchronized void reload() {
        instance = load();
    }

    public static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("luckydraw").resolve("lottery.json");
    }

    public static LotteryConfig load() {
        Path file = file();
        try {
            if (Files.exists(file)) {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                LotteryConfig cfg = GSON.fromJson(json, LotteryConfig.class);
                if (cfg != null) {
                    cfg.normalize();
                    if (!json.contains("\"prizeCommands\"")) {
                        // 老版本的配置文件没有这一段：用默认的 auto，顺便在日志里提醒一句
                        LOG.info("[抽奖机] 配置里还没有 prizeCommands 段，奖品命令按 auto 执行（普通玩家中奖也能发出去）。");
                    }
                    return cfg;
                }
            }
            LotteryConfig fresh = new LotteryConfig();
            fresh.save();
            return fresh;
        } catch (Exception e) {
            LOG.error("[抽奖机] 读取配置失败，先用默认配置运行：{}", e.toString());
            LotteryConfig fallback = new LotteryConfig();
            fallback.normalize();
            return fallback;
        }
    }

    public void save() {
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("lottery.json.tmp");
            Files.writeString(tmp, GSON.toJson(this), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.error("[抽奖机] 保存配置失败：{}", e.toString());
        }
    }

    /** 读进来之后把可能为空/非法的字段补成能用的值。 */
    public void normalize() {
        if (machine == null) machine = new Machine();
        if (economy == null) economy = new EconomyCfg();
        if (price == null) price = new Price();
        if (pity == null) pity = new Pity();
        if (effects == null) effects = new Effects();
        if (prizeCommands == null) prizeCommands = new CommandsCfg();
        if (prizeCommands.runAs == null || prizeCommands.runAs.isBlank()) prizeCommands.runAs = "auto";
        if (ui == null) ui = new Ui();

        if (price.single == null || price.single.signum() < 0) price.single = new BigDecimal("100");
        if (price.ten == null || price.ten.signum() < 0) price.ten = new BigDecimal("900");
        if (economy.startingBalance == null) economy.startingBalance = new BigDecimal("1000");
        if (economy.symbol == null) economy.symbol = "币";
        if (economy.provider == null || economy.provider.isBlank()) economy.provider = "auto";
        if (pity.threshold <= 0) pity.threshold = 50;
        if (effects.animationTicks < 1) effects.animationTicks = 1;
        if (effects.animationTicks > 200) effects.animationTicks = 200;
        if (ui.title == null || ui.title.isBlank()) ui.title = "★ 幸运抽奖机 ★";

        if (pools == null) pools = new Pools();
        pools.single = sanitize(pools.single, defaultSingle());
        pools.ten = sanitize(pools.ten, defaultTen());
    }

    private static List<Prize> sanitize(List<Prize> incoming, List<Prize> fallback) {
        if (incoming == null || incoming.isEmpty()) return fallback;
        List<Prize> out = new ArrayList<>();
        for (Prize prize : incoming) {
            if (prize == null || !prize.valid()) continue;
            if (prize.weight <= 0.0D) continue;
            if (prize.count < 1) prize.count = 1;
            if (prize.count > 64) prize.count = 64;
            if (prize.item != null && item(prize.item) == null) continue;
            out.add(prize);
        }
        return out.isEmpty() ? fallback : out;
    }

    /** 把 "minecraft:diamond" 或 "diamond" 解析成物品；不存在返回 null。 */
    public static Item item(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String text = raw.trim();
        try {
            net.minecraft.resources.Identifier id = text.indexOf(':') >= 0
                    ? net.minecraft.resources.Identifier.parse(text)
                    : net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", text);
            return BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- 默认奖池 ----------

    private static Prize prize(String item, int count, double weight, boolean rare) {
        Prize prize = new Prize();
        prize.item = item;
        prize.count = count;
        prize.weight = weight;
        prize.rare = rare;
        return prize;
    }

    private static Prize money(String amount, double weight, boolean rare) {
        Prize prize = new Prize();
        prize.money = new BigDecimal(amount);
        prize.weight = weight;
        prize.rare = rare;
        return prize;
    }

    private static List<Prize> defaultSingle() {
        List<Prize> list = new ArrayList<>();
        list.add(prize("minecraft:bread", 6, 30, false));
        list.add(prize("minecraft:iron_ingot", 8, 24, false));
        list.add(prize("minecraft:gold_ingot", 4, 18, false));
        list.add(prize("minecraft:emerald", 3, 12, false));
        list.add(prize("minecraft:lapis_lazuli", 8, 9, false));
        list.add(prize("minecraft:diamond", 2, 8, true));
        list.add(prize("minecraft:golden_apple", 1, 5, true));
        list.add(prize("minecraft:enchanted_golden_apple", 1, 2, true));
        list.add(prize("minecraft:netherite_ingot", 1, 1, true));
        return list;
    }

    private static List<Prize> defaultTen() {
        List<Prize> list = new ArrayList<>();
        list.add(prize("minecraft:bread", 16, 26, false));
        list.add(prize("minecraft:iron_ingot", 24, 20, false));
        list.add(prize("minecraft:gold_ingot", 12, 16, false));
        list.add(prize("minecraft:emerald", 10, 12, false));
        list.add(prize("minecraft:lapis_lazuli", 24, 9, false));
        list.add(prize("minecraft:diamond", 6, 9, true));
        list.add(prize("minecraft:golden_apple", 3, 6, true));
        list.add(prize("minecraft:enchanted_golden_apple", 1, 4, true));
        list.add(prize("minecraft:netherite_ingot", 1, 2, true));
        list.add(money("500", 3, false));
        return list;
    }
}
