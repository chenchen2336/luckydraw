package cn.blockforge.generated.fabric263wishesfabri.economy;

import cn.blockforge.generated.fabric263wishesfabri.config.LotteryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/** 决定这次服务器启动用哪个钱包：优先 Savs，没有就用内置的。 */
public final class Economies {

    private static final Logger LOG = LoggerFactory.getLogger("luckydraw");
    private static EconomyBridge current;

    private Economies() {
    }

    public static synchronized void init() {
        LotteryConfig.EconomyCfg cfg = LotteryConfig.get().economy;
        String prefer = cfg.provider == null ? "auto" : cfg.provider.trim().toLowerCase(Locale.ROOT);

        if ("internal".equals(prefer)) {
            current = new InternalEconomy();
            LOG.info("[抽奖机] 按配置使用内置钱包。");
            return;
        }

        SavsEconomy savs = SavsEconomy.tryCreate();
        if (savs != null) {
            current = savs;
        } else {
            if ("savs".equals(prefer)) {
                LOG.warn("[抽奖机] 配置要求用 Savs 经济，但没找到它，已临时改用内置钱包。");
            } else {
                LOG.info("[抽奖机] 没检测到 Savs Common Economy，使用内置钱包（/lottery give 发币）。");
            }
            current = new InternalEconomy();
        }
    }

    public static EconomyBridge get() {
        EconomyBridge local = current;
        if (local == null) {
            init();
            local = current;
        }
        return local;
    }
}
