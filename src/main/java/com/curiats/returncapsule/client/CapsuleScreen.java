package com.curiats.returncapsule.client;

import com.curiats.returncapsule.menu.CapsuleMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;






public class CapsuleScreen extends AbstractContainerScreen<CapsuleMenu> {
    private static final ResourceLocation CHEST_TEXTURE =
            new ResourceLocation("textures/gui/container/generic_54.png");

    public CapsuleScreen(CapsuleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 222;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 129;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(CHEST_TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int controlY = this.topPos - 14;
        graphics.drawString(this.font, "<", this.leftPos + 2, controlY, 0x404040, false);
        graphics.drawString(this.font, ">", this.leftPos + this.imageWidth - 10, controlY, 0x404040, false);
        String page = (this.menu.getPage() + 1) + "/" + this.menu.pages();
        graphics.drawString(this.font, page,
                this.leftPos + this.imageWidth / 2 - this.font.width(page) / 2,
                controlY, 0x404040, false);

        
        
        
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int controlY = this.topPos - 14;
            if (mouseY >= controlY - 2 && mouseY <= controlY + 10) {
                if (mouseX >= this.leftPos && mouseX <= this.leftPos + 14) {
                    this.menu.setPage(this.menu.getPage() - 1);
                    if (this.minecraft != null && this.minecraft.gameMode != null) {
                        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 0);
                    }
                    return true;
                }
                if (mouseX >= this.leftPos + this.imageWidth - 16
                        && mouseX <= this.leftPos + this.imageWidth) {
                    this.menu.setPage(this.menu.getPage() + 1);
                    if (this.minecraft != null && this.minecraft.gameMode != null) {
                        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 1);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
