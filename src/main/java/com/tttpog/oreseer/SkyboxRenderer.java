package com.tttpog.oreseer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Draws a picture sky (equirectangular texture on a sphere) in the Overworld, added on top of the
 * vanilla sky with the same pipeline the sun and moon use. Called from SkyRendererMixin.
 */
public final class SkyboxRenderer {
    private static final String[] TEXTURES = {
            "textures/sky/milkyway_arch.png",
            "textures/sky/milkyway_pillar.png"
    };
    private static final int SLICES = 64;
    private static final int STACKS = 32;
    private static final float RADIUS = 90.0F;

    private static GpuBuffer buffer;
    private static int indexCount;

    private SkyboxRenderer() {}

    private static void ensureBuffer() {
        if (buffer != null) return;
        VertexFormat format = DefaultVertexFormat.POSITION_TEX;
        int quads = SLICES * STACKS * 2; // every quad twice, once per winding, so culling never hides it
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(quads * 4 * format.getVertexSize())) {
            BufferBuilder b = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, format);
            for (int i = 0; i < SLICES; i++) {
                for (int j = 0; j < STACKS; j++) {
                    float[][] p = new float[4][];
                    p[0] = point(i, j);
                    p[1] = point(i + 1, j);
                    p[2] = point(i + 1, j + 1);
                    p[3] = point(i, j + 1);
                    for (int k = 0; k < 4; k++) b.addVertex(p[k][0], p[k][1], p[k][2]).setUv(p[k][3], p[k][4]);
                    for (int k = 3; k >= 0; k--) b.addVertex(p[k][0], p[k][1], p[k][2]).setUv(p[k][3], p[k][4]);
                }
            }
            try (MeshData mesh = b.buildOrThrow()) {
                indexCount = mesh.drawState().indexCount();
                buffer = RenderSystem.getDevice().createBuffer(() -> "OreSeer skybox", 32, mesh.vertexBuffer());
            }
        }
    }

    /** x, y, z on the sphere plus u, v (v = 0 at the zenith, 0.5 at the horizon). */
    private static float[] point(int slice, int stack) {
        double lon = slice * Math.PI * 2.0 / SLICES;
        double el = Math.PI / 2.0 - stack * Math.PI / STACKS;
        float x = (float) (RADIUS * Math.cos(el) * Math.cos(lon));
        float y = (float) (RADIUS * Math.sin(el));
        float z = (float) (RADIUS * Math.cos(el) * Math.sin(lon));
        return new float[]{x, y, z, (float) slice / SLICES, (float) stack / STACKS};
    }

    public static void render(PoseStack poseStack, float starAngle, float starBrightness) {
        OreConfig c = OreConfig.get();
        if (!c.skyEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != Level.OVERWORLD) return;

        float alpha = c.skyDay ? Math.max(starBrightness, 0.55F) : starBrightness;
        if (alpha <= 0.002F) return;

        ensureBuffer();
        AbstractTexture tex = mc.getTextureManager().getTexture(
                Identifier.fromNamespaceAndPath(OreSeer.MODID, TEXTURES[Math.floorMod(c.skyChoice, TEXTURES.length)]));

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
        if (c.skyRotate) poseStack.mulPose(Axis.XP.rotation(starAngle));

        Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        stack.mul(poseStack.last().pose());
        float b = c.skyBrightness / 100.0F;
        GpuBufferSlice slice = RenderSystem.getDynamicUniforms()
                .writeTransform(stack, new Vector4f(b, b, b, alpha), new Vector3f(), new Matrix4f());
        GpuTextureView color = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
        GpuTextureView depth = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indexBuffer = indices.getBuffer(indexCount);

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "OreSeer skybox", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
            pass.setPipeline(RenderPipelines.CELESTIAL);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", slice);
            pass.bindTexture("Sampler0", tex.getTextureView(), tex.getSampler());
            pass.setVertexBuffer(0, buffer);
            pass.setIndexBuffer(indexBuffer, indices.type());
            pass.drawIndexed(0, 0, indexCount, 1);
        }

        stack.popMatrix();
        poseStack.popPose();
    }
}
