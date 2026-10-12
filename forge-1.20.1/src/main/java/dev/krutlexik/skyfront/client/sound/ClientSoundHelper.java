package dev.krutlexik.skyfront.client.sound;

import dev.krutlexik.skyfront.entity.ShahedEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;

public class ClientSoundHelper {
    public static void startShahedSound(ShahedEntity entity) {
        Minecraft.getInstance().getSoundManager().play(new ShahedSoundInstance(entity));
    }
}
