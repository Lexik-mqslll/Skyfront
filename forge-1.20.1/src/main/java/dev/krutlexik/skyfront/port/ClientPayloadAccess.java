package dev.krutlexik.skyfront.port;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

/** The client's half of {@link IPayloadContext#player()}: kept apart so a dedicated server never loads it. */
@OnlyIn(Dist.CLIENT)
final class ClientPayloadAccess {

    @Nullable
    static Player player() {
        return Minecraft.getInstance().player;
    }

    private ClientPayloadAccess() {
    }
}
