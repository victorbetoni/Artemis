package com.wynntils.features.consumables;

import com.wynntils.core.components.Managers;
import com.wynntils.core.components.Models;
import com.wynntils.core.components.Services;
import com.wynntils.core.consumers.features.Feature;
import com.wynntils.core.persisted.Persisted;
import com.wynntils.core.persisted.config.Category;
import com.wynntils.core.persisted.config.Config;
import com.wynntils.core.persisted.config.ConfigCategory;
import com.wynntils.core.persisted.config.HiddenConfig;
import com.wynntils.core.text.StyledText;
import com.wynntils.mc.event.*;
import com.wynntils.mc.extension.ScreenExtension;
import com.wynntils.models.consumables.GenericConsumableModel;
import com.wynntils.models.containers.type.SearchableContainerType;
import com.wynntils.models.items.WynnItem;
import com.wynntils.models.items.WynnItemCache;
import com.wynntils.models.items.items.game.CraftedConsumableItem;
import com.wynntils.screens.base.widgets.ConsumableSearchWidget;
import com.wynntils.screens.base.widgets.ItemSearchHelperWidget;
import com.wynntils.screens.base.widgets.ItemSearchWidget;
import com.wynntils.screens.base.widgets.SearchWidget;
import com.wynntils.services.itemfilter.type.ItemSearchQuery;
import com.wynntils.utils.colors.CommonColors;
import com.wynntils.utils.colors.CustomColor;
import com.wynntils.utils.mc.McUtils;
import com.wynntils.utils.render.FontRenderer;
import com.wynntils.utils.render.RenderUtils;
import com.wynntils.utils.render.Texture;
import com.wynntils.utils.wynn.ContainerUtils;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ConfigCategory(Category.INVENTORY)
public class ConsumablesSearchFeature extends Feature {

    @Persisted
    public final HiddenConfig<List<GenericConsumableModel>> consumables = new HiddenConfig<>(new ArrayList<>());
    @Persisted
    public final Config<Boolean> filterInBank = new Config<>(true);
    @Persisted
    public final Config<Boolean> filterInBlockBank = new Config<>(true);
    @Persisted
    public final Config<Boolean> filterInBookshelf = new Config<>(true);
    @Persisted
    public final Config<Boolean> filterInMiscBucket = new Config<>(true);
    @Persisted
    public final Config<Boolean> filterInGuildBank = new Config<>(true);

    @Persisted
    public final Config<Boolean> filterInGuildMemberList = new Config<>(true);

    @Persisted
    public final Config<Boolean> filterInScrapMenu = new Config<>(true);

    @Persisted
    public final Config<CustomColor> highlightColor = new Config<>(CommonColors.MAGENTA);

    private GenericConsumableModel currentConsumable;
    private SearchableContainerType currentSearchableContainerType;
    private List<ConsumableSearchWidget> widgets;
    private ConsumableSearchWidget lastWidget;
    private static final int GUILD_BANK_SEARCH_DELAY = 500;
    private long guildBankLastSearch = 0;
    private boolean autoSearching = false;

    @SubscribeEvent
    public void onScreenInit(ScreenInitEvent event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        if (!(screen.getMenu() instanceof ChestMenu)) return;

        StyledText title = StyledText.fromComponent(screen.getTitle());

        // This is screen.topPos and screen.leftPos, but they are not calculated yet when this is called
        int renderX = (screen.width - screen.imageWidth) / 2;
        int renderY = (screen.height - screen.imageHeight) / 2;

        SearchableContainerType searchableContainerType = getCurrentSearchableContainerType(title);
        if (searchableContainerType == null) return;

        currentSearchableContainerType = searchableContainerType;
        addWidgets(((AbstractContainerScreen<ChestMenu>) screen), renderX, renderY);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onContainerRender(ContainerRenderEvent event) {
        if (widgets == null || widgets.isEmpty()) return;

        widgets.stream().filter(ConsumableSearchWidget::isHovered)
                .forEach(x -> event.getGuiGraphics()
                        .renderComponentTooltip(
                                FontRenderer.getInstance().getFont(),
                                x.getTooltipLines(),
                                event.getMouseX(),
                                event.getMouseY()));
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onRenderSlot(SlotRenderEvent.Pre e) {
        ItemStack itemStack = e.getSlot().getItem();
        Optional<WynnItem> wynnItemOpt = Models.Item.getWynnItem(itemStack);
        if (wynnItemOpt.isEmpty()) return;

        Boolean result = wynnItemOpt.get().getCache().get(WynnItemCache.SEARCHED_KEY);
        if (result == null || !result) return;

        RenderUtils.drawArc(e.getPoseStack(), highlightColor.get(), e.getSlot().x, e.getSlot().y, 200, 1f, 6, 8);
    }

    @SubscribeEvent
    public void onContainerSetContent(ContainerSetContentEvent.Post event) {
        forceUpdateSearch();

        if (autoSearching && McUtils.mc().screen instanceof AbstractContainerScreen<?> abstractContainerScreen) {
            tryAutoSearch(abstractContainerScreen);
        }
    }

    @SubscribeEvent
    public void onContainerSetSlot(ContainerSetSlotEvent.Pre event) {
        forceUpdateSearch();
    }

    @SubscribeEvent
    public void onContainerClose(ContainerCloseEvent.Post event) {
        currentConsumable = null;
        currentSearchableContainerType = null;
        autoSearching = false;
        guildBankLastSearch = 0;
    }

    private void tryAutoSearch(AbstractContainerScreen<?> abstractContainerScreen) {
        if (!autoSearching) return;
        if (currentSearchableContainerType == SearchableContainerType.GUILD_BANK) {
            long diff = System.currentTimeMillis() - guildBankLastSearch;
            if (diff < GUILD_BANK_SEARCH_DELAY) {
                Managers.TickScheduler.scheduleLater(
                        () -> tryAutoSearch(abstractContainerScreen), (int) (GUILD_BANK_SEARCH_DELAY - diff) / 50);
                return;
            }
            guildBankLastSearch = System.currentTimeMillis();
        }

        StyledText name = StyledText.fromComponent(abstractContainerScreen
                .getMenu()
                .getItems()
                .get(currentSearchableContainerType.getNextItemSlot())
                .getHoverName());

        if (!name.matches(currentSearchableContainerType.getNextItemPattern())) {
            autoSearching = false;
            return;
        }

        ContainerUtils.clickOnSlot(
                currentSearchableContainerType.getNextItemSlot(),
                abstractContainerScreen.getMenu().containerId,
                GLFW.GLFW_MOUSE_BUTTON_LEFT,
                abstractContainerScreen.getMenu().getItems());
    }

    private SearchableContainerType getCurrentSearchableContainerType(StyledText title) {
        SearchableContainerType containerType = SearchableContainerType.getContainerType(title);

        if (containerType == SearchableContainerType.ACCOUNT_BANK && filterInBank.get()) {
            return SearchableContainerType.ACCOUNT_BANK;
        }

        if (containerType == SearchableContainerType.CHARACTER_BANK && filterInBank.get()) {
            return SearchableContainerType.CHARACTER_BANK;
        }

        if (containerType == SearchableContainerType.BLOCK_BANK && filterInBlockBank.get()) {
            return SearchableContainerType.BLOCK_BANK;
        }

        if (containerType == SearchableContainerType.BOOKSHELF && filterInBookshelf.get()) {
            return SearchableContainerType.BOOKSHELF;
        }

        if (containerType == SearchableContainerType.MISC_BUCKET && filterInMiscBucket.get()) {
            return SearchableContainerType.MISC_BUCKET;
        }

        if (containerType == SearchableContainerType.GUILD_BANK && filterInGuildBank.get()) {
            return SearchableContainerType.GUILD_BANK;
        }

        if (containerType == SearchableContainerType.MEMBER_LIST && filterInGuildMemberList.get()) {
            return SearchableContainerType.MEMBER_LIST;
        }

        if (containerType == SearchableContainerType.SCRAP_MENU && filterInScrapMenu.get()) {
            return SearchableContainerType.SCRAP_MENU;
        }

        return null;
    }

    @SubscribeEvent
    public void onInventoryMouseClick(InventoryMouseClickedEvent event) {
        if (lastWidget == null) return;

        if (lastWidget.mouseClicked(event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
            return;
        }
    }

    private void addWidgets(AbstractContainerScreen<ChestMenu> screen, int renderX, int renderY) {
        int maxHeight = screen.imageHeight/2;
        int currentY = 5;
        int currentX = 5;
        for (GenericConsumableModel genericConsumableModel : consumables.get()) {
            currentY = currentY + 15 > maxHeight ? 5 : currentY + 15;
            currentX = currentX + 15 > maxHeight ? currentX + 15 : currentX;
            Texture texture = switch (genericConsumableModel.getType()) {
                case FOOD -> Texture.COOKING_STATION;
                case POTION -> Texture.ALCHEMIST_STATION;
                case SCROLL -> Texture.SCRIBING_STATION;
            };
            ConsumableSearchWidget widget = new ConsumableSearchWidget(
                    genericConsumableModel,
                    currentX, currentY, 15, 15, texture,
                    integer -> {
                        currentConsumable = genericConsumableModel;

                        if (lastWidget == null
                                || currentSearchableContainerType == null
                                || currentSearchableContainerType.getNextItemSlot() == -1
                                || !(McUtils.mc().screen instanceof AbstractContainerScreen<?> abstractContainerScreen)
                                || !(abstractContainerScreen.getMenu() instanceof ChestMenu chestMenu)) return;


                        autoSearching = true;
                        matchItems(currentConsumable, chestMenu);

                        tryAutoSearch(abstractContainerScreen);

                        matchItems(genericConsumableModel, screen.getMenu());
                    }, null
            );
            screen.addRenderableWidget(widget);
        }
    }

    private void matchItems(GenericConsumableModel consumable, ChestMenu chestMenu) {
        if (consumable == null) return;

        Container container = chestMenu.getContainer();
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (!currentSearchableContainerType.getBounds().getSlots().contains(i)) continue;

            ItemStack itemStack = container.getItem(i);

            Optional<WynnItem> wynnItemOpt = Models.Item.getWynnItem(itemStack);
            if (wynnItemOpt.isEmpty()) continue;

            if(!(wynnItemOpt.get() instanceof CraftedConsumableItem consumableItem)) {
                return;
            }

            boolean filtered = consumableItem.getIdentifications().stream().allMatch(x ->
                    currentConsumable.getStats().containsKey(x.statType())
                            && currentConsumable.getStats().get(x.statType()).inRange(x.value()));

            wynnItemOpt.get().getCache().store(WynnItemCache.SEARCHED_KEY, filtered);
            if (filtered) {
                autoSearching = false;
            }
        }
    }

    private void forceUpdateSearch() {
        Screen screen = McUtils.mc().screen;
        if (widgets != null && !widgets.isEmpty()
                && screen instanceof AbstractContainerScreen<?> abstractContainerScreen
                && abstractContainerScreen.getMenu() instanceof ChestMenu chestMenu) {
            matchItems(currentConsumable, chestMenu);
        }
    }

}
