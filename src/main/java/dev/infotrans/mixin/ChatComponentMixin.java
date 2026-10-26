package dev.infotrans.mixin;

import dev.infotrans.chat.ChatTranslate;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Single choke point: every chat line (player chat, server system messages,
 * client system messages) funnels into ChatComponent#addMessage(...) in 26.1.
 */
@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    @Inject(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("HEAD")
    )
    private void infotrans$onChatMessage(Component message, MessageSignature signature, GuiMessageSource source,
                                         GuiMessageTag tag, CallbackInfo ci) {
        ChatTranslate.onMessage(message);
    }
}
