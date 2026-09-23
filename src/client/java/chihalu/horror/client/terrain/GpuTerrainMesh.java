package chihalu.horror.client.terrain;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import java.nio.ByteBuffer;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

/**
 * The surface of one section on the GPU. Built on a section compile worker, uploaded on the render
 * thread when first drawn, and freed on the render thread after vanilla releases the section mesh
 * that owns it. The CPU {@link TerrainMesh} is dropped once uploaded.
 */
public final class GpuTerrainMesh {
    /** Section-local position, flat terrain colour, vanilla lightmap coordinates, smooth normal: 24 bytes. */
    public static final VertexFormat FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("UV2", GpuFormat.RG16_SINT)
            .addAttribute("Normal", GpuFormat.RGBA8_SNORM)
            .build();

    private @Nullable TerrainMesh mesh;
    private final int indexCount;
    private @Nullable GpuBuffer vertices, indices;
    private volatile boolean closed;

    GpuTerrainMesh(TerrainMesh mesh) {
        this.mesh = mesh;
        this.indexCount = mesh.indices().length;
    }

    int indexCount() {
        return indexCount;
    }

    /** Render thread: uploads on first use. False once released. */
    boolean prepare() {
        if (closed) return false;
        if (vertices == null && mesh != null) {
            TerrainMesh pending = mesh;
            mesh = null;
            upload(pending);
        }
        return vertices != null && indices != null;
    }

    void bind(RenderPass pass) {
        pass.setVertexBuffer(0, vertices.slice());
        pass.setIndexBuffer(indices, IndexType.SHORT);
    }

    /** Any thread: vanilla released the section mesh. The buffers are freed on the render thread. */
    public void close() {
        closed = true;
        TerrainRenderer.release(this);
    }

    /** Render thread only. */
    void free() {
        if (vertices != null) vertices.close();
        if (indices != null) indices.close();
        vertices = indices = null;
        mesh = null;
    }

    private void upload(TerrainMesh mesh) {
        GpuDevice device = RenderSystem.getDevice();
        int count = mesh.vertexCount();
        float[] p = mesh.positions(), n = mesh.normals();
        ByteBuffer data = MemoryUtil.memAlloc(count * FORMAT.getVertexSize());
        try {
            for (int v = 0; v < count; v++) {
                data.putFloat(p[v * 3]).putFloat(p[v * 3 + 1]).putFloat(p[v * 3 + 2]);
                int color = mesh.type(v).color();
                data.put((byte) (color >> 16)).put((byte) (color >> 8)).put((byte) color).put((byte) 255);
                int light = mesh.light()[v] & 0xFF;
                // Vanilla lightmap coordinates: block and sky light times 16.
                data.putShort((short) ((light & 15) << 4)).putShort((short) ((light >>> 4) << 4));
                data.put(snorm(n[v * 3])).put(snorm(n[v * 3 + 1])).put(snorm(n[v * 3 + 2])).put((byte) 0);
            }
            vertices = device.createBuffer(() -> "Horror terrain vertices", GpuBuffer.USAGE_VERTEX, data.flip());
        } finally {
            MemoryUtil.memFree(data);
        }
        // A section has at most 17 * 17 * 17 * 3 lattice edges, so 16-bit indices always suffice.
        int[] idx = mesh.indices();
        ByteBuffer indexData = MemoryUtil.memAlloc(idx.length * 2);
        try {
            for (int i : idx) indexData.putShort((short) i);
            indices = device.createBuffer(() -> "Horror terrain indices", GpuBuffer.USAGE_INDEX, indexData.flip());
        } finally {
            MemoryUtil.memFree(indexData);
        }
    }

    private static byte snorm(float value) {
        return (byte) Math.round(Math.max(-1, Math.min(1, value)) * 127);
    }
}
