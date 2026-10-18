package fr.wakfu.rpg.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import fr.wakfu.rpg.WakfuRPG;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Short-lived floating combat numbers. */
@Mod.EventBusSubscriber(modid = WakfuRPG.MODID, value = net.minecraftforge.api.distmarker.Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DamagePopups {
    private record Popup(int amount, boolean healing, long start, int stack) {}
    private static final Map<Integer, Popup> ACTIVE = new ConcurrentHashMap<>();
    private DamagePopups() {}
    public static void add(int entityId, int amount, boolean healing) {
        if (amount <= 0) return;
        long now = System.currentTimeMillis();
        Popup old = ACTIVE.get(entityId);
        boolean merge = old != null && old.healing == healing && now - old.start < 450;
        int stack = merge ? Math.min(4, old.stack + 1) : 0;
        int total = merge ? Math.min(9999, old.amount + amount) : amount;
        ACTIVE.put(entityId, new Popup(total, healing, now, stack));
        Minecraft mc = Minecraft.getInstance();
        if (!healing && mc.level != null
                && mc.level.getEntity(entityId) instanceof net.minecraft.world.entity.LivingEntity living) {
            living.hurtTime = Math.max(living.hurtTime, 6);
            living.hurtDuration = Math.max(living.hurtDuration, 10);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ACTIVE.clear();
            return;
        }
        long now = System.currentTimeMillis();
        ACTIVE.entrySet().removeIf(entry -> now - entry.getValue().start > 1100
                || mc.level.getEntity(entry.getKey()) == null);
    }

    @SubscribeEvent
    public static void render(RenderLivingEvent.Post<?, ?> event) {
        Entity entity = event.getEntity();
        Popup p = ACTIVE.get(entity.getId());
        if (p == null) return;
        long elapsed = System.currentTimeMillis() - p.start;
        if (elapsed > 950) { ACTIVE.remove(entity.getId(), p); return; }
        float progress = elapsed / 950.0f;
        float fade = Math.min(1.0f, (1.0f - progress) * 2.5f);
        float pulse = 1.0f + 0.22f * Math.max(0.0f, 1.0f - elapsed / 140.0f);
        float rise = progress * 0.8f + p.stack * 0.12f;
        var pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(0, entity.getBbHeight() + 0.3 + rise, 0);
        pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-0.025f * pulse, -0.025f * pulse, 0.025f);
        String label = (p.healing ? "+" : "-" ) + p.amount;
        int rgb = p.healing ? 0x55FF77 : 0xFF5555;
        int color = ((int) (fade * 255.0f) << 24) | rgb;
        var font = Minecraft.getInstance().font;
        font.drawInBatch(label, -font.width(label) / 2.0f, 0, color, true, pose.last().pose(),
                event.getMultiBufferSource(), net.minecraft.client.gui.Font.DisplayMode.NORMAL,
                0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
