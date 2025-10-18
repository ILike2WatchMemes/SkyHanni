package at.hannibal2.skyhanni.mixins.transformers;

import at.hannibal2.skyhanni.SkyHanniMod;
import at.hannibal2.skyhanni.events.entity.EntityEquipmentChangeEvent;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.NetworkThreadUtils;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.util.thread.ThreadExecutor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(value = ClientPlayNetworkHandler.class)
public class MixinClientPlayNetworkHandler {

    @Inject(method = "onEntityEquipmentUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/packet/s2c/play/EntityEquipmentUpdateS2CPacket;getEquipmentList()Ljava/util/List;", shift = At.Shift.AFTER), locals = LocalCapture.CAPTURE_FAILHARD)
    public void onEntityEquipmentUpdate(EntityEquipmentUpdateS2CPacket packet, CallbackInfo ci, Entity entity, LivingEntity livingEntity) {
        packet.getEquipmentList().forEach((equipment) -> {
            new EntityEquipmentChangeEvent(entity, equipment.getFirst().getIndex(), equipment.getSecond()).post();
        });
    }

    @Redirect(method = "onGameMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/util/thread/ThreadExecutor;)V"))
    public void onGameMessage(Packet packet, PacketListener packetListener, ThreadExecutor threadExecutor) {
        if (!SkyHanniMod.feature.getDev().getNonBlockingMessageProcessing()) {
            NetworkThreadUtils.forceMainThread(packet, packetListener, threadExecutor);
        }
    }
}
