package dev.infotrans.book;

import dev.infotrans.config.ModConfig;
import dev.infotrans.translate.TranslationService;
import dev.infotrans.ui.TranslatedBookScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.LecternScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Translates whole written books: every page of the book is sent (chunked, so
 * neighbouring pages keep their context), then a reader screen shows the
 * translated pages with the original always one click away.
 */
public final class BookTranslate {
    private BookTranslate() {
    }

    private static final int PAGES_PER_REQUEST = 4;

    /** screens -> the BookAccess captured when the screen was built */
    private static final Map<Screen, BookViewScreen.BookAccess> ACCESSES =
            java.util.Collections.synchronizedMap(new WeakHashMap<>());

    public static void capture(Screen screen, BookViewScreen.BookAccess access) {
        if (access != null && access.getPageCount() > 0) {
            ACCESSES.put(screen, access);
        }
    }

    public static void translateAndOpen(Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        ModConfig cfg = ModConfig.get();
        if (!cfg.enabled || !cfg.translateBooks) {
            feedback(mc, "§c[Information Translation] 翻译功能已关闭");
            return;
        }

        BookViewScreen.BookAccess access = ACCESSES.get(screen);
        if (access == null) {
            ItemStack stack = null;
            if (screen instanceof LecternScreen lectern) {
                stack = lectern.getMenu().getBook();
            }
            if (stack == null) {
                LocalPlayer player = mc.player;
                if (player != null) {
                    if (isWrittenBook(player.getMainHandItem())) {
                        stack = player.getMainHandItem();
                    } else if (isWrittenBook(player.getOffhandItem())) {
                        stack = player.getOffhandItem();
                    }
                }
            }
            if (stack != null) {
                access = BookViewScreen.BookAccess.fromItem(stack);
            }
        }

        if (access == null || access.getPageCount() == 0) {
            feedback(mc, "§c[Information Translation] 无法读取书本内容");
            return;
        }

        List<String> pages = new ArrayList<>();
        for (int i = 0; i < access.getPageCount(); i++) {
            pages.add(access.getPage(i).getString());
        }

        TranslatedBookScreen reader = new TranslatedBookScreen(screen, pages);
        mc.setScreen(reader);

        // chunked requests keep neighbouring pages in one prompt (context!) while
        // staying inside the free rate limits; results appear page by page
        for (int from = 0; from < pages.size(); from += PAGES_PER_REQUEST) {
            int start = from;
            int end = Math.min(from + PAGES_PER_REQUEST, pages.size());
            TranslationService.translate(pages.subList(start, end), result -> mc.execute(() -> {
                if (result.error() != null) {
                    reader.setError(start, result.error());
                } else {
                    reader.setPages(start, result.translated());
                }
            }));
        }
    }

    private static boolean isWrittenBook(ItemStack stack) {
        return stack != null && stack.has(DataComponents.WRITTEN_BOOK_CONTENT);
    }

    private static void feedback(Minecraft mc, String message) {
        if (mc.gui != null) {
            mc.gui.setOverlayMessage(Component.literal(message), false);
        }
    }
}
