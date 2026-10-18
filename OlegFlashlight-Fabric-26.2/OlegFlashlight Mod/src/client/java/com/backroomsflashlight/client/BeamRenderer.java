package com.backroomsflashlight.client;

import com.backroomsflashlight.FlashlightConfig;
import com.backroomsflashlight.item.FlashlightItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;

/**
 * The star-shaped lens glare, drawn for every player who holds a switched-on flashlight.
 * (The solid beam mesh is gone: the light and the haze in the air come from SpotlightPass.)
 */
public final class BeamRenderer {
    private BeamRenderer() {}

    public static void register() {
        LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(BeamRenderer::draw);
    }

    private static void draw(LevelRenderContext context) {
        if (!FlashlightConfig.INSTANCE.lens_glare || FlashlightConfig.foundFootage()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 cam = context.levelState().cameraRenderState.pos;

        for (AbstractClientPlayer p : mc.level.players()) {
            if (p.isSpectator() || !FlashlightItem.isShining(p)) continue;
            Vec3 f = p.getViewVector(pt).normalize();
            Vec3 r = f.cross(new Vec3(0, 1, 0));
            if (r.lengthSqr() < 1.0e-6) r = new Vec3(1, 0, 0);
            r = r.normalize();
            Vec3 u = r.cross(f).normalize();

            // which hand holds it: the main hand if the flashlight is there, else the other hand
            boolean inMain = p.getMainHandItem().getItem() instanceof FlashlightItem;
            HumanoidArm arm = inMain ? p.getMainArm() : p.getMainArm().getOpposite();
            double side = arm == HumanoidArm.RIGHT ? 1.0 : -1.0;
            Vec3 origin = p.getEyePosition(pt).add(f.scale(0.5)).add(r.scale(0.25 * side)).add(u.scale(-0.22));

            PoseStack ps = context.poseStack();
            ps.pushPose();
            ps.translate(-cam.x, -cam.y, -cam.z);
            final boolean self = p == mc.player;
            context.submitNodeCollector().submitCustomGeometry(ps, RenderTypes.debugFilledBox(),
                    (pose, vc) -> glare(pose, vc, origin, f, cam, self));
            ps.popPose();
        }
    }

    /** Star-shaped lens glare: a soft glow plus four long spikes and four short ones, always facing the camera. */
    private static void glare(PoseStack.Pose pose, VertexConsumer vc, Vec3 o, Vec3 beam, Vec3 cam, boolean self) {
        Vec3 toCam = cam.subtract(o);
        double d = toCam.length();
        if (d < 0.05) return;
        toCam = toCam.scale(1.0 / d);
        double facing = Math.max(0.0, beam.dot(toCam));   // 1 when the light points at the viewer
        double k = (self ? 0.35 : 0.25) + 0.75 * facing * facing;
        double size = (self ? 0.10 : 0.16) * (0.6 + 0.9 * facing) * Math.min(2.5, 1.0 + d * 0.08);
        Vec3 a = toCam.cross(new Vec3(0, 1, 0));
        if (a.lengthSqr() < 1.0e-6) a = new Vec3(1, 0, 0);
        a = a.normalize();
        Vec3 b = a.cross(toCam).normalize();
        Vec3 c = o.add(toCam.scale(0.03));
        int hot = ARGB.colorFromFloat((float) Math.min(1.0, 0.9 * k), 1.0f, 0.97f, 0.85f);
        int none = ARGB.colorFromFloat(0f, 1.0f, 0.95f, 0.75f);
        // glow disc
        int n = 16;
        for (int i = 0; i < n; i++) {
            double a0 = 2 * Math.PI * i / n, a1 = 2 * Math.PI * (i + 1) / n;
            Vec3 p0 = c.add(a.scale(Math.cos(a0) * size * 0.9)).add(b.scale(Math.sin(a0) * size * 0.9));
            Vec3 p1 = c.add(a.scale(Math.cos(a1) * size * 0.9)).add(b.scale(Math.sin(a1) * size * 0.9));
            vertex(pose, vc, c, hot); vertex(pose, vc, c, hot); vertex(pose, vc, p1, none); vertex(pose, vc, p0, none);
            vertex(pose, vc, c, hot); vertex(pose, vc, c, hot); vertex(pose, vc, p0, none); vertex(pose, vc, p1, none);
        }
        // spikes
        for (int s = 0; s < 8; s++) {
            double ang = Math.PI / 4 * s;
            double len = size * (s % 2 == 0 ? 5.0 : 2.4);
            double w = size * 0.18;
            Vec3 dir = a.scale(Math.cos(ang)).add(b.scale(Math.sin(ang)));
            Vec3 side = a.scale(-Math.sin(ang)).add(b.scale(Math.cos(ang)));
            Vec3 tip = c.add(dir.scale(len));
            Vec3 l = c.add(side.scale(w)), r2 = c.subtract(side.scale(w));
            int mid = ARGB.colorFromFloat((float) Math.min(1.0, 0.7 * k), 1.0f, 0.97f, 0.85f);
            vertex(pose, vc, l, mid); vertex(pose, vc, r2, mid); vertex(pose, vc, tip, none); vertex(pose, vc, tip, none);
            vertex(pose, vc, r2, mid); vertex(pose, vc, l, mid); vertex(pose, vc, tip, none); vertex(pose, vc, tip, none);
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, Vec3 p, int color) {
        vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(color);
    }
}
