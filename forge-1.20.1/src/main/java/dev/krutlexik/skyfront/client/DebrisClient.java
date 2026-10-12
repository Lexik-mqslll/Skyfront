package dev.krutlexik.skyfront.client;

import dev.krutlexik.skyfront.entity.DebrisEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/** The client's half of an airframe coming apart: the pieces put into this world, to fly here alone. */
@OnlyIn(Dist.CLIENT)
public final class DebrisClient {
    public static void spawn(List<DebrisEntity.Spawn> pieces) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || pieces.isEmpty()) {
            return;
        }
        var first = pieces.get(0).position();
        if (!level.hasChunk(SectionPos.blockToSectionCoord(first.x), SectionPos.blockToSectionCoord(first.z))) {
            return;
        }
        DebrisEntity.spawnLocally(level, pieces, debris -> level.putNonPlayerEntity(debris.getId(), debris));
    }

    private DebrisClient() {
    }
}
