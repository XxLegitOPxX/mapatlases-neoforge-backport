package pepjebs.mapatlases.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pepjebs.mapatlases.MapAtlasesMod;
import pepjebs.mapatlases.capabilities.MapCollectionCap;
import pepjebs.mapatlases.capabilities.MapKey;
import pepjebs.mapatlases.config.MapAtlasesConfig;
import pepjebs.mapatlases.integration.SupplementariesCompat;
import pepjebs.mapatlases.networking.C2S2COpenAtlasScreenPacket;
import pepjebs.mapatlases.networking.MapAtlasesNetworking;
import pepjebs.mapatlases.utils.*;

import java.util.List;
import java.util.Optional;

public class MapAtlasItem extends Item {

    protected static final String EMPTY_MAPS_NBT = "empty";
    protected static final String LOCKED_NBT = "locked";
    protected static final String SELECTED_NBT = "selected";
    public static final String HEIGHT_NBT = "height";
    public static final String TYPE_NBT = "type";
    private static final String SHARE_TAG = "map_cap";

    public MapAtlasItem(Properties settings) {
        super(settings);
    }


    @Nullable
    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new Provider(LazyOptional.of(MapCollectionCap::new));
    }

    // maybe not needed, could be in cap itself
    private record Provider(
            LazyOptional<MapCollectionCap> capInstance) implements ICapabilitySerializable<CompoundTag> {
        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return MapCollectionCap.ATLAS_CAP_TOKEN.orEmpty(cap, capInstance);
        }

        @Override
        public CompoundTag serializeNBT() {
            return capInstance.resolve().get().serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            capInstance.resolve().get().deserializeNBT(nbt);
        }
    }


    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag isAdvanced) {
        super.appendHoverText(stack, level, tooltip, isAdvanced);

        if (level != null && stack.hasTag()) {
            MapCollectionCap maps = getMaps(stack, level);
            int mapSize = maps.getCount();
            int empties = getEmptyMaps(stack);
            if (getMaxMapCount() != -1 && mapSize + empties >= getMaxMapCount()) {
                tooltip.add(Component.translatable("item.map_atlases.atlas.tooltip_full", "", null)
                        .withStyle(ChatFormatting.ITALIC).withStyle(ChatFormatting.GRAY));
            }
            tooltip.add(Component.translatable("item.map_atlases.atlas.tooltip_maps", mapSize).withStyle(ChatFormatting.GRAY));
            if (MapAtlasesConfig.requireEmptyMapsToExpand.get() &&
                    MapAtlasesConfig.enableEmptyMapEntryAndFill.get()) {
                // If there are no maps & no empty maps, the atlas is "inactive", so display how many empty maps
                // they *would* receive if they activated the atlas
                if (mapSize + empties == 0) {
                    empties = MapAtlasesConfig.pityActivationMapCount.get();
                }
                tooltip.add(Component.translatable("item.map_atlases.atlas.tooltip_empty", empties).withStyle(ChatFormatting.GRAY));
            }

            tooltip.add(Component.translatable("filled_map.scale", 1 << maps.getScale()).withStyle(ChatFormatting.GRAY));

            if (isLocked(stack)) {
                tooltip.add(Component.translatable("item.map_atlases.atlas.tooltip_locked").withStyle(ChatFormatting.GRAY));
            }
            Slice selected = getSelectedSlice(stack, level.dimension());
            Integer slice = selected.height();
            if (slice != null) {
                tooltip.add(Component.translatable("item.map_atlases.atlas.tooltip_slice", slice).withStyle(ChatFormatting.GRAY));
            }
            var type = selected.type();
            if (type != MapType.VANILLA) {
                tooltip.add(Component.translatable("item.map_atlases.atlas.tooltip_type", type.getName()).withStyle(ChatFormatting.GRAY));
            }
            if(MapAtlasesMod.SUPPLEMENTARIES && SupplementariesCompat.hasAntiqueInk(stack)){
                tooltip.add(Component.translatable("item.map_atlases.atlas.supplementaries_antique").withStyle(ChatFormatting.GRAY));
            }
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        CompoundTag tag = stack.getOrCreateTag();
        convertOldAtlas(level, stack);
        if (player.isSecondaryUseActive()) {
            boolean locked = !tag.getBoolean(LOCKED_NBT);
            tag.putBoolean(LOCKED_NBT, locked);
            if (player.level().isClientSide) {
                player.displayClientMessage(Component.translatable(locked ? "message.map_atlases.locked" : "message.map_atlases.unlocked"), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (player instanceof ServerPlayer sp) {
            syncAndOpenGui(sp, stack, null, false);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static void convertOldAtlas(Level level, ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        //convert old atlas
        if (tag.contains("maps")) {
            MapCollectionCap maps = getMaps(stack, level);
            for (var i : tag.getIntArray("maps")) {
                maps.add(i, level);
            }
            tag.remove("maps");
        }
    }

    // I hate this
    @Nullable
    @Override
    public CompoundTag getShareTag(ItemStack stack) {
        CompoundTag baseTag = stack.getTag();
        var cap = stack.getCapability(MapCollectionCap.ATLAS_CAP_TOKEN, null).resolve().get();
        if (baseTag == null) baseTag = new CompoundTag();
        baseTag = baseTag.copy();
        baseTag.put(SHARE_TAG, cap.serializeNBT());
        return baseTag;
    }

    @Override
    public void readShareTag(ItemStack stack, @Nullable CompoundTag tag) {
        if (tag != null && tag.contains(SHARE_TAG)) {
            CompoundTag capTag = tag.getCompound(SHARE_TAG);
            tag.remove(SHARE_TAG);
            var cap = stack.getCapability(MapCollectionCap.ATLAS_CAP_TOKEN, null).resolve().get();
            cap.deserializeNBT(capTag);
        }
        stack.setTag(tag);
    }

    // convert lectern
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return super.useOn(context);
        }
        BlockPos blockPos = context.getClickedPos();

        Level level = context.getLevel();
        BlockState blockState = level.getBlockState(blockPos);
        ItemStack stack = context.getItemInHand();
        if (blockState.is(Blocks.LECTERN)) {
            if (level.getBlockEntity(blockPos) instanceof AtlasLectern ah) {
                ah.mapatlases$setAtlas(player, stack);
                //height.sendBlockUpdated(blockPos, blockState, blockState, 3);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (blockState.is(BlockTags.BANNERS)) {
            if (!level.isClientSide) {

                MapCollectionCap maps = getMaps(stack, level);
                MapDataHolder mapState = maps.select(MapKey.at(maps.getScale(), player, getSelectedSlice(stack, level.dimension())));
                if (mapState == null) return InteractionResult.FAIL;
                boolean didAdd = mapState.data.toggleBanner(level, blockPos);
                if (!didAdd)
                    return InteractionResult.FAIL;
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        } else {
            //others deco

            return super.useOn(context);
        }
    }


    // Utilities functions


    public static void syncAndOpenGui(ServerPlayer player, ItemStack atlas, @Nullable BlockPos lecternPos, boolean pinOnly) {
        if (atlas.isEmpty()) return;
        //we need to send all data for all dimensions as they are not sent automatically
        MapCollectionCap maps = MapAtlasItem.getMaps(atlas, player.level());
        for (var info : maps.getAll()) {
            // update all maps and sends them to player, if needed
            MapAtlasesAccessUtils.updateMapDataAndSync(info, player, atlas, InteractionResult.PASS);
        }
        MapAtlasesNetworking.sendToClientPlayer(player, new C2S2COpenAtlasScreenPacket(lecternPos, pinOnly));
    }

    public static void setSelectedSlice(ItemStack stack, Slice slice) {
        MapType t = slice.type();
        Integer h = slice.height();
        var dimension = slice.dimension();
        if (h == null && t == MapType.VANILLA) {
            CompoundTag tag = stack.getTagElement(SELECTED_NBT);
            if (tag != null) {
                tag.remove(dimension.location().toString());
            }

        } else {
            CompoundTag tag = stack.getOrCreateTagElement(SELECTED_NBT);
            tag.put(dimension.location().toString(), slice.save());
        }
    }
    //TODO:
/*
    public static boolean decreaseSlice(ItemStack atlas, Level level) {
        MapCollectionCap maps = MapAtlasItem.getMaps(atlas, level);
        int current = selectedSlice.heightOrTop();
        MapType type = selectedSlice.type();
        ResourceKey<Level> dim = selectedSlice.dimension();
        Integer newHeight = maps.getHeightTree(dim, type).floor(current - 1);
        return updateSlice(Slice.of(type, newHeight, dim));
    }

    //TODO: make static
    public static boolean increaseSlice(ItemStack atlas, Level level) {
        MapCollectionCap maps = MapAtlasItem.getMaps(atlas, level);
        int current = selectedSlice.heightOrTop();
        MapType type = selectedSlice.type();
        ResourceKey<Level> dim = selectedSlice.dimension();
        Integer newHeight = maps.getHeightTree(dim, type).ceiling(current + 1);
        return updateSlice(Slice.of(type, newHeight, dim));
    }*/
    public static MapCollectionCap getMaps(ItemStack stack, Level level) {
        Optional<MapCollectionCap> resolve = stack.getCapability(MapCollectionCap.ATLAS_CAP_TOKEN, null).resolve();
        if (resolve.isEmpty()) {
            throw new AssertionError("Map Atlas capability was empty. How is this possible? Culprit itemstack " + stack);
        }
        MapCollectionCap cap = resolve.get();
        if (!cap.isInitialized()) cap.initialize(level);
        return cap;
    }

    public static int getMaxMapCount() {
        return MapAtlasesConfig.maxMapCount.get();
    }

    public static int getEmptyMaps(ItemStack atlas) {
        CompoundTag tag = atlas.getTag();
        return tag != null && tag.contains(EMPTY_MAPS_NBT) ? tag.getInt(EMPTY_MAPS_NBT) : 0;
    }

    public static void setEmptyMaps(ItemStack stack, int count) {
        stack.getOrCreateTag().putInt(EMPTY_MAPS_NBT, count);
    }

    public static void increaseEmptyMaps(ItemStack stack, int count) {
        setEmptyMaps(stack, getEmptyMaps(stack) + count);
    }

    public static boolean isLocked(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(LOCKED_NBT);
    }

    @NotNull
    public static Slice getSelectedSlice(ItemStack stack, ResourceKey<Level> dimension) {
        CompoundTag tag = stack.getTagElement(SELECTED_NBT);
        if (tag != null) {
            String string = dimension.location().toString();
            if (tag.contains(string)) {
                var t = tag.getCompound(string);
                return Slice.parse(t, dimension);
            }
        }
        return Slice.of(MapType.VANILLA, null, dimension);
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player pPlayer) {
        super.onCraftedBy(stack, level, pPlayer);

        validateSelectedSlices(stack, level);
        convertOldAtlas(level, stack);
    }

    private static void validateSelectedSlices(ItemStack pStack, Level level) {
        // Populate default slices
        var maps = getMaps(pStack, level);
        var dim = maps.getAvailableDimensions();
        for (var d : dim) {
            for (var k : maps.getAvailableTypes(d)) {
                var av = maps.getHeightTree(d, k);
                if (!av.contains(getSelectedSlice(pStack, d).heightOrTop())) {
                    setSelectedSlice(pStack, Slice.of(k, av.first(), d));
                }
            }
        }
    }

}
