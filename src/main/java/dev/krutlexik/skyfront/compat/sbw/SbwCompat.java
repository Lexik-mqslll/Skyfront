package dev.krutlexik.skyfront.compat.sbw;

import net.neoforged.fml.ModList;

/** Is SuperbWarfare installed? Nothing else — and deliberately so. */
public final class SbwCompat {
    public static final String MOD_ID = "superbwarfare";

    private SbwCompat() {
    }

    /** True when SuperbWarfare is installed in this runtime. */
    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }
}
