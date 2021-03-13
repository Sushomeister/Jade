package snownee.jade.addon.vanilla;

import mcp.mobius.waila.Waila;
import mcp.mobius.waila.api.IComponentProvider;
import mcp.mobius.waila.api.IDataAccessor;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.IServerDataProvider;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.TooltipPosition;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropsBlock;
import net.minecraft.block.JukeboxBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.SilverfishBlock;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MusicDiscItem;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.state.properties.ComparatorMode;
import net.minecraft.tileentity.JukeboxTileEntity;
import net.minecraft.tileentity.MobSpawnerTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.registries.ForgeRegistries;
import snownee.jade.VanillaPlugin;

public class VanillaProvider implements IComponentProvider, IServerDataProvider<TileEntity> {

    public static final VanillaProvider INSTANCE = new VanillaProvider();

    static final ResourceLocation OBJECT_NAME_TAG = new ResourceLocation(Waila.MODID, "object_name");

    @Override
    public ItemStack getStack(IDataAccessor accessor, IPluginConfig config) {
        if (config.get(VanillaPlugin.CONFIG_HIDE_SILVERFISH) && accessor.getBlock() instanceof SilverfishBlock)
            return new ItemStack(((SilverfishBlock) accessor.getBlock()).getMimickedBlock().asItem());

        if (accessor.getBlock() == Blocks.WHEAT)
            return new ItemStack(Items.WHEAT);

        if (accessor.getBlock() == Blocks.BEETROOTS)
            return new ItemStack(Items.BEETROOT);

        return ItemStack.EMPTY;
    }

    public void appendHead(ITooltip tooltip, IDataAccessor accessor, IPluginConfig config) {
        if (config.get(VanillaPlugin.CONFIG_HIDE_SILVERFISH) && accessor.getBlock() instanceof SilverfishBlock)
            tooltip.add(new StringTextComponent(String.format(Waila.CONFIG.get().getFormatting().getBlockName(), accessor.getPickedResult().getDisplayName().getString())), OBJECT_NAME_TAG);

        if (accessor.getBlock() == Blocks.SPAWNER && config.get(VanillaPlugin.CONFIG_SPAWNER_TYPE)) {
            MobSpawnerTileEntity spawner = (MobSpawnerTileEntity) accessor.getTileEntity();
            String name = I18n.format(accessor.getBlock().getTranslationKey());
            name = I18n.format("jade.spawner", name, spawner.getSpawnerBaseLogic().getCachedEntity().getDisplayName().getString());
            name = String.format(Waila.CONFIG.get().getFormatting().getBlockName(), name);
            tooltip.remove(OBJECT_NAME_TAG);
            tooltip.add(new StringTextComponent(name), OBJECT_NAME_TAG);
        }
    }

    @Override
    public void append(ITooltip tooltip, IDataAccessor accessor, IPluginConfig config) {
        if (accessor.getTooltipPosition() == TooltipPosition.HEAD) {
            appendHead(tooltip, accessor, config);
            return;
        }

        BlockState state = accessor.getBlockState();
        Block block = state.getBlock();

        if (config.get(VanillaPlugin.CONFIG_CROP_PROGRESS)) {
            if (block instanceof CropsBlock) {
                CropsBlock crop = (CropsBlock) block;
                addMaturityTooltip(tooltip, state.get(crop.getAgeProperty()) / (float) crop.getMaxAge());
            } else if (block == Blocks.MELON_STEM || block == Blocks.PUMPKIN_STEM) {
                addMaturityTooltip(tooltip, state.get(BlockStateProperties.AGE_0_7) / 7F);
            } else if (block == Blocks.COCOA) {
                addMaturityTooltip(tooltip, state.get(BlockStateProperties.AGE_0_2) / 2.0F);
            }
        }

        if (config.get(VanillaPlugin.CONFIG_LEVER) && block instanceof LeverBlock) {
            boolean active = state.get(BlockStateProperties.POWERED);
            tooltip.add(new TranslationTextComponent("tooltip.waila.state", new TranslationTextComponent("tooltip.waila.state_" + (active ? "on" : "off"))));
            return;
        }

        if (config.get(VanillaPlugin.CONFIG_REPEATER) && block == Blocks.REPEATER) {
            int delay = state.get(BlockStateProperties.DELAY_1_4);
            tooltip.add(new TranslationTextComponent("tooltip.waila.delay", TextFormatting.WHITE.toString() + delay));
            return;
        }

        if (config.get(VanillaPlugin.CONFIG_COMPARATOR) && block == Blocks.COMPARATOR) {
            ComparatorMode mode = state.get(BlockStateProperties.COMPARATOR_MODE);
            tooltip.add(new TranslationTextComponent("tooltip.waila.mode", new TranslationTextComponent("tooltip.waila.mode_" + (mode == ComparatorMode.COMPARE ? "comparator" : "subtractor"))));
            return;
        }

        if (config.get(VanillaPlugin.CONFIG_REDSTONE) && state.hasProperty(BlockStateProperties.POWER_0_15)) {
            tooltip.add(new TranslationTextComponent("tooltip.waila.power", TextFormatting.WHITE.toString() + state.get(BlockStateProperties.POWER_0_15)));
            return;
        }

        if (config.get(VanillaPlugin.CONFIG_JUKEBOX) && block == Blocks.JUKEBOX) {
            if (state.get(JukeboxBlock.HAS_RECORD) && accessor.getServerData().contains("record")) {
                try {
                    Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(accessor.getServerData().getString("record")));
                    if (item instanceof MusicDiscItem) {
                        tooltip.add(new TranslationTextComponent("record.nowPlaying", ((MusicDiscItem) item).getDescription()));
                    }
                } catch (Exception e) {
                    Waila.LOGGER.catching(e);
                }
            } else
                tooltip.add(new TranslationTextComponent("tooltip.waila.empty"));
        }
    }

    @Override
    public void appendServerData(CompoundNBT data, ServerPlayerEntity player, World world, TileEntity blockEntity) {
        if (blockEntity instanceof JukeboxTileEntity) {
            JukeboxTileEntity jukebox = (JukeboxTileEntity) blockEntity;
            ItemStack stack = jukebox.getRecord();
            if (!stack.isEmpty()) {
                data.putString("record", stack.getItem().getRegistryName().toString());
            }
        }
    }

    private static void addMaturityTooltip(ITooltip tooltip, float growthValue) {
        growthValue *= 100.0F;
        if (growthValue < 100.0F)
            tooltip.add(new TranslationTextComponent("tooltip.waila.crop_growth", TextFormatting.WHITE + String.format("%.0f%%", growthValue)));
        else
            tooltip.add(new TranslationTextComponent("tooltip.waila.crop_growth", new TranslationTextComponent("tooltip.waila.crop_mature").mergeStyle(TextFormatting.GREEN)));
    }
}