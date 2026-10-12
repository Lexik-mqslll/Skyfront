package dev.krutlexik.skyfront.client.sound;

import dev.krutlexik.skyfront.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class SirenSoundInstance extends AbstractTickableSoundInstance {
    private static final Map<BlockPos, SirenSoundInstance> activeSounds = new HashMap<>();

    private final BlockPos sirenPos;
    private final Vec3 sirenVec;
    private final double maxDistance;
    private boolean forceStopped = false;

    public SirenSoundInstance(BlockPos pos, double maxDistance) {
        super(ModSounds.SIREN.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.sirenPos = pos;
        this.sirenVec = Vec3.atCenterOf(pos);
        this.maxDistance = maxDistance;

        this.relative = false;
        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 0.5;
        this.z = pos.getZ() + 0.5;
        
        this.looping = false;
        this.delay = 0;
        this.pitch = 1.0f;
        this.attenuation = Attenuation.LINEAR;
        
        this.volume = calculateVolume();
    }
    
    private float calculateVolume() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0.0f;

        double distance = mc.player.position().distanceTo(this.sirenVec);

        double fullVolumeUpTo = Math.min(10.0, this.maxDistance * 0.1);
        if (distance >= this.maxDistance) return 0.0f;
        if (distance <= fullVolumeUpTo) return 2.0f;

        double factor = 1.0 - ((distance - fullVolumeUpTo) / (this.maxDistance - fullVolumeUpTo));
        return (float) (factor * 2.0);
    }

    public static void playSiren(BlockPos pos, int range) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        double distance = mc.player.position().distanceTo(Vec3.atCenterOf(pos));
        if (distance > range) return;

        SirenSoundInstance old = activeSounds.remove(pos);
        if (old != null) {
            old.forceStopped = true;
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().stop(old);
            }
        }

        SirenSoundInstance sound = new SirenSoundInstance(pos, range);
        activeSounds.put(pos, sound);
        mc.getSoundManager().play(sound);
    }
    
    public static void stopSiren(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        
        SirenSoundInstance sound = activeSounds.remove(pos);
        if (sound != null) {
            sound.forceStopped = true;
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().stop(sound);
            }
        }
    }
    
    @Override
    public void tick() {
        if (this.forceStopped) return;
        
        this.volume = calculateVolume();
        
        if (this.volume <= 0.001f) {
            this.forceStopped = true;
            activeSounds.remove(this.sirenPos);
        }
    }

    @Override
    public boolean isStopped() {
        return this.forceStopped;
    }
}
