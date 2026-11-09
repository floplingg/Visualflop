package dev.tony.visuals.mixin;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/** Lets the payments helper replace its own chat lines (stacking, marking won / lost). */
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
    @Accessor("allMessages")
    List<GuiMessage> flopvisuals$allMessages();

    @Invoker("refreshTrimmedMessages")
    void flopvisuals$refresh();
}
