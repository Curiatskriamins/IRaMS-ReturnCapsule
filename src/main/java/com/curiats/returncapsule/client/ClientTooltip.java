package com.curiats.returncapsule.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientTooltip {
    public static boolean isShiftDown() {
        return Screen.hasShiftDown();
    }

    private ClientTooltip() {
    }
}
