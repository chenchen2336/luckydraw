package cn.blockforge.generated.fabric263wishesfabri.fx;

import cn.blockforge.generated.fabric263wishesfabri.config.LotteryConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * 抽奖的粒子与音效表现。全部用原版粒子和音效，
 * 所以没装模组的客户端也能看见、能听见。
 */
public final class LotteryFx {

    private LotteryFx() {
    }

    /** 开始抽奖：一声点击 + 第一圈火花。 */
    public static void start(ServerPlayer player) {
        LotteryConfig.Effects fx = LotteryConfig.get().effects;
        if (fx.sounds) {
            play(player, SoundEvents.UI_BUTTON_CLICK.value(), 0.8F, 1.5F);
        }
        ring(player, 0);
    }

    /** 脚下环形上升的火花，每 tick 画一圈，转着往上走。 */
    public static void ring(ServerPlayer player, int elapsed) {
        LotteryConfig.Effects fx = LotteryConfig.get().effects;
        if (!fx.particles) return;
        ServerLevel level = player.level();
        int points = 12;
        double radius = 1.15D;
        double spin = elapsed * 0.32D;
        double y = player.getY() + 0.1D + (elapsed % 14) * 0.14D;
        for (int i = 0; i < points; i++) {
            double angle = spin + i * (Math.PI * 2.0D / points);
            double x = player.getX() + Math.cos(angle) * radius;
            double z = player.getZ() + Math.sin(angle) * radius;
            if (i % 2 == 0) {
                level.sendParticles(ParticleTypes.FLAME, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            } else {
                level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
        if (elapsed % 4 == 0) {
            level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 0.15D, player.getZ(),
                    6, 0.55D, 0.05D, 0.55D, 0.02D);
        }
    }

    /** 滚动加速阶段：越接近开奖，滴答声越密、音调越高。 */
    public static void rollTick(ServerPlayer player, int elapsed, int ticksLeft, int totalTicks) {
        LotteryConfig.Effects fx = LotteryConfig.get().effects;
        if (!fx.sounds) return;
        int interval = ticksLeft <= totalTicks / 3 ? 1 : (ticksLeft <= totalTicks * 2 / 3 ? 2 : 3);
        if (elapsed % interval != 0) return;
        float pitch = 0.9F + Math.min(0.9F, elapsed * 0.03F);
        play(player, SoundEvents.NOTE_BLOCK_HAT.value(), 0.6F, pitch);
    }

    /** 开奖：彩色粒子炸开 + 中奖号角。 */
    public static void finish(ServerPlayer player, boolean rare) {
        LotteryConfig.Effects fx = LotteryConfig.get().effects;
        ServerLevel level = player.level();
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        if (fx.particles) {
            level.sendParticles(ParticleTypes.FIREWORK, x, y + 1.0D, z, 80, 0.9D, 0.9D, 0.9D, 0.35D);
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y + 1.2D, z, 40, 0.6D, 0.6D, 0.6D, 0.5D);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y + 1.4D, z, 25, 0.8D, 0.5D, 0.8D, 0.1D);
        }
        if (fx.sounds) {
            play(player, SoundEvents.PLAYER_LEVELUP, 1.0F, 1.0F);
            if (rare) {
                play(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
            }
        }
    }

    private static void play(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        ServerLevel level = player.level();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }
}
