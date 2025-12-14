package com.texasoveja.villarium.mixin;

import com.texasoveja.villarium.common.entity.IVillagerFollow;
import com.texasoveja.villarium.common.entity.FollowPlayerGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Mixin(Villager.class)
public abstract class VillagerMixin extends AbstractVillager implements IVillagerFollow {

    @Unique
    private static final EntityDataAccessor<Boolean> IS_FOLLOWING = SynchedEntityData.defineId(Villager.class, EntityDataSerializers.BOOLEAN);

    public VillagerMixin(EntityType<? extends AbstractVillager> entityType, net.minecraft.world.level.Level level) {
        super(entityType, level);
    }

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    protected void villarium$defineData(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(IS_FOLLOWING, false);
    }

    @Override
    public boolean villarium$isFollowing() {
        return this.entityData.get(IS_FOLLOWING);
    }

    @Override
    public void villarium$setFollowing(boolean following) {
        this.entityData.set(IS_FOLLOWING, following);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    public void villarium$addSaveData(CompoundTag compound, CallbackInfo ci) {
        compound.putBoolean("VillariumIsFollowing", this.villarium$isFollowing());
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    public void villarium$readSaveData(CompoundTag compound, CallbackInfo ci) {
        this.villarium$setFollowing(compound.getBoolean("VillariumIsFollowing"));
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void villarium$onInit(EntityType<? extends AbstractVillager> entityType, net.minecraft.world.level.Level level, CallbackInfo ci) {
        Villager self = (Villager) (Object) this;
        this.goalSelector.addGoal(2, new FollowPlayerGoal(self, 0.7D));
    }

    // --- SISTEMA DE TRADEOS ---

    @Inject(method = "updateSpecialPrices", at = @At("TAIL"))
    private void villarium$updateTradesAndPrices(Player player, CallbackInfo ci) {
        if (this.level().isClientSide) return;

        Villager villager = (Villager) (Object) this;
        VillagerProfession profession = villager.getVillagerData().getProfession();
        int level = villager.getVillagerData().getLevel();
        BlockPos center = villager.blockPosition();

        Set<Block> nearbyBlocks = new HashSet<>();
        int range = 10;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-range, -range, -range), center.offset(range, range, range))) {
            nearbyBlocks.add(this.level().getBlockState(pos).getBlock());
        }

        boolean hasArmorStand = !this.level().getEntitiesOfClass(ArmorStand.class, new AABB(center).inflate(range)).isEmpty();
        boolean hasBoat = !this.level().getEntitiesOfClass(Boat.class, new AABB(center).inflate(range)).isEmpty();

        // Gestionar Tradeos Especiales (Ahora soporta múltiples simultáneos)
        villarium$handleSpecialTrades(villager, profession, level, nearbyBlocks, hasArmorStand, hasBoat);

        // Calcular Descuento
        int favoritePoints = villarium$countFavorites(profession, nearbyBlocks, hasArmorStand, hasBoat);
        int discount = Math.min(favoritePoints * 2, 30);

        // Aplicar descuentos SOLO a los no especiales
        for (MerchantOffer offer : villager.getOffers()) {
            if (villarium$isSpecialTrade(offer)) {
                offer.setSpecialPriceDiff(0); // Proteger precio especial
            } else {
                if (discount > 0) {
                    offer.setSpecialPriceDiff(offer.getSpecialPriceDiff() - discount);
                }
            }
        }
    }

    @Unique
    private void villarium$handleSpecialTrades(Villager villager, VillagerProfession prof, int level, Set<Block> blocks, boolean hasArmorStand, boolean hasBoat) {
        // Usamos una LISTA para permitir múltiples tradeos especiales a la vez
        List<MerchantOffer> validSpecialOffers = new ArrayList<>();

        // --- REGLAS DE TRADEOS (INDEPENDIENTES) ---
        // Usamos 'if' independientes y 'level >= X' para que no se borren al subir de nivel

        // Weaponsmith
        if (prof == VillagerProfession.WEAPONSMITH) {
            if (level >= 3 && blocks.contains(Blocks.GRINDSTONE))
                validSpecialOffers.add(createSpecial(new ItemStack(Items.EMERALD, 5), new ItemStack(Items.IRON_INGOT, 1), createEnchantedItem(Items.IRON_SWORD, Enchantments.SHARPNESS, 1)));

            if (level >= 4 && blocks.contains(Blocks.ANVIL))
                validSpecialOffers.add(createSpecial(new ItemStack(Items.EMERALD, 7), new ItemStack(Items.IRON_INGOT, 1), createEnchantedItem(Items.IRON_AXE, Enchantments.EFFICIENCY, 2)));

            if (level >= 5 && blocks.contains(Blocks.SMITHING_TABLE))
                validSpecialOffers.add(createSpecial(new ItemStack(Items.EMERALD, 18), new ItemStack(Items.DIAMOND, 1), createEnchantedItem(Items.DIAMOND_SWORD, Enchantments.UNBREAKING, 2)));
        }

        // Armorer
        else if (prof == VillagerProfession.ARMORER) {
            if (level >= 3 && blocks.contains(Blocks.BLAST_FURNACE))
                validSpecialOffers.add(createSpecial(new ItemStack(Items.EMERALD, 7), new ItemStack(Items.IRON_INGOT, 1), createEnchantedItem(Items.IRON_CHESTPLATE, Enchantments.PROTECTION, 1)));

            if (level >= 4 && blocks.contains(Blocks.ANVIL))
                validSpecialOffers.add(createSpecial(new ItemStack(Items.EMERALD, 6), ItemStack.EMPTY, createEnchantedItem(Items.IRON_BOOTS, Enchantments.FEATHER_FALLING, 2)));

            if (level >= 5 && blocks.contains(Blocks.FURNACE))
                validSpecialOffers.add(createSpecial(new ItemStack(Items.EMERALD, 20), new ItemStack(Items.DIAMOND, 1), createEnchantedItem(Items.DIAMOND_LEGGINGS, Enchantments.PROTECTION, 2)));
        }

        // Toolsmith
        else if (prof == VillagerProfession.TOOLSMITH) {
            if (level >= 3 && blocks.contains(Blocks.SMITHING_TABLE))
                validSpecialOffers.add(createSpecial(new ItemStack(Items.EMERALD, 6), new ItemStack(Items.IRON_PICKAXE, 1), createEnchantedItem(Items.IRON_PICKAXE, Enchantments.EFFICIENCY, 1)));

            if (level >= 4 && blocks.contains(Blocks.GRINDSTONE))
                validSpecialOffers.add(createSpecial(new ItemStack(Items.EMERALD, 5), ItemStack.EMPTY, createEnchantedItem(Items.IRON_SHOVEL, Enchantments.EFFICIENCY, 2)));

            if (level >= 5 && blocks.contains(Blocks.ANVIL))
                validSpecialOffers.add(createSpecial(new ItemStack(Items.EMERALD, 22), new ItemStack(Items.DIAMOND, 1), createEnchantedItem(Items.DIAMOND_PICKAXE, Enchantments.UNBREAKING, 2)));
        }

        // Fletcher
        else if (prof == VillagerProfession.FLETCHER) {
            if (level >= 3 && blocks.contains(Blocks.FLETCHING_TABLE))
                validSpecialOffers.add(createSpecial(new ItemStack(Items.EMERALD, 1), ItemStack.EMPTY, new ItemStack(Items.ARROW, 16)));

            if (level >= 4 && blocks.contains(Blocks.TARGET))
                validSpecialOffers.add(createSpecial(new ItemStack(Items.EMERALD, 6), ItemStack.EMPTY, createEnchantedItem(Items.BOW, Enchantments.POWER, 1)));

            if (level >= 5 && blocks.contains(Blocks.HAY_BLOCK))
                validSpecialOffers.add(createSpecial(new ItemStack(Items.EMERALD, 9), ItemStack.EMPTY, createEnchantedItem(Items.CROSSBOW, Enchantments.QUICK_CHARGE, 1)));
        }

        // Farmer
        else if (prof == VillagerProfession.FARMER) {
            if (level >= 3 && blocks.contains(Blocks.COMPOSTER))
                validSpecialOffers.add(createSpecial(new ItemStack(Items.EMERALD, 1), ItemStack.EMPTY, new ItemStack(Items.BREAD, 6)));

            if (level >= 4 && blocks.contains(Blocks.HAY_BLOCK))
                validSpecialOffers.add(createSpecial(new ItemStack(Items.EMERALD, 2), ItemStack.EMPTY, new ItemStack(Items.PUMPKIN_PIE, 4)));

            if (level >= 5 && blocks.contains(Blocks.BEE_NEST))
                validSpecialOffers.add(createSpecial(new ItemStack(Items.EMERALD, 3), ItemStack.EMPTY, new ItemStack(Items.HONEY_BOTTLE, 1)));
        }

        // Librarian
        else if (prof == VillagerProfession.LIBRARIAN) {
            if (level >= 3 && blocks.contains(Blocks.LECTERN))
                validSpecialOffers.add(createSpecial(new ItemStack(Items.EMERALD, 7), new ItemStack(Items.BOOK, 1), createEnchantedBook(Enchantments.UNBREAKING, 1)));

            if (level >= 4 && blocks.contains(Blocks.ENCHANTING_TABLE))
                validSpecialOffers.add(createSpecial(new ItemStack(Items.EMERALD, 12), new ItemStack(Items.BOOK, 1), createEnchantedBook(Enchantments.EFFICIENCY, 2)));

            if (level >= 5 && blocks.contains(Blocks.BOOKSHELF))
                validSpecialOffers.add(createSpecial(new ItemStack(Items.EMERALD, 24), new ItemStack(Items.BOOK, 1), createEnchantedBook(Enchantments.MENDING, 1)));
        }

        // Cleric
        else if (prof == VillagerProfession.CLERIC) {
            if (level >= 3 && blocks.contains(Blocks.BREWING_STAND))
                validSpecialOffers.add(createSpecial(new ItemStack(Items.EMERALD, 1), ItemStack.EMPTY, new ItemStack(Items.REDSTONE, 2)));

            if (level >= 4 && blocks.contains(Blocks.AMETHYST_BLOCK))
                validSpecialOffers.add(createSpecial(new ItemStack(Items.EMERALD, 2), ItemStack.EMPTY, new ItemStack(Items.GLOWSTONE, 3)));

            if (level >= 5 && blocks.contains(Blocks.NETHER_WART_BLOCK))
                validSpecialOffers.add(createSpecial(new ItemStack(Items.EMERALD, 4), ItemStack.EMPTY, new ItemStack(Items.ENDER_PEARL, 1)));
        }

        // Butcher
        else if (prof == VillagerProfession.BUTCHER) {
            if (level >= 3 && blocks.contains(Blocks.CAMPFIRE))
                validSpecialOffers.add(createSpecial(new ItemStack(Items.EMERALD, 2), ItemStack.EMPTY, new ItemStack(Items.COOKED_BEEF, 6)));

            if (level >= 4 && blocks.contains(Blocks.SMOKER))
                validSpecialOffers.add(createSpecial(new ItemStack(Items.EMERALD, 2), ItemStack.EMPTY, new ItemStack(Items.COOKED_PORKCHOP, 4)));

            if (level >= 5 && (blocks.contains(Blocks.LAVA) || blocks.contains(Blocks.LAVA_CAULDRON)))
                validSpecialOffers.add(createSpecial(new ItemStack(Items.EMERALD, 1), ItemStack.EMPTY, new ItemStack(Items.COOKED_MUTTON, 1)));
        }

        // Mason
        else if (prof == VillagerProfession.MASON) {
            if (level >= 3 && blocks.contains(Blocks.STONECUTTER))
                validSpecialOffers.add(createSpecial(new ItemStack(Items.EMERALD, 1), ItemStack.EMPTY, new ItemStack(Items.STONE_BRICKS, 4)));

            if (level >= 4 && blocks.contains(Blocks.SMOOTH_STONE))
                validSpecialOffers.add(createSpecial(new ItemStack(Items.EMERALD, 2), ItemStack.EMPTY, new ItemStack(Items.QUARTZ_BLOCK, 2)));

            if (level >= 5 && blocks.contains(Blocks.TERRACOTTA))
                validSpecialOffers.add(createSpecial(new ItemStack(Items.EMERALD, 3), ItemStack.EMPTY, new ItemStack(Items.MAGENTA_GLAZED_TERRACOTTA, 4)));
        }

        // Leatherworker
        else if (prof == VillagerProfession.LEATHERWORKER) {
            if (level >= 3 && blocks.contains(Blocks.WATER_CAULDRON))
                validSpecialOffers.add(createSpecial(new ItemStack(Items.EMERALD, 3), ItemStack.EMPTY, new ItemStack(Items.LEATHER_BOOTS, 1)));

            if (level >= 4 && blocks.contains(Blocks.BROWN_WOOL))
                validSpecialOffers.add(createSpecial(new ItemStack(Items.EMERALD, 5), ItemStack.EMPTY, new ItemStack(Items.LEATHER_CHESTPLATE, 1)));

            if (level >= 5 && blocks.contains(Blocks.LAVA_CAULDRON))
                validSpecialOffers.add(createSpecial(new ItemStack(Items.EMERALD, 8), ItemStack.EMPTY, new ItemStack(Items.SADDLE, 1)));
        }

        // Fisherman
        else if (prof == VillagerProfession.FISHERMAN) {
            if (level >= 3 && blocks.contains(Blocks.BARREL))
                validSpecialOffers.add(createSpecial(new ItemStack(Items.EMERALD, 1), ItemStack.EMPTY, new ItemStack(Items.COOKED_COD, 1)));

            if (level >= 4 && hasBoat)
                validSpecialOffers.add(createSpecial(new ItemStack(Items.EMERALD, 5), ItemStack.EMPTY, createEnchantedItem(Items.FISHING_ROD, Enchantments.UNBREAKING, 1)));

            if (level >= 5 && blocks.contains(Blocks.CAMPFIRE))
                validSpecialOffers.add(createSpecial(new ItemStack(Items.EMERALD, 1), ItemStack.EMPTY, new ItemStack(Items.COOKED_SALMON, 1)));
        }

        // Shepherd
        else if (prof == VillagerProfession.SHEPHERD) {
            if (level >= 3 && blocks.contains(Blocks.WHITE_WOOL))
                validSpecialOffers.add(createSpecial(new ItemStack(Items.EMERALD, 1), ItemStack.EMPTY, new ItemStack(Items.WHITE_CARPET, 6)));

            if (level >= 4 && blocks.contains(Blocks.LOOM))
                validSpecialOffers.add(createSpecial(new ItemStack(Items.EMERALD, 3), ItemStack.EMPTY, new ItemStack(Items.WHITE_BANNER, 1)));

            if (level >= 5 && blocks.contains(Blocks.WHITE_WOOL))
                validSpecialOffers.add(createSpecial(new ItemStack(Items.EMERALD, 6), ItemStack.EMPTY, new ItemStack(Items.WHITE_BED, 1)));
        }

        // --- GESTIÓN DE OFERTAS ACTUALIZADA ---

        // 1. ELIMINAR tradeos especiales antiguos que YA NO están en la lista de válidos
        // (Ejemplo: Quitar la mesa de trabajo o bajar de nivel teóricamente)
        villager.getOffers().removeIf(offer ->
                villarium$isSpecialTrade(offer) && !validSpecialOffers.contains(offer)
        );

        // 2. AÑADIR los tradeos válidos si no existen ya
        for (MerchantOffer specialOffer : validSpecialOffers) {
            boolean exists = false;
            for (MerchantOffer o : villager.getOffers()) {
                if (o.equals(specialOffer)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                villager.getOffers().add(specialOffer);
            }
        }
    }

    // --- MÉTODOS AUXILIARES ---

    @Unique
    private MerchantOffer createSpecial(ItemStack cost1, ItemStack cost2, ItemStack result) {
        // Copiamos la data actual (o creamos vacía), modificamos y seteamos de vuelta
        CompoundTag tag = result.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putBoolean("VillariumSpecial", true);
        result.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        if (cost2.isEmpty()) {
            return new MerchantOffer(new ItemCost(cost1.getItem(), cost1.getCount()), result, 100, 10, 0.05f);
        } else {
            return new MerchantOffer(new ItemCost(cost1.getItem(), cost1.getCount()), Optional.of(new ItemCost(cost2.getItem(), cost2.getCount())), result, 100, 10, 0.05f);
        }
    }

    @Unique
    private boolean villarium$isSpecialTrade(MerchantOffer offer) {
        ItemStack result = offer.getResult();
        CustomData data = result.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return data.copyTag().getBoolean("VillariumSpecial");
    }

    @Unique
    private ItemStack createEnchantedItem(Item item, net.minecraft.resources.ResourceKey<Enchantment> enchant, int level) {
        ItemStack stack = new ItemStack(item);
        var registry = this.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        stack.enchant(registry.getHolderOrThrow(enchant), level);
        return stack;
    }

    @Unique
    private ItemStack createEnchantedBook(net.minecraft.resources.ResourceKey<Enchantment> enchant, int level) {
        var registry = this.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        return net.minecraft.world.item.EnchantedBookItem.createForEnchantment(new net.minecraft.world.item.enchantment.EnchantmentInstance(registry.getHolderOrThrow(enchant), level));
    }

    @Unique
    private int villarium$countFavorites(VillagerProfession prof, Set<Block> blocks, boolean hasArmorStand, boolean hasBoat) {
        int count = 0;
        if (prof == VillagerProfession.WEAPONSMITH) {
            if (blocks.contains(Blocks.GRINDSTONE)) count++;
            if (blocks.contains(Blocks.ANVIL)) count++;
            if (blocks.contains(Blocks.IRON_BLOCK)) count++;
            if (blocks.contains(Blocks.COAL_BLOCK)) count++;
            if (blocks.contains(Blocks.BARREL)) count++;
            if (blocks.contains(Blocks.SMITHING_TABLE)) count++;
        }
        else if (prof == VillagerProfession.ARMORER) {
            if (blocks.contains(Blocks.BLAST_FURNACE)) count++;
            if (blocks.contains(Blocks.FURNACE)) count++;
            if (blocks.contains(Blocks.ANVIL)) count++;
            if (blocks.contains(Blocks.IRON_BLOCK)) count++;
            if (hasArmorStand) count++;
        }
        else if (prof == VillagerProfession.LIBRARIAN) {
            if (blocks.contains(Blocks.BOOKSHELF)) count++;
            if (blocks.contains(Blocks.LECTERN)) count++;
            if (blocks.contains(Blocks.ENCHANTING_TABLE)) count++;
            if (blocks.contains(Blocks.AMETHYST_BLOCK)) count++;
        }
        else if (prof == VillagerProfession.BUTCHER) {
            if (blocks.contains(Blocks.SMOKER)) count++;
            if (blocks.contains(Blocks.CAMPFIRE)) count++;
            if (blocks.contains(Blocks.BARREL)) count++;
            if (blocks.contains(Blocks.CARVED_PUMPKIN)) count++;
            if (blocks.contains(Blocks.FURNACE)) count++;
        }
        else if (prof == VillagerProfession.CLERIC) {
            if (blocks.contains(Blocks.BREWING_STAND)) count++;
            if (blocks.contains(Blocks.GLOWSTONE)) count++;
            if (blocks.contains(Blocks.AMETHYST_BLOCK)) count++;
            if (blocks.contains(Blocks.CAULDRON)) count++;
            if (blocks.contains(Blocks.TINTED_GLASS)) count++;
        }
        else if (prof == VillagerProfession.FLETCHER) {
            if (blocks.contains(Blocks.FLETCHING_TABLE)) count++;
            if (blocks.contains(Blocks.TARGET)) count++;
            if (blocks.contains(Blocks.BARREL)) count++;
        }
        else if (prof == VillagerProfession.FARMER) {
            if (blocks.contains(Blocks.COMPOSTER)) count++;
            if (blocks.contains(Blocks.HAY_BLOCK)) count++;
            if (blocks.contains(Blocks.PUMPKIN)) count++;
            if (blocks.contains(Blocks.MELON)) count++;
            if (blocks.contains(Blocks.BARREL)) count++;
        }
        else if (prof == VillagerProfession.MASON) {
            if (blocks.contains(Blocks.STONECUTTER)) count++;
            if (blocks.contains(Blocks.STONE)) count++;
            if (blocks.contains(Blocks.BRICKS)) count++;
            if (blocks.contains(Blocks.ANDESITE)) count++;
            if (blocks.contains(Blocks.DIORITE)) count++;
            if (blocks.contains(Blocks.GRANITE)) count++;
            if (blocks.contains(Blocks.CLAY)) count++;
        }
        else if (prof == VillagerProfession.LEATHERWORKER) {
            if (blocks.contains(Blocks.CAULDRON)) count++;
            if (blocks.contains(Blocks.WATER_CAULDRON)) count++;
            if (blocks.contains(Blocks.CAMPFIRE)) count++;
            if (blocks.contains(Blocks.BARREL)) count++;
        }
        else if (prof == VillagerProfession.FISHERMAN) {
            if (blocks.contains(Blocks.BARREL)) count++;
            if (blocks.contains(Blocks.CAMPFIRE)) count++;
            if (blocks.contains(Blocks.CHEST)) count++;
            if (hasBoat) count++;
        }
        else if (prof == VillagerProfession.SHEPHERD) {
            if (blocks.contains(Blocks.LOOM)) count++;
            if (blocks.contains(Blocks.HAY_BLOCK)) count++;
            if (blocks.contains(Blocks.BARREL)) count++;
        }
        else if (prof == VillagerProfession.TOOLSMITH) {
            if (blocks.contains(Blocks.SMITHING_TABLE)) count++;
            if (blocks.contains(Blocks.ANVIL)) count++;
            if (blocks.contains(Blocks.IRON_BLOCK)) count++;
            if (blocks.contains(Blocks.COAL_BLOCK)) count++;
            if (blocks.contains(Blocks.FURNACE)) count++;
            if (blocks.contains(Blocks.BARREL)) count++;
        }
        return count;
    }
}