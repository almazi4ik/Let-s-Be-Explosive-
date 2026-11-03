package com.letsbeexplosive;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class ProgressiveExplosion {
    private static final int RADIUS = 4;
    private static final int LAYER_INTERVAL = 6;
    private static final List<ExplosionTask> ACTIVE = new ArrayList<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Iterator<ExplosionTask> iterator = ACTIVE.iterator();

            while (iterator.hasNext()) {
                ExplosionTask task = iterator.next();
                if (task.tick()) {
                    iterator.remove();
                }
            }
        });
    }

    public static void start(ServerLevel level, double x, double y, double z) {
        level.playSound(null, x, y, z,
            SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 4.0F, 1.0F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
            x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        Map<Integer, List<BlockPos>> layers = new TreeMap<>(Comparator.reverseOrder());

        BlockPos center = BlockPos.containing(x, y, z);

        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dy = -RADIUS; dy <= RADIUS; dy++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    if (dx * dx + dy * dy + dz * dz > RADIUS * RADIUS) {
                        continue;
                    }

                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);

                    if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) {
                        continue;
                    }

                    layers.computeIfAbsent(pos.getY(), key -> new ArrayList<>()).add(pos);
                }
            }
        }

        ACTIVE.add(new ExplosionTask(level, center, new ArrayList<>(layers.values())));
    }

    private static float destructionFactor(ServerLevel level, BlockPos pos, BlockState state) {
        String id = state.getBlock().getDescriptionId();

        if (id.contains("obsidian")) return 0.05F;

        if (id.contains("diamond") || id.contains("emerald")
                || id.contains("netherite") || id.contains("gold_block")
                || id.contains("iron_block")) {
            return 0.20F;
        }

        if (id.contains("deepslate")) return 0.45F;

        if (id.contains("stone") || id.contains("cobblestone")
                || id.contains("tuff") || id.contains("blackstone")
                || id.contains("granite") || id.contains("diorite")
                || id.contains("andesite")) {
            return 0.65F;
        }

        if (id.contains("planks") || id.contains("log")
                || id.contains("wood") || id.contains("stem")
                || id.contains("hyphae")) {
            return 0.85F;
        }

        float hardness = state.getDestroySpeed(level, pos);

        if (hardness <= 0.6F) return 1.0F;
        if (hardness <= 2.0F) return 0.85F;
        if (hardness <= 3.0F) return 0.65F;
        if (hardness <= 5.0F) return 0.45F;

        return 0.20F;
    }

    private static class ExplosionTask {
        private final ServerLevel level;
        private final BlockPos center;
        private final List<List<BlockPos>> layers;
        private int layerIndex = 0;
        private int wait = 0;

        private ExplosionTask(ServerLevel level, BlockPos center, List<List<BlockPos>> layers) {
            this.level = level;
            this.center = center;
            this.layers = layers;
        }

        private boolean tick() {
            if (wait > 0) {
                wait--;
                return false;
            }

            if (layerIndex >= layers.size()) {
                return true;
            }

            damageNearbyEntities();
            destroyLayer(layers.get(layerIndex));
            layerIndex++;
            wait = LAYER_INTERVAL;

            return layerIndex >= layers.size();
        }

        private void damageNearbyEntities() {
            double cx = center.getX() + 0.5;
            double cy = center.getY() + 0.5;
            double cz = center.getZ() + 0.5;

            AABB area = new AABB(
                    cx - RADIUS, cy - RADIUS, cz - RADIUS,
                    cx + RADIUS, cy + RADIUS, cz + RADIUS
            );

            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
                double distanceSqr = entity.distanceToSqr(cx, cy, cz);

                float damage;
                if (distanceSqr <= 0.64) damage = 5.0F;
                else if (distanceSqr <= 2.56) damage = 4.0F;
                else if (distanceSqr <= 5.76) damage = 3.0F;
                else if (distanceSqr <= 10.24) damage = 2.0F;
                else if (distanceSqr <= 16.0) damage = 1.0F;
                else continue;

                // Снижаем урон пропорционально сумме единиц брони.
                int armorPoints = entity.getArmorValue();
                float armorReduction = Math.min(20, armorPoints) / 25.0F;
                damage *= (1.0F - armorReduction);

                // Позволяем наносить урон на каждом слое.
                entity.invulnerableTime = 0;
                entity.hurt(level.damageSources().generic(), damage);
            }
        }

        private boolean hasFullDiamondOrNetheriteArmor(LivingEntity entity) {
            return isDiamondOrNetherite(entity.getItemBySlot(EquipmentSlot.HEAD))
                    && isDiamondOrNetherite(entity.getItemBySlot(EquipmentSlot.CHEST))
                    && isDiamondOrNetherite(entity.getItemBySlot(EquipmentSlot.LEGS))
                    && isDiamondOrNetherite(entity.getItemBySlot(EquipmentSlot.FEET));
        }

        private boolean isDiamondOrNetherite(net.minecraft.world.item.ItemStack stack) {
            return stack.is(Items.DIAMOND_HELMET) || stack.is(Items.NETHERITE_HELMET)
                    || stack.is(Items.DIAMOND_CHESTPLATE) || stack.is(Items.NETHERITE_CHESTPLATE)
                    || stack.is(Items.DIAMOND_LEGGINGS) || stack.is(Items.NETHERITE_LEGGINGS)
                    || stack.is(Items.DIAMOND_BOOTS) || stack.is(Items.NETHERITE_BOOTS);
        }

        private void destroyLayer(List<BlockPos> positions) {
            List<BlockPos> candidates = new ArrayList<>();
            float totalFactor = 0.0F;

            for (BlockPos pos : positions) {
                BlockState state = level.getBlockState(pos);

                if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) {
                    continue;
                }

                candidates.add(pos);
                totalFactor += destructionFactor(level, pos, state);
            }

            if (candidates.isEmpty()) return;

            // Прочность рассчитывается для всего слоя, а не отдельно для каждого блока.
            float averageFactor = totalFactor / candidates.size();
            int destroyCount = Math.round(candidates.size() * averageFactor);

            // Перемешиваем блоки, чтобы разрушение выглядело естественнее.
            for (int i = candidates.size() - 1; i > 0; i--) {
                int j = level.random.nextInt(i + 1);
                Collections.swap(candidates, i, j);
            }

            for (int i = 0; i < destroyCount; i++) {
                BlockPos pos = candidates.get(i);
                BlockState state = level.getBlockState(pos);

                if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) {
                    continue;
                }

                double px = pos.getX() + 0.5;
                double py = pos.getY() + 0.5;
                double pz = pos.getZ() + 0.5;

                if (level.destroyBlock(pos, true)) {
                    level.sendParticles(
                            new BlockParticleOption(ParticleTypes.BLOCK, state),
                            px, py, pz, 8, 0.25, 0.25, 0.25, 0.05
                    );
                    level.sendParticles(
                            ParticleTypes.POOF,
                            px, py, pz, 3, 0.2, 0.2, 0.2, 0.02
                    );
                }
            }
        }
    }
}
