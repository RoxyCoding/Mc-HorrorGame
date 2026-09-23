package chihalu.horror.client.terrain;

import net.minecraft.network.chat.Component;

/** Debug view of the terrain, cycled with a key. */
public enum TerrainRenderMode {
    /** Vanilla cubes; no marching cubes meshes are built. */
    VANILLA("vanilla"),
    /** Smooth marching cubes surface in place of the terrain blocks. */
    MARCHING_CUBES("marching_cubes"),
    /** The same surface drawn as triangle edges. */
    WIREFRAME("wireframe");

    private final String key;

    TerrainRenderMode(String key) {
        this.key = key;
    }

    /** Whether section compiles build the surface and leave the terrain blocks out. */
    public boolean replacesBlocks() {
        return this != VANILLA;
    }

    public Component label() {
        return Component.translatable("message.horror.terrain." + key);
    }

    public TerrainRenderMode next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
