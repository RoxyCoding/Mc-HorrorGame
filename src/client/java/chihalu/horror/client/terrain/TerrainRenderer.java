package chihalu.horror.client.terrain;

import chihalu.horror.Horror;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PolygonMode;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.DynamicGpuData;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Draws the marching cubes surface of every visible section inside vanilla's opaque terrain pass,
 * right after the vanilla terrain, so depth testing against blocks and entities works as usual.
 * Culling (frustum and occlusion) is vanilla's: only sections in its visible list are drawn.
 */
public final class TerrainRenderer {
    private static final RenderPipeline.Snippet SNIPPET = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER2)
            .withVertexShader(Horror.id("core/marching_terrain"))
            .withFragmentShader(Horror.id("core/marching_terrain"))
            .withVertexBinding(0, GpuTerrainMesh.FORMAT)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .buildSnippet();
    public static final RenderPipeline SOLID = RenderPipeline.builder(SNIPPET)
            .withLocation(Horror.id("pipeline/marching_terrain"))
            .build();
    /**
     * The surface under the wireframe, pushed slightly away from the camera (reverse depth: negative
     * bias) so the triangle edges drawn on top of it always win the depth test.
     */
    public static final RenderPipeline SOLID_UNDER_WIREFRAME = RenderPipeline.builder(SNIPPET)
            .withLocation(Horror.id("pipeline/marching_terrain_under_wireframe"))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true, -2.0F, -4.0F))
            .build();
    /** Triangle edges. Needs the device's wireframe fill mode; optional like vanilla's own wireframe pipeline. */
    public static final RenderPipeline WIREFRAME = RenderPipeline.builder(SNIPPET)
            .withLocation(Horror.id("pipeline/marching_terrain_wireframe"))
            .withPolygonMode(PolygonMode.WIREFRAME)
            .withShaderDefine("WIREFRAME")
            .build();

    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final Queue<GpuTerrainMesh> RELEASED = new ConcurrentLinkedQueue<>();
    private static volatile int drawnSections, drawnTriangles;

    /** Sections and triangles of surface drawn in the last frame, for tests and debugging. */
    public static int drawnSections() {
        return drawnSections;
    }

    public static int drawnTriangles() {
        return drawnTriangles;
    }

    /** Compiled with the other pipelines on resource reload; a failure only logs a warning. */
    static void registerPipelines() {
        RenderPipelines.registerOptional(SOLID);
        RenderPipelines.registerOptional(SOLID_UNDER_WIREFRAME);
        RenderPipelines.registerOptional(WIREFRAME);
    }

    static void release(GpuTerrainMesh mesh) {
        RELEASED.add(mesh);
    }

    /** Render thread: frees the GPU buffers of released section meshes. */
    static void releasePending() {
        for (GpuTerrainMesh mesh; (mesh = RELEASED.poll()) != null; ) mesh.free();
    }

    /**
     * Render thread, inside the opaque terrain pass after vanilla terrain: draws every visible section
     * that has a surface. Drawn whatever the current mode, so sections still waiting to be recompiled
     * after a mode change keep their old surface instead of leaving a hole.
     */
    public static void draw(RenderPass pass, List<SectionRenderDispatcher.RenderSection> sections, CameraRenderState camera,
                            double fadeInSeconds) {
        releasePending();
        drawnSections = drawnTriangles = 0;
        CompiledRenderPipeline solid = RenderSystem.getCompiledPipelineNullable(SOLID);
        if (solid == null) return;
        // Wireframe view: the shaded surface pushed back a little, and its triangle edges on top.
        CompiledRenderPipeline under = null, edges = null;
        if (MarchingTerrain.mode() == TerrainRenderMode.WIREFRAME && RenderSystem.isWireframeAvailable()) {
            under = RenderSystem.getCompiledPipelineNullable(SOLID_UNDER_WIREFRAME);
            edges = RenderSystem.getCompiledPipelineNullable(WIREFRAME);
        }

        // Same camera split as vanilla terrain: exact integer offsets here, the fraction from Globals.
        int camX = Mth.floor(camera.pos.x), camY = Mth.floor(camera.pos.y), camZ = Mth.floor(camera.pos.z);
        long now = Util.getMillis(), fade = Util.toMillis(fadeInSeconds);
        List<GpuTerrainMesh> meshes = new ArrayList<>();
        List<DynamicGpuData.Transform> transforms = new ArrayList<>();
        for (SectionRenderDispatcher.RenderSection section : sections) {
            if (!(section.getSectionMesh() instanceof TerrainMeshHolder holder)) continue;
            GpuTerrainMesh mesh = holder.horror$terrainMesh();
            if (mesh == null || !mesh.prepare()) continue;
            BlockPos origin = section.getRenderOrigin();
            meshes.add(mesh);
            transforms.add(new DynamicGpuData.Transform(camera.viewRotationMatrix,
                    new Vector4f(1, 1, 1, section.getVisibility(now, fade)),
                    new Vector3f(origin.getX() - camX, origin.getY() - camY, origin.getZ() - camZ), IDENTITY));
        }
        if (meshes.isEmpty()) return;

        GpuBufferSlice[] uniforms = RenderSystem.getDynamicUniforms().writeTransforms(transforms.toArray(DynamicGpuData.Transform[]::new));
        pass.pushDebugGroup(() -> "Horror marching cubes terrain");
        pass.setUniform("Sampler2", Minecraft.getInstance().gameRenderer.lightmap(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
        if (under != null && edges != null) {
            drawAll(pass, under, meshes, uniforms);
            drawAll(pass, edges, meshes, uniforms);
        } else {
            drawAll(pass, solid, meshes, uniforms);
        }
        pass.popDebugGroup();
        int triangles = 0;
        for (GpuTerrainMesh mesh : meshes) triangles += mesh.indexCount() / 3;
        drawnSections = meshes.size();
        drawnTriangles = triangles;
    }

    private static void drawAll(RenderPass pass, CompiledRenderPipeline pipeline, List<GpuTerrainMesh> meshes, GpuBufferSlice[] uniforms) {
        pass.setPipeline(pipeline);
        for (int i = 0; i < meshes.size(); i++) {
            GpuTerrainMesh mesh = meshes.get(i);
            pass.setUniform("DynamicTransforms", uniforms[i]);
            mesh.bind(pass);
            pass.drawIndexed(mesh.indexCount(), 1, 0, 0, 0);
        }
    }

    private TerrainRenderer() { }
}
