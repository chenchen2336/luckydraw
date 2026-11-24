package cn.blockforge.generated.fabric263wishesfabri.game;

import cn.blockforge.generated.fabric263wishesfabri.config.LotteryConfig;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.phys.Vec2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * 执行奖品里配置的 commands。
 * <p>
 * 关键点：命令默认<b>不是</b>用玩家自己的权限去跑的，而是以玩家为执行者、临时借用管理员权限，
 * 所以普通玩家抽到 "give %player% diamond 1" 这类奖品也能真的拿到手，不需要服主临时给他 OP。
 * 万一某条命令只有控制台才能跑，还会自动退一步改用控制台身份再试一次。
 */
public final class PrizeCommands {

    private static final Logger LOG = LoggerFactory.getLogger("luckydraw");

    /** 自动：先以玩家身份借用管理员权限，不行再用控制台。 */
    public static final String AUTO = "auto";
    /** 以玩家身份执行，但临时借用管理员权限（@s、~ 相对坐标都能用）。 */
    public static final String OPERATOR = "operator";
    /** 以服务器控制台身份执行。 */
    public static final String CONSOLE = "console";
    /** 完全按玩家本人的权限执行（玩家没权限时命令会失败）。 */
    public static final String PLAYER = "player";

    private PrizeCommands() {
    }

    /** 把配置里写的身份名字统一成上面四个值之一，写错了就当自动。 */
    public static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) return AUTO;
        String text = raw.trim().toLowerCase(Locale.ROOT);
        return switch (text) {
            case CONSOLE, "server", "control", "控制台", "服务器" -> CONSOLE;
            case PLAYER, "self", "own", "玩家", "本人" -> PLAYER;
            case OPERATOR, "op", "admin", "管理员" -> OPERATOR;
            case AUTO, "自动" -> AUTO;
            default -> AUTO;
        };
    }

    /** /lottery info 里显示的说明文字。 */
    public static String label(String mode) {
        return switch (sanitize(mode)) {
            case CONSOLE -> "服务器控制台";
            case PLAYER -> "玩家本人（受玩家权限限制）";
            case OPERATOR -> "以玩家身份 + 管理员权限";
            default -> "自动（先借管理员权限，不行再用控制台）";
        };
    }

    /**
     * 执行一条奖品命令。
     *
     * @param mode 可选，单条奖品的覆盖身份；传 null / 空就用全局配置
     * @return 全部尝试都失败时返回 false
     */
    public static boolean run(MinecraftServer server, ServerPlayer player, String raw, String mode) {
        String command = clean(raw, player);
        if (command.isEmpty()) return true;

        LotteryConfig.CommandsCfg cfg = LotteryConfig.get().prizeCommands;
        String wanted = mode == null || mode.isBlank() ? cfg.runAs : mode;
        String effective = sanitize(wanted);

        if (AUTO.equals(effective)) {
            String asPlayer = execute(server, player, command, OPERATOR, cfg.silent);
            if (asPlayer == null) return true;
            String asConsole = execute(server, player, command, CONSOLE, cfg.silent);
            if (asConsole == null) return true;
            LOG.warn("[抽奖机] 奖品命令执行失败：{}（以玩家身份：{}；以控制台：{}）", command, asPlayer, asConsole);
            return false;
        }

        String problem = execute(server, player, command, effective, cfg.silent);
        if (problem == null) return true;
        LOG.warn("[抽奖机] 奖品命令执行失败：{}（身份 {}：{}）；可以在奖品里加 \"commandAs\": \"console\" 再试试",
                command, effective, problem);
        return false;
    }

    /** 去掉多余空白、开头的斜杠，并把 %player% 换成玩家名。 */
    private static String clean(String raw, ServerPlayer player) {
        if (raw == null || raw.isBlank()) return "";
        String command = raw.trim();
        while (command.startsWith("/")) {
            command = command.substring(1).trim();
        }
        if (command.isEmpty()) return "";
        return command.replace("%player%", player.getName().getString());
    }

    /** 跑一次；成功返回 null，失败返回原因。 */
    private static String execute(MinecraftServer server, ServerPlayer player, String command,
                                  String mode, boolean silent) {
        try {
            CommandSourceStack source = sourceFor(server, player, mode);
            if (silent) source = source.withSuppressedOutput();
            ParseResults<CommandSourceStack> parsed = server.getCommands().getDispatcher().parse(command, source);
            // 权限不够或命令写错时，Brigadier 会把错误藏起来，这里先自己检查一遍，才能知道到底发没发出去
            Commands.validateParseResults(parsed);
            server.getCommands().performCommand(parsed, command);
            return null;
        } catch (Throwable t) {
            return t.toString();
        }
    }

    private static CommandSourceStack sourceFor(MinecraftServer server, ServerPlayer player, String mode) {
        if (CONSOLE.equals(mode)) {
            // 控制台没有实体，但把坐标/维度挪到玩家身上，命令里写 ~ 时不会跑到世界原点去
            return server.createCommandSourceStack()
                    .withLevel((ServerLevel) player.level())
                    .withPosition(player.position())
                    .withRotation(new Vec2(player.getXRot(), player.getYRot()))
                    .withPermission(PermissionSet.ALL_PERMISSIONS);
        }
        CommandSourceStack base = player.createCommandSourceStack();
        if (PLAYER.equals(mode)) return base;
        return base.withPermission(PermissionSet.ALL_PERMISSIONS);
    }
}
