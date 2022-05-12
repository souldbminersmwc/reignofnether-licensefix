package com.solegendary.reignofnether.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.units.Unit;
import com.solegendary.reignofnether.units.UnitClientEvents;
import com.solegendary.reignofnether.util.MyRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.model.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;

public class HudClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();

    private static ArrayList<Button> unitButtons = new ArrayList<>();
    private static final ArrayList<Button> actionButtons = new ArrayList<>(Arrays.asList(
            ActionButtons.attack,
            ActionButtons.stop,
            ActionButtons.hold,
            ActionButtons.move
    ));
    // unit type that is selected in the list of unit icons
    public static Entity hudSelectedUnit = null;
    public static Model hudSelectedModel = null;

    // if we are rendering > this amount, then just render an empty icon with +N for the remaining units
    private static final int maxUnitButtons = 8;

    // eg. entity.reignofnether.zombie_unit -> zombie
    private static String getSimpleUnitName(Entity unit) {
        return unit.getName().getString()
            .replace(" ","")
            .replace("entity.reignofnether.","")
            .replace("_unit","");
    }

    @SubscribeEvent
    public static void onDrawScreen(ScreenEvent.DrawScreenEvent evt) {
        String screenName = evt.getScreen().getTitle().getString();
        if (!OrthoviewClientEvents.isEnabled() || !screenName.equals("topdowngui_container"))
            return;
        if (MC.level == null)
            return;

        int mouseX = evt.getMouseX();
        int mouseY = evt.getMouseY();

        ArrayList<LivingEntity> units = new ArrayList<>();
        unitButtons = new ArrayList<>();

        for (int id: UnitClientEvents.getSelectedUnitIds()) {
            Entity entity = MC.level.getEntity(id);
            if (entity instanceof LivingEntity)
                units.add((LivingEntity) entity);
        }

        // sort and hudSelect the first unit type in the list
        units.sort(Comparator.comparing(HudClientEvents::getSimpleUnitName));

        if (units.size() <= 0)
            hudSelectedUnit = null;
        else if (hudSelectedUnit == null)
            hudSelectedUnit = units.get(0);

        // create all of the unit buttons for this frame
        int screenWidth = MC.getWindow().getGuiScaledWidth();
        int screenHeight = MC.getWindow().getGuiScaledHeight();

        int iconSize = 14;
        int iconFrameSize = Button.iconFrameSize;

        for (LivingEntity unit : units) {

            if (unitButtons.size() < maxUnitButtons) {
                // mob head icon
                String unitName = getSimpleUnitName(unit);

                unitButtons.add(new Button(
                        unitName,
                        iconSize,
                        "textures/mobheads/" + unitName + ".png",
                        unit,
                        () -> getSimpleUnitName(hudSelectedUnit).equals(unitName),
                        () -> {
                            // click to select this unit type as a group
                            if (getSimpleUnitName(hudSelectedUnit).equals(unitName)) {
                                UnitClientEvents.setSelectedUnitIds(new ArrayList<>());
                                UnitClientEvents.addSelectedUnitId(unit.getId());
                            } else { // select this one specific unit
                                hudSelectedUnit = unit;
                            }
                        }
                ));
            }
        }

        // ---------------------------
        // Unit icons using mob heads
        // ---------------------------
        int numUnitButtons = Math.min(units.size(), maxUnitButtons);
        int blitX = (screenWidth / 2) - (numUnitButtons * iconFrameSize / 2);
        int blitY = screenHeight - iconFrameSize;

        for (Button unitButton : unitButtons) {
            unitButton.render(evt.getPoseStack(), blitX, blitY, mouseX, mouseY);
            unitButton.renderHealthBar(evt.getPoseStack());
            blitX += iconFrameSize;
        }

        // -------------------------------------------------------
        // Unit action icons (attack, stop, move, abilities etc.)
        // -------------------------------------------------------

        if (UnitClientEvents.getSelectedUnitIds().size() > 0) {
            blitX = 0;
            blitY = screenHeight - iconFrameSize;
            for (Button actionButton : actionButtons) {
                actionButton.render(evt.getPoseStack(), blitX, blitY, mouseX, mouseY);
                actionButton.checkPressed();
                blitX += iconFrameSize;
            }
            blitX = 0;
            blitY = screenHeight - (iconFrameSize * 2);
            for (LivingEntity unit : units) {
                if (getSimpleUnitName(unit).equals(getSimpleUnitName(hudSelectedUnit))) {
                    for (AbilityButton ability : ((Unit) unit).getAbilities()) {
                        ability.render(evt.getPoseStack(), blitX, blitY, mouseX, mouseY);
                        ability.checkPressed();
                        blitX += iconFrameSize;
                    }
                    break;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLivingEntity(RenderLivingEvent.Pre<? extends LivingEntity, ? extends Model> evt) {
        LivingEntity entity = evt.getEntity();
        if (hudSelectedUnit == null)
            hudSelectedModel = null;
        else if (entity == hudSelectedUnit)
            hudSelectedModel = evt.getRenderer().getModel();
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGameOverlayEvent.Post evt) {
        /*
        MiscUtil.drawDebugStrings(evt.getMatrixStack(), MC.font, new String[] {
                "showOnlyReducedInfo: " + MC.showOnlyReducedInfo()
        });
         */
        // ------------------------------------------------
        // Unit head portrait (based on selected unit type)
        // ------------------------------------------------
        if (hudSelectedUnit != null && hudSelectedModel != null) {
            // icon frame
            ResourceLocation iconFrameResource = new ResourceLocation(ReignOfNether.MOD_ID, "textures/hud/unit_frame.png");
            RenderSystem.setShaderTexture(0, iconFrameResource);
            GuiComponent.blit(evt.getMatrixStack(),
                    0,0, 0,
                    0,0, // where on texture to start drawing from
                    42, 42, // dimensions of blit texture
                    42, 42 // size of texture itself (if < dimensions, texture is repeated)
            );
            MyRenderer.setNonHeadModelVisibility(hudSelectedModel, false);
            MyRenderer.drawEntityOnScreen(evt.getMatrixStack(), 20, 35, 13, -80, -20, (LivingEntity) hudSelectedUnit, 1.0f);
            MyRenderer.setNonHeadModelVisibility(hudSelectedModel, true);
        }
    }

    @SubscribeEvent
    public static void onMouseRelease(ScreenEvent.MouseReleasedEvent.Post evt) {
        int mouseX = (int) evt.getMouseX();
        int mouseY = (int) evt.getMouseY();

        ArrayList<Button> buttons = new ArrayList<>();
        buttons.addAll(actionButtons);
        buttons.addAll(unitButtons);

        for (Button button : buttons)
            button.checkClicked(mouseX, mouseY);
    }
}
