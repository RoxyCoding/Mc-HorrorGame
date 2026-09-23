package chihalu.horror.client.terrain;

/**
 * Materials drawn by the marching cubes surface. Pure data, no Minecraft types, so the mesh
 * mathematics can be tested without a client. Colours are flat debug colours, not textures.
 */
public enum TerrainType {
    GRASS(0x7DA24C),
    DIRT(0x86603F),
    STONE(0x7E7E7E),
    SAND(0xDBCF9F),
    GRAVEL(0x8F817C);

    /** Voxel code for everything else: air, water, plants, logs, built blocks. */
    public static final byte NONE = 0;
    private static final TerrainType[] VALUES = values();
    private final int color;

    TerrainType(int color) {
        this.color = color;
    }

    /** Non-zero voxel code stored in {@link VoxelGrid}. */
    public byte code() {
        return (byte) (ordinal() + 1);
    }

    /** Flat 0xRRGGBB colour used until the surface gets real materials. */
    public int color() {
        return color;
    }

    public static TerrainType fromCode(byte code) {
        return VALUES[code - 1];
    }
}
