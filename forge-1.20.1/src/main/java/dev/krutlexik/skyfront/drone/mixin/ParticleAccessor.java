package dev.krutlexik.skyfront.drone.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A particle's colour and age, which the thermal camera reads and, for one draw, overwrites. */
@Mixin(Particle.class)
public interface ParticleAccessor {
    @Accessor("rCol")
    float sfw$getRed();

    @Accessor("rCol")
    void sfw$setRed(float red);

    @Accessor("gCol")
    float sfw$getGreen();

    @Accessor("gCol")
    void sfw$setGreen(float green);

    @Accessor("bCol")
    float sfw$getBlue();

    @Accessor("bCol")
    void sfw$setBlue(float blue);

    @Accessor("age")
    int sfw$getAge();

    @Accessor("lifetime")
    int sfw$getLifetime();
}
