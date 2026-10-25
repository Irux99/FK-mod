package fr.irux.fk2000.client;

import com.mojang.logging.LogUtils;
import fr.irux.fk2000.compat.SbwAssemblyIntegration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/** Adds a filtered FK catalogue to the real SBW menu, retaining its recipe/crafting packet and checks. */
@Mod.EventBusSubscriber(modid = "iruxfk2000", value = Dist.CLIENT)
public final class SbwAssemblyTab {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SCREEN = "com.atsuishio.superbwarfare.client.screens.VehicleAssemblingScreen";
    private static final Map<Screen, State> STATES = new WeakHashMap<>();
    private static boolean reportedFailure;
    private SbwAssemblyTab() {}

    @SubscribeEvent
    public static void init(ScreenEvent.Init.Post event) {
        if (!event.getScreen().getClass().getName().equals(SCREEN)) return;
        State state = STATES.computeIfAbsent(event.getScreen(), ignored -> new State());
        state.add = event::addListener;
        state.remove = event::removeListener;
        install(event.getScreen(), state);
    }

    @SubscribeEvent
    public static void render(ScreenEvent.Render.Pre event) {
        State state = STATES.get(event.getScreen());
        if (state != null) install(event.getScreen(), state);
    }

    @SubscribeEvent
    public static void close(ScreenEvent.Closing event) { STATES.remove(event.getScreen()); }

    @SubscribeEvent
    public static void categoryClick(ScreenEvent.MouseButtonPressed.Pre event) {
        State state = STATES.get(event.getScreen());
        if (state == null || !state.selected) return;
        for (GuiEventListener child : event.getScreen().children()) {
            if (child.getClass().getSimpleName().equals("CategoryButton") && child.isMouseOver(event.getMouseX(), event.getMouseY())) {
                state.selected = false;
                return;
            }
        }
    }

    private static void install(Screen screen, State state) {
        if (state.failed || Minecraft.getInstance().level == null) return;
        Recipe<?> recipe = Minecraft.getInstance().level.getRecipeManager().byKey(SbwAssemblyIntegration.RECIPE).orElse(null);
        if (recipe == null) return;
        try {
            int left = (screen.width - 356) / 2;
            int top = (screen.height - 181) / 2;
            if (state.tab == null || !screen.children().contains(state.tab)) {
                state.tab = Button.builder(Component.literal("FK"), button -> {
                    state.selected = true;
                    select(screen, state, recipe, left, top);
                }).bounds(left, top + 159, 22, 22).tooltip(Tooltip.create(Component.translatable("screen.iruxfk2000.assembly_tab"))).build();
                state.add.accept(state.tab);
            }
            if (state.selected) {
                Object current = field(screen, "currentRecipes").get(screen);
                if (!(current instanceof List<?> ids) || ids.size() != 1 || !SbwAssemblyIntegration.RECIPE.equals(ids.get(0))
                        || state.entry == null || !screen.children().contains(state.entry)) select(screen, state, recipe, left, top);
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            fail(state, error);
        }
    }

    private static void select(Screen screen, State state, Recipe<?> recipe, int left, int top) {
        try {
            field(screen, "currentRecipes").set(screen, new ArrayList<ResourceLocation>(List.of(SbwAssemblyIntegration.RECIPE)));
            field(screen, "currentRecipe").set(screen, recipe);
            field(screen, "pageIndex").setInt(screen, 0);
            Method calculate = screen.getClass().getMethod("calculateMaterialCount", recipe.getClass());
            calculate.invoke(screen, recipe);
            for (GuiEventListener child : List.copyOf(screen.children())) {
                String name = child.getClass().getSimpleName();
                if (name.equals("RecipeButton") || name.equals("PageButton") || child == state.entry) state.remove.accept(child);
                if (name.equals("CategoryButton")) child.getClass().getMethod("setSelected", boolean.class).invoke(child, false);
            }
            state.entry = Button.builder(Component.literal("FK-2000"), button -> {}).bounds(left + 26, top + 21, 80, 16)
                    .tooltip(Tooltip.create(Component.translatable("screen.iruxfk2000.assembly_recipe"))).build();
            state.add.accept(state.entry);
        } catch (ReflectiveOperationException | RuntimeException error) {
            fail(state, error);
        }
    }

    private static Field field(Screen screen, String name) throws ReflectiveOperationException {
        Field field = screen.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void fail(State state, Exception error) {
        state.failed = true;
        if (!reportedFailure) {
            reportedFailure = true;
            LOGGER.warn("FK-2000 assembly tab unavailable; its native recipe remains in SBW's Defense category", error);
        }
    }

    private static final class State {
        Consumer<GuiEventListener> add, remove;
        AbstractWidget tab, entry;
        boolean selected, failed;
    }
}
