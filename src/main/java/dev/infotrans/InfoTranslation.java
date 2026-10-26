package dev.infotrans;

import com.mojang.blaze3d.platform.InputConstants;
import dev.infotrans.chat.ChatTranslate;
import dev.infotrans.config.ModConfig;
import dev.infotrans.sign.SignTranslate;
import dev.infotrans.ui.ModConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InfoTranslation implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("InformationTranslation");

    public static final String MOD_ID = "information_translation";

    private static KeyMapping openSettingsKey;
    private static KeyMapping toggleKey;

    @Override
    public void onInitializeClient() {
        ModConfig.load();

        openSettingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.information_translation.open_settings",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, KeyMapping.Category.MISC));
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.information_translation.toggle",
                InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openSettingsKey.consumeClick()) {
                mc.setScreen(new ModConfigScreen(null));
            }
            while (toggleKey.consumeClick()) {
                ModConfig cfg = ModConfig.get();
                cfg.enabled = !cfg.enabled;
                ModConfig.save();
                mc.gui.setOverlayMessage(Component.literal(
                        cfg.enabled ? "§a[Information Translation] 翻译已开启"
                                : "§c[Information Translation] 翻译已关闭"), false);
            }
            ChatTranslate.tick();
            SignTranslate.tick();
        });

        LOGGER.info("Information Translation loaded (client-side translation for chat / books / signs)");
    }
}
