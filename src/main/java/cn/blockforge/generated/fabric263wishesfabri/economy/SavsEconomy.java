package cn.blockforge.generated.fabric263wishesfabri.economy;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Savs Common Economy 的桥。
 * <p>
 * 用反射而不是硬依赖，原因有两个：服务器可以不装经济模组，装了的话版本新旧都能用
 * （老版本是同步返回，26.x 的版本返回 CompletableFuture，这里两种都兼容）。
 * 类名和方法名取自它的公开 API：EconomyManager.getInstance() / getBalance(UUID) /
 * removeBalance(UUID, BigDecimal) / addBalance(UUID, BigDecimal) / format(BigDecimal)。
 */
public final class SavsEconomy implements EconomyBridge {

    private static final Logger LOG = LoggerFactory.getLogger("luckydraw");
    private static final String MOD_ID = "savs-common-economy";
    private static final String MANAGER_CLASS = "savage.commoneconomy.EconomyManager";

    private final Object manager;
    private final Method getBalance;
    private final Method getCachedBalance;
    private final Method removeBalance;
    private final Method addBalance;
    private final Method format;

    private SavsEconomy(Object manager, Method getBalance, Method getCachedBalance,
                        Method removeBalance, Method addBalance, Method format) {
        this.manager = manager;
        this.getBalance = getBalance;
        this.getCachedBalance = getCachedBalance;
        this.removeBalance = removeBalance;
        this.addBalance = addBalance;
        this.format = format;
    }

    /** 装了 Savs 且 API 对得上就返回桥，否则返回 null。 */
    public static SavsEconomy tryCreate() {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) return null;
        try {
            Class<?> type = Class.forName(MANAGER_CLASS);
            Object manager = type.getMethod("getInstance").invoke(null);
            if (manager == null) return null;

            Method getBalance = find(type, "getBalance", UUID.class);
            Method removeBalance = find(type, "removeBalance", UUID.class, BigDecimal.class);
            Method addBalance = find(type, "addBalance", UUID.class, BigDecimal.class);
            if (getBalance == null || removeBalance == null || addBalance == null) {
                LOG.warn("[抽奖机] 检测到 Savs Common Economy，但接口对不上，改用内置钱包。");
                return null;
            }
            Method getCachedBalance = find(type, "getCachedBalance", UUID.class);
            Method format = find(type, "format", BigDecimal.class);
            LOG.info("[抽奖机] 已对接 Savs Common Economy{}。",
                    getCachedBalance == null ? "（无余额缓存，界面余额由命令查询）" : "");
            return new SavsEconomy(manager, getBalance, getCachedBalance, removeBalance, addBalance, format);
        } catch (Throwable t) {
            LOG.warn("[抽奖机] 对接 Savs Common Economy 失败，改用内置钱包：{}", t.toString());
            return null;
        }
    }

    private static Method find(Class<?> type, String name, Class<?>... params) {
        try {
            Method method = type.getMethod(name, params);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    @Override
    public String id() {
        return MOD_ID;
    }

    @Override
    public String label() {
        return "Savs Common Economy";
    }

    @Override
    public CompletableFuture<BigDecimal> balance(UUID player) {
        try {
            return adapt(getBalance.invoke(manager, player));
        } catch (Throwable t) {
            warn("查询余额", t);
            return CompletableFuture.completedFuture(BigDecimal.ZERO);
        }
    }

    @Override
    public BigDecimal cachedBalance(UUID player) {
        if (getCachedBalance == null) return null;
        try {
            Object value = getCachedBalance.invoke(manager, player);
            return value instanceof BigDecimal money ? money : null;
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public CompletableFuture<Boolean> charge(UUID player, BigDecimal amount) {
        try {
            return adapt(removeBalance.invoke(manager, player, amount));
        } catch (Throwable t) {
            warn("扣费", t);
            return CompletableFuture.completedFuture(false);
        }
    }

    @Override
    public CompletableFuture<Void> grant(UUID player, BigDecimal amount) {
        try {
            return SavsEconomy.<Boolean>adapt(addBalance.invoke(manager, player, amount)).thenAccept(ok -> {
            });
        } catch (Throwable t) {
            warn("发奖", t);
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public String format(BigDecimal amount) {
        if (format == null) return amount.toPlainString();
        try {
            Object value = format.invoke(manager, amount);
            return value == null ? amount.toPlainString() : value.toString();
        } catch (Throwable t) {
            return amount.toPlainString();
        }
    }

    /** 老版本同步返回 BigDecimal/Boolean，26.x 返回 CompletableFuture，两种都接。 */
    @SuppressWarnings("unchecked")
    private static <T> CompletableFuture<T> adapt(Object result) {
        if (result instanceof CompletableFuture<?> future) {
            return (CompletableFuture<T>) future;
        }
        return CompletableFuture.completedFuture((T) result);
    }

    private static void warn(String action, Throwable t) {
        Throwable cause = t instanceof InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : t;
        LOG.warn("[抽奖机] 调用 Savs 经济{}失败：{}", action, cause.toString());
    }
}
