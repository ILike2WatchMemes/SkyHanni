package at.hannibal2.skyhanni.mixins.transformers;

import com.mojang.authlib.properties.Property;
import com.mojang.authlib.yggdrasil.YggdrasilServicesKeyInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(YggdrasilServicesKeyInfo.class)
public class SignaturePropertyErrorIgnoreMixin {
    //Implemented by SkyHanni because throwing a stacktrace is expensive AND they are thrown extremely often
    @Inject(method = "validateProperty", at = @At("HEAD"), remap = false, cancellable = true)
    public void SkyHanni$validateProperty(Property property, CallbackInfoReturnable<Boolean> cir) {
        if (property.signature() != null && property.signature().isEmpty()) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }

}
