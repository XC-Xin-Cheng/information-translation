package dev.infotrans.mixin;

import dev.infotrans.book.BookTranslate;
import dev.infotrans.config.ModConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a small "译" button to every book reading screen (BookViewScreen and
 * its subclass LecternScreen, which reuses this init) that translates the
 * whole book and opens the translated reader.
 */
@Mixin(BookViewScreen.class)
public abstract class BookViewScreenMixin {

    @Shadow
    private BookViewScreen.BookAccess bookAccess;

    @Shadow
    protected abstract <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(T widget);

    @Inject(method = "init()V", at = @At("TAIL"))
    private void infotrans$addTranslateButton(CallbackInfo ci) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.enabled || !cfg.translateBooks) {
            return;
        }
        BookViewScreen self = (BookViewScreen) (Object) this;
        BookTranslate.capture(self, this.bookAccess);
        this.addRenderableWidget(Button.builder(Component.literal("译"),
                        b -> BookTranslate.translateAndOpen(self))
                .bounds(self.width - 24, 2, 20, 20)
                .build());
    }
}
