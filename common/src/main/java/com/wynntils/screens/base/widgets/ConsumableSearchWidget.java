package com.wynntils.screens.base.widgets;

import com.wynntils.models.consumables.GenericConsumableModel;
import com.wynntils.utils.render.Texture;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

public class ConsumableSearchWidget extends BasicTexturedButton {

    private GenericConsumableModel consumable;

    public ConsumableSearchWidget(GenericConsumableModel consumable, int x, int y, int width, int height, Texture texture, Consumer<Integer> onClick, List<Component> tooltip) {
        super(x, y, width, height, texture, onClick, tooltip);
        this.consumable = consumable;
    }
}
