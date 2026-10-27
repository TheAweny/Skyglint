package dev.skyglint.mixin;

import java.util.Comparator;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Preserve vanilla listOrder/spectator/team/name ordering when interpreting adjacent widget rows. */
@Mixin(PlayerTabOverlay.class)
public interface PlayerTabOverlayAccessor {
    @Accessor("header") Component skyglintHeader();
    @Accessor("footer") Component skyglintFooter();
    @Accessor("PLAYER_COMPARATOR")
    static Comparator<PlayerInfo> skyglintComparator() {
        throw new AssertionError("Mixin accessor was not transformed");
    }
}
