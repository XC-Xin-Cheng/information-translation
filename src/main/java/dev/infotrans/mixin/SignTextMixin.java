package dev.infotrans.mixin;

import dev.infotrans.sign.SignTranslate;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.entity.SignText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

/**
 * In 26.1 every sign renderer (standing / hanging / wall) draws text through
 * SignText#getRenderMessages, so swapping the returned lines translates all
 * signs at once.
 */
@Mixin(SignText.class)
public class SignTextMixin {

    @Inject(method = "getRenderMessages", at = @At("HEAD"), cancellable = true)
    private void infotrans$translateSign(boolean filteringEnabled,
                                         Function<Component, FormattedCharSequence> transformer,
                                         CallbackInfoReturnable<FormattedCharSequence[]> cir) {
        FormattedCharSequence[] translated = SignTranslate.apply(this, filteringEnabled, transformer);
        if (translated != null) {
            cir.setReturnValue(translated);
        }
    }
}
