package dev.krutlexik.skyfront.network;

import dev.krutlexik.skyfront.client.ClientWorldMapData;
import dev.krutlexik.skyfront.client.sound.ShahedEngineSoundClient;
import dev.krutlexik.skyfront.entity.ShahedEntity;
import dev.krutlexik.skyfront.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

@OnlyIn(Dist.CLIENT)
public class ClientPacketHandler {
    private static final Map<Integer, ShahedEngineSoundClient> activeSounds = new HashMap<>();
    private static final Random random = new Random();

    public static void handleShahedSpawn(int entityId, Vec3 currentPos, BlockPos targetPos, String diveMode,
                                         float healthFract, boolean falling, float diveProgress, boolean finalLeg) {
        cleanupStoppedSounds();

        Minecraft mc = Minecraft.getInstance();

        if (mc.level != null) {
            Entity entity = mc.level.getEntity(entityId);
            if (entity instanceof ShahedEntity shahed) {
                shahed.setServerPosition(currentPos);
            }
        }

        ShahedEngineSoundClient existingSound = activeSounds.get(entityId);
        if (existingSound != null) {
            if (existingSound.isStopped()) {
                activeSounds.remove(entityId);
            } else {
                existingSound.updateDronePosition(currentPos);
                existingSound.updateTarget(targetPos);
                existingSound.updateDamageState(healthFract, falling, diveProgress, finalLeg);
                return;
            }
        }

        ShahedEngineSoundClient sound = new ShahedEngineSoundClient(entityId, currentPos, targetPos, diveMode);
        sound.updateDamageState(healthFract, falling, diveProgress, finalLeg);
        activeSounds.put(entityId, sound);
        mc.execute(sound::start);
    }

    public static void handleShahedRemove(int entityId) {
        ShahedEngineSoundClient sound = activeSounds.remove(entityId);
        if (sound != null && !sound.isStopped()) {
            sound.stopSound();
        }
    }

    public static void handleExplosion(Vec3 explosionPos, float particleMultiplier, boolean customSounds) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        dev.krutlexik.skyfront.client.ClientExplosionEffects.spawn(explosionPos, particleMultiplier);
        
        ShahedEngineSoundClient closestSound = null;
        double closestDistance = Double.MAX_VALUE;
        int closestEntityId = -1;
        
        for (Map.Entry<Integer, ShahedEngineSoundClient> entry : activeSounds.entrySet()) {
            ShahedEngineSoundClient sound = entry.getValue();
            if (sound.isStopped()) continue;
            
            Vec3 soundPos = new Vec3(sound.getX(), sound.getY(), sound.getZ());
            double dist = soundPos.distanceTo(explosionPos);
            if (dist < closestDistance && dist < 150.0) {
                closestDistance = dist;
                closestSound = sound;
                closestEntityId = entry.getKey();
            }
        }
        
        if (closestSound != null) {
            closestSound.markExploded(explosionPos);
            final int entityId = closestEntityId;
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ignored) {}
                Minecraft.getInstance().execute(() -> activeSounds.remove(entityId));
            }).start();
        }
        
        Vec3 playerPos = dev.krutlexik.skyfront.client.ClientViewpoint.position();
        double distance = playerPos.distanceTo(explosionPos);
        float explosionVolume = calculateExplosionVolume(distance);
        
        if (explosionVolume < 0.1f) {
            return;
        }
        
        if (!customSounds) {
            return;
        }
        
        int delay = (int) (distance / 17.0);
        int randomSound = random.nextInt(5);
        
        new Thread(() -> {
            try {
                Thread.sleep((long) delay * 50L);
                mc.execute(() -> {
                    float randomPitch = 0.9f + random.nextFloat() * 0.2f;
                    SoundEvent soundEvent = switch (randomSound) {
                        case 0 -> ModSounds.EXPLOSION_1.get();
                        case 1 -> ModSounds.EXPLOSION_2.get();
                        case 2 -> ModSounds.EXPLOSION_3.get();
                        case 3 -> ModSounds.EXPLOSION_4.get();
                        default -> ModSounds.EXPLOSION_5.get();
                    };
                    mc.level.playLocalSound(explosionPos.x, explosionPos.y, explosionPos.z, soundEvent, SoundSource.HOSTILE, explosionVolume, randomPitch, false);
                });
            } catch (InterruptedException ignored) {}
        }).start();
    }
    
    private static float calculateExplosionVolume(double distance) {
        if (distance >= 700.0) return 0.0f;
        if (distance <= 350.0) return 40.0f;
        if (distance <= 450.0) return 24.0f;
        if (distance <= 550.0) return 12.0f;
        if (distance <= 650.0) return 4.0f;
        return 1.0f;
    }
    
    private static void cleanupStoppedSounds() {
        Iterator<Map.Entry<Integer, ShahedEngineSoundClient>> iterator = activeSounds.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, ShahedEngineSoundClient> entry = iterator.next();
            if (entry.getValue().isStopped()) {
                iterator.remove();
            }
        }
    }

    public static void clearAll() {
        for (ShahedEngineSoundClient sound : activeSounds.values()) {
            if (!sound.isStopped()) {
                sound.stopSound();
            }
        }
        activeSounds.clear();
    }
    
    public static void handleWorldMapPacket(WorldMapPacket packet) {
        ClientWorldMapData.getInstance().update(
            packet.getCenterChunkX(),
            packet.getCenterChunkZ(),
            packet.getRadiusChunks(),
            packet.decodeGrid(),
            packet.isScanComplete(),
            packet.getScanProgress(),
            packet.getTotalChunks()
        );
    }
}
