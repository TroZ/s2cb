package s2cb;

/*
Schematic To Command Block for Minecraft 1.13

Copyright 2018 Brian Risinger

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

*/

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.swing.JTextPane;
import javax.swing.SwingUtilities;

import s2cb.S2CB.Block;
import s2cb.S2CB.SchematicData;

import com.evilco.mc.nbt.error.TagNotFoundException;
import com.evilco.mc.nbt.error.UnexpectedTagTypeException;
import com.evilco.mc.nbt.tag.*;
import com.sun.corba.se.spi.legacy.connection.GetEndPointInfoAgainException;



public class SchematicConverter {
	
	
	//warning - this is now somewhat a mix of the old names and the new names
	public static final String[] materials = { 
		"air","stone","grass_block","dirt","cobblestone","planks","sapling","bedrock",
		"flowing_water","water","flowing_lava","lava","sand","gravel","gold_ore","iron_ore",
		"coal_ore","log","leaves","sponge","glass","lapis_ore","lapis_block","dispenser",
		"sandstone","note_block","bed","powered_rail","detector_rail","sticky_piston","cobweb","tallgrass",
		
		"dead_bush","piston","piston_head","wool","moving_piston","dandelion","red_flower","brown_mushroom",
		"red_mushroom","gold_block","iron_block","double_stone_slab","stone_slab","brick_block","tnt","bookshelf",
		"mossy_cobblestone","obsidian","torch","fire","spawner","oak_stairs","chest","redstone_wire",
		"diamond_ore","diamond_block","crafting_table","wheat","farmland","furnace","lit_furnace","standing_sign",
		
		"wooden_door","ladder","rail","stone_stairs","wall_sign","lever","stone_pressure_plate","iron_door",
		"wooden_pressure_plate","redstone_ore","lit_redstone_ore","unlit_redstone_torch","redstone_torch","stone_button","snow_layer","ice",
		"snow_block","cactus","clay","reeds","jukebox","fence","pumpkin","netherrack",
		"soul_sand","glowstone","nether_portal","lit_pumpkin","cake","unpowered_repeater","powered_repeater","stained_glass", 
		
		"trapdoor","monster_egg","stonebrick","brown_mushroom_block","red_mushroom_block","iron_bars","glass_pane","melon",
		"pumpkin_stem","melon_stem","vine","fence_gate","brick_stairs","stone_brick_stairs","mycelium","lily_pad", 
		"nether_bricks","nether_brick_fence","nether_brick_stairs","nether_wart","enchanting_table","brewing_stand","cauldron","end_portal",
		"end_portal_frame","end_stone","dragon_egg","redstone_lamp","lit_redstone_lamp","double_wooden_slab","wooden_slab","cocoa", 
		
		"sandstone_stairs","emerald_ore","ender_chest","tripwire_hook","tripwire","emerald_block","spruce_stairs","birch_stairs",
		"jungle_stairs","command_block","beacon","cobblestone_wall","flower_pot","carrots","potatoes","wooden_button", 
		"skull","anvil","trapped_chest","light_weighted_pressure_plate","heavy_weighted_pressure_plate","unpowered_comparator","powered_comparator","daylight_detector",
		"redstone_block","nether_quartz_ore","hopper","quartz_block","quartz_stairs","activator_rail","dropper","stained_hardened_clay", 
		
		"stained_glass_pane","leaves2","log2","acacia_stairs","dark_oak_stairs","slime","barrier","iron_trapdoor",
		"prismarine","sea_lantern","hay_block","carpet","hardened_clay","coal_block","packed_ice","double_plant",
		"standing_banner","wall_banner","daylight_detector_inverted","red_sandstone","red_sandstone_stairs","double_stone_slab2","stone_slab2","spruce_fence_gate",
		"birch_fence_gate","jungle_fence_gate","dark_oak_fence_gate","acacia_fence_gate","spruce_fence","birch_fence","jungle_fence","dark_oak_fence", 
		
		"acacia_fence","spruce_door","birch_door","jungle_door","acacia_door","dark_oak_door","end_rod","chorus_plant",
		"chorus_flower","purpur_block","purpur_pillar","purpur_stairs","purpur_double_slab","purpur_slab","end_stone_bricks","beetroots",
		"grass_path","end_gateway","repeating_command_block","chain_command_block","frosted_ice","magma_block","nether_wart_block","red_nether_bricks",
		"bone_block","structure_void","observer","white_shulker_box","orange_shulker_box","magenta_shulker_box","light_blue_shulker_box","yellow_shulker_box",
		
		"lime_shulker_box","pink_shulker_box","gray_shulker_box","silver_shulker_box","cyan_shulker_box","purple_shulker_box","blue_shulker_box","brown_shulker_box",
		"green_shukler_box","red_shulker_box","black_shulker_box","white_glazed_terracotta","orange_glazed_terracotta","magenta_glazed_terracotta","light_blue_glazed_terracotta","yellow_glazed_terracotta",
		"lime_glazed_terracotta","pink_glazed_terracotta","gray_glazed_terracotta","silver_glazed_terracotta","cyan_glazed_terracotta","purple_glazed_terracotta","blue_glazed_terracotta","brown_glazed_terracotta",
		"green_glazed_terracotta","red_glazed_terracotta","black_glazed_terracotta","concrete","concrete_powder","","","structure_block"
	};
	
	private static final int tr = 0x10000;
	/**
	 * An array detailing the issues materials may have and how to deal with them
	 * 0 = normal block
	 * -1 = ignore - air or equivalent (flowing liquid)
	 * 1 = item with inventory or other block entity data
	 * 2 = side attached item - torch, ladder, liquid - place as last blocks from this level
	 * 4 = block that is actually multiple blocks that need to be placed together - doors, beds, tall grass
	 * 8 = rail - needs special placement rules and be place at end.
	 * 16 = block that needs to be placed on another block  - redstone wire, repeaters, comparators, pressure plates, rails - similar to type 2 above, but will be placed in first pass if block it is on already exists (this really won't be an issue except for when the block is on a hopper which are placed late due to redstone locking them may not be in place yet...)
	 * 32 = directional block (starting in 1.12) - stairs (confirmed), bed(confirmed), door(confirmed), chests?, dispenser? / dropper?, torch?, hopper?, log?, trapdoor?, purpur pillar?, rails?  Previously leaving off the dataVal when 0 resulted in a dataVal of 0 being used.  However, this no longer seems to be the case for certain blocks. This is confirmed for stairs, beds and doors, but may affect other blocks.  Mismarking a block that doesn't actually have an issue only uses an extra 3 characters, so not a big issue.
	 * 65536 / 0x10000 = transparent - used in removing hidden blocks (blocks next to transparent blocks are kept
	 */
	public static final int[] materialIssue = {
		    -1,0,0,0,0,0,0+tr,0,  						-1,2+tr,-1,2+tr,0,0,0,0,  				0,32,0+tr,0,0+tr,0,0,33,  				0,1,36+tr,56+tr,56+tr,32+tr,0+tr,0+tr,
			0+tr,32+tr,32+tr,0,-1,0+tr,0+tr,0+tr,  		0+tr,0,0,0,0+tr,0,0,0,  				0,0,2+tr,32+tr,1+tr,32+tr,33+tr,16+tr,  0,0,0,0+tr,0+tr,33,33,33+tr,
			36+tr,2+tr,56+tr,32+tr,35+tr,2+tr,16+tr,36+tr, 	16+tr,0,0,34+tr,34+tr,34+tr,0+tr,0+tr,	0,0+tr,0,2+tr,1,0+tr,0,0,  			0,0,0+tr,0,0+tr,48+tr,48+tr,0+tr,
			32+tr,0+tr,0,0,0,0+tr,0+tr,0,  				0+tr,0+tr,2+tr,0+tr,32+tr,32+tr,0,16+tr,	0,0+tr,32+tr,0+tr,0+tr,1+tr,0+tr,0+tr,  0+tr,0,0+tr,0,0,0,0+tr,0+tr,
			32+tr,0,0+tr,2+tr,0+tr,0,32+tr,32+tr,  		32+tr,33,1+tr,0+tr,1+tr,0+tr,0+tr,2+tr,	33+tr,32+tr,33+tr,16+tr,16+tr,49+tr,49+tr,1+tr,  	0,0,35+tr,0,32+tr,56+tr,33,0,
			0+tr,0+tr,32,32+tr,32+tr,0+tr,0+tr,32+tr,  	0,0,32,0+tr,0,0,0,4+tr,  				33+tr,33+tr,1+tr,0,0+tr,0,0+tr,0+tr,  	0+tr,0+tr,0+tr,0+tr,0+tr,0+tr,0+tr,0+tr,
			0+tr,36+tr,36+tr,36+tr,36+tr,36+tr,32+tr,0+tr,  0+tr,0,32,32+tr,0,0+tr,0,0+tr,  	0+tr,0+tr,33,33,0+tr,0,0,0,				0,0+tr,32,33+tr,33+tr,33+tr,33+tr,33+tr,
			33+tr,33+tr,33+tr,33+tr,33+tr,33+tr,33+tr,33+tr, 	33+tr,33+tr,33+tr,32,32,32,32,32,	32,32,32,32,32,32,32,32,			32,32,32,0,0,0+tr,0+tr,0
	};
	
	
	//item name for (id - 256).  It's missing some newer items, but those version should be storing names and so won't need this list.
	//name may need to be fixed, as this are likely the old names and not the 1.13 names
	public static final String[] items = { 
		"iron_shovel","iron_pickaxe","iron_axe","flint_and_steel","apple","bow","arrow","coal",
		"diamond","iron_ingot","gold_ingot","iron_sword","wodden_sword","wodden_shovel","wooden_pickaxe","wooden_axe",
		"stone_sword","stone_shovel","stone_pickaxe","stone_axe","diamond_sword","diamond_shovel","diamond_pickaxe","diamond_axe",
		"stick","bowl","mushroom_stew","golden_sword","golden_shovel","golden_pickaxe","golden_axe","string",
		
		"feather","gunpowder","wooden_hoe","stone_hoe","iron_hoe","iron_hoe","golden_hoe","wheat_seeds",
		"wheat","bread","leather_helmet","leather_chestplate","leather_leggings","leather_boots","chainmail_helmet","chainmail_chestplate",
		"chainmail_leggings","chianmail_boots","iron_helmet","iron_chestplate","iron_leggings","iron_boots","diamond_helmet","diamond_chestplate",
		"diamond_leggings","diamond_boots","golden_helmet","golden_chestplate","golden_leggings","golden_boots","flint","porkchop",
		
		"cooked_porkchop","painting","golden_apple","sign","wooden_door","bucket","water_bucket","lava_bucket",
		"minecart","saddle","iron_door","redstone","snowball","boat","leather","milk_bucket",
		"brick","clay_ball","reeds","paper","book","slime_ball","chest_minecart","furnace_minecart",
		"egg","compass","fishing_rod","clock","glowstone_dust","fish","cooked_fish","dye",
		
		"bone","sugar","cake","bed","repeater","cookie","filled_map","shears",
		"melon","pumpkin_seeds","melon_seeds","beef","cooked_beef","chicken","cooked_chicken","rotten_flesh",
		"ender_pearl","blaze_rod","ghast_tear","gold_nugget","nether_wart","potion","glass_bottle","spider_eye",
		"fermented_spider_eye","blaze_powder","magma_cream","brewing_stand","cauldron","ender_eye","speckled_melon","spawn_egg",

		"experience_bottle","fire_charge","writable_book","written_book","emerald","item_frame","flower_pot","carrot",
		"potato","baked_potato","poisonous_potato","map","golden_carrot","skull","carrot_on_a_stick","nether_star",
		"pumpkin_pie","fireworks","firework_charge","enchanted_books","comparator","netherbrick","quartz","tnt_minecart",
		"hopper_minecart","prismarine_shard","prismarine_crystals","rabbit","cooked_rabbit","rabbit_stew","rabbit_foot","rabbit_hide",

		"armor_stand","iron_horse_armor","golden_horse_armor","diamond_horse_armor","lead","name_tag","command_block_minecart","mutton",
		"cooked_mutton","banner","end_crystal","spruce_door","birch_door","jungle_door","acacia_door","dark_oak_door",
		"chorus_fruit","popped_chorus_fruit","beetroot","beetroot_seeds","beetroot_soup","dragon_breath","splash_potion","spectral_arrow",
		"tipped_arrow","lingering_potion","shield","elytra","spruce_boat","birch_boat","jungle_boat","acacia_boat",

		"dark_oak_boat",

	};
	
	
	static class Item {
		String name;
		byte damage;
		
		public Item(String name, int damage) {
			this.name = name;
			this.damage = (byte)damage;
		}
		
		public boolean equals(Object o) {
			if(o instanceof Item) {
				Item i = (Item)o;
				if(name.equals(i.name) && i.damage == damage){
					return true;
				}
			}
			return false;
		}
		
		public int hashCode() {
			return name.hashCode() + damage;
		}
	}
	
	public static final HashMap<Item,String> itemMapping = new HashMap<Item,String>();
	
	static {
		itemMapping.put(new Item("stone",0),"stone");
		itemMapping.put(new Item("stone",1),"granite");
		itemMapping.put(new Item("stone",2),"polished_granite");
		itemMapping.put(new Item("stone",3),"diorite");
		itemMapping.put(new Item("stone",4),"polished_diorite");
		itemMapping.put(new Item("stone",5),"andesite");
		itemMapping.put(new Item("stone",6),"polished_andesite");
		itemMapping.put(new Item("grass",0),"grass_block");
		itemMapping.put(new Item("dirt",0),"dirt");
		itemMapping.put(new Item("dirt",1),"coarse_dirt");
		itemMapping.put(new Item("dirt",2),"podzol");
		itemMapping.put(new Item("planks",0),"oak_planks");
		itemMapping.put(new Item("planks",1),"spruce_planks");
		itemMapping.put(new Item("planks",2),"birch_planks");
		itemMapping.put(new Item("planks",3),"jungle_planks");
		itemMapping.put(new Item("planks",4),"acacia_planks");
		itemMapping.put(new Item("planks",5),"dark_oak_planks");
		itemMapping.put(new Item("sapling",0),"oak_sapling");
		itemMapping.put(new Item("sapling",1),"spruce_sapling");
		itemMapping.put(new Item("sapling",2),"birch_sapling");
		itemMapping.put(new Item("sapling",3),"jungle_sapling");
		itemMapping.put(new Item("sapling",4),"acacia_sapling");
		itemMapping.put(new Item("sapling",5),"dark_oak_sapling");
		itemMapping.put(new Item("sand",0),"sand");
		itemMapping.put(new Item("sand",1),"red_sand");
		itemMapping.put(new Item("log",0),"oak_log");
		itemMapping.put(new Item("log",1),"spruce_log");
		itemMapping.put(new Item("log",2),"birch_log");
		itemMapping.put(new Item("log",3),"jungle_log");
		itemMapping.put(new Item("log",12),"oak_wood");
		itemMapping.put(new Item("log",13),"spruce_wood");
		itemMapping.put(new Item("log",14),"birch_wood");
		itemMapping.put(new Item("log",15),"jungle_wood");
		itemMapping.put(new Item("log2",0),"acacia_log");
		itemMapping.put(new Item("log2",1),"dark_oak_log");
		itemMapping.put(new Item("log2",12),"acacia_wood");
		itemMapping.put(new Item("log2",13),"dark_oak_wood");
		itemMapping.put(new Item("leaves",0),"oak_leaves");
		itemMapping.put(new Item("leaves",1),"spruce_leaves");
		itemMapping.put(new Item("leaves",2),"birch_leaves");
		itemMapping.put(new Item("leaves",3),"jungle_leaves");
		itemMapping.put(new Item("leaves2",0),"acacia_leaves");
		itemMapping.put(new Item("leaves2",1),"dark_oak_leaves");
		itemMapping.put(new Item("sponge",0),"sponge");
		itemMapping.put(new Item("sponge",1),"wet_sponge");
		itemMapping.put(new Item("noteblock",0),"note_block");
		itemMapping.put(new Item("golden_rail",0),"powered_rail");
		itemMapping.put(new Item("web",0),"cobweb");
		itemMapping.put(new Item("tallgrass",0),"grass");
		itemMapping.put(new Item("tallgrass",1),"grass");  // ???
		itemMapping.put(new Item("tallgrass",2),"fern");
		itemMapping.put(new Item("deadbush",0),"dead_bush");
		itemMapping.put(new Item("wool",0),"white_wool");
		itemMapping.put(new Item("wool",1),"orange_wool");
		itemMapping.put(new Item("wool",2),"magenta_wool");
		itemMapping.put(new Item("wool",3),"light_blue_wool");
		itemMapping.put(new Item("wool",4),"yellow_wool");
		itemMapping.put(new Item("wool",5),"lime_wool");
		itemMapping.put(new Item("wool",6),"pink_wool");
		itemMapping.put(new Item("wool",7),"gray_wool");
		itemMapping.put(new Item("wool",8),"light_gray_wool");
		itemMapping.put(new Item("wool",9),"cyan_wool");
		itemMapping.put(new Item("wool",10),"purple_wool");
		itemMapping.put(new Item("wool",11),"blue_wool");
		itemMapping.put(new Item("wool",12),"brown_wool");
		itemMapping.put(new Item("wool",13),"green_wool");
		itemMapping.put(new Item("wool",14),"red_wool");
		itemMapping.put(new Item("wool",15),"black_wool");
		itemMapping.put(new Item("yellow_flower",0),"dandelion");
		itemMapping.put(new Item("red_flower",0),"poppy");
		itemMapping.put(new Item("red_flower",1),"blue_orchid");
		itemMapping.put(new Item("red_flower",2),"allium");
		itemMapping.put(new Item("red_flower",3),"azure_bluet");
		itemMapping.put(new Item("red_flower",4),"red_tulip");
		itemMapping.put(new Item("red_flower",5),"orange_tulip");
		itemMapping.put(new Item("red_flower",6),"white_tulip");
		itemMapping.put(new Item("red_flower",7),"pink_tulip");
		itemMapping.put(new Item("red_flower",8),"oxeye_daisy");
		itemMapping.put(new Item("wooden_slab",0),"oak_slab");
		itemMapping.put(new Item("wooden_slab",1),"spruce_slab");
		itemMapping.put(new Item("wooden_slab",2),"birch_slab");
		itemMapping.put(new Item("wooden_slab",3),"jungle_slab");
		itemMapping.put(new Item("wooden_slab",4),"acacia_slab");
		itemMapping.put(new Item("wooden_slab",5),"dark_oak_slab");
		itemMapping.put(new Item("stone_slab",0),"stone_slab");
		itemMapping.put(new Item("stone_slab",1),"sandstone_slab");
		itemMapping.put(new Item("stone_slab",2),"petrified_oak_slab");
		itemMapping.put(new Item("stone_slab",3),"cobblestone_slab");
		itemMapping.put(new Item("stone_slab",4),"brick_slab");
		itemMapping.put(new Item("stone_slab",5),"stone_brick_slab");
		itemMapping.put(new Item("stone_slab",6),"nether_brick_slab");
		itemMapping.put(new Item("stone_slab",7),"quartz_slab");
		itemMapping.put(new Item("stone_slab2",0),"red_sandstone_slab");
		itemMapping.put(new Item("stone_slab2",1),"purpur_slab");
		itemMapping.put(new Item("stone_slab2",2),"prismarine_slab");
		itemMapping.put(new Item("stone_slab2",3),"prismarine_brick_slab");
		itemMapping.put(new Item("stone_slab2",4),"dark_prismarine_slab");
		itemMapping.put(new Item("brick_block",0),"bricks");
		itemMapping.put(new Item("mob_spawner",0),"spawner");
		itemMapping.put(new Item("stone_stairs",0),"cobblestone_stairs");
		itemMapping.put(new Item("wooden_pressure_plate",0),"oak_pressure_plate");
		itemMapping.put(new Item("snow_layer",0),"snow");
		itemMapping.put(new Item("snow",0),"snow_block");
		itemMapping.put(new Item("fence",0),"oak_fence");
		itemMapping.put(new Item("pumpkin",0),"carved_pumpkin");
		itemMapping.put(new Item("lit_pumpkin",0),"jack_o_lantern");
		itemMapping.put(new Item("trapdoor",0),"oak_trapdoor");
		itemMapping.put(new Item("monster_egg",0),"infested_stone");
		itemMapping.put(new Item("monster_egg",1),"infested_cobblestone");
		itemMapping.put(new Item("monster_egg",2),"infested_stone_bricks");
		itemMapping.put(new Item("monster_egg",3),"infested_mossy_stone_bricks");
		itemMapping.put(new Item("monster_egg",4),"infested_cracked_stone_bricks");
		itemMapping.put(new Item("monster_egg",5),"infested_chiseled_stone_bricks");
		itemMapping.put(new Item("stonebrick",0),"stone_bricks");
		itemMapping.put(new Item("stonebrick",1),"mossy_stone_bricks");
		itemMapping.put(new Item("stonebrick",2),"cracked_stone_bricks");
		itemMapping.put(new Item("stonebrick",3),"chiseled_stone_bricks");
		itemMapping.put(new Item("melon_block",0),"melon");
		itemMapping.put(new Item("fence_gate",0),"oak_fence_gate");
		itemMapping.put(new Item("waterlily",0),"lily_pad");
		itemMapping.put(new Item("nether_brick",0),"nether_bricks");
		itemMapping.put(new Item("end_bricks",0),"end_stone_bricks");
		itemMapping.put(new Item("cobblestone_wall",0),"cobblestone_wall");
		itemMapping.put(new Item("cobblestone_wall",1),"mossy_cobblestone_wall");
		itemMapping.put(new Item("wooden_button",0),"oak_button");
		itemMapping.put(new Item("anvil",0),"anvil");
		itemMapping.put(new Item("anvil",1),"chipped_anvil");
		itemMapping.put(new Item("anvil",2),"damaged_anvil");
		itemMapping.put(new Item("quartz_ore",0),"nether_quartz_ore");
		itemMapping.put(new Item("quartz_block",0),"quartz_block");
		itemMapping.put(new Item("quartz_block",1),"chiseled_quartz_block");
		itemMapping.put(new Item("quartz_block",2),"quartz_pillar");
		itemMapping.put(new Item("stained_hardened_clay",0),"white_terracotta");
		itemMapping.put(new Item("stained_hardened_clay",1),"orange_terracotta");
		itemMapping.put(new Item("stained_hardened_clay",2),"magenta_terracotta");
		itemMapping.put(new Item("stained_hardened_clay",3),"light_blue_terracotta");
		itemMapping.put(new Item("stained_hardened_clay",4),"yellow_terracotta");
		itemMapping.put(new Item("stained_hardened_clay",5),"lime_terracotta");
		itemMapping.put(new Item("stained_hardened_clay",6),"pink_terracotta");
		itemMapping.put(new Item("stained_hardened_clay",7),"gray_terracotta");
		itemMapping.put(new Item("stained_hardened_clay",8),"light_gray_terracotta");
		itemMapping.put(new Item("stained_hardened_clay",9),"cyan_terracotta");
		itemMapping.put(new Item("stained_hardened_clay",10),"purple_terracotta");
		itemMapping.put(new Item("stained_hardened_clay",11),"blue_terracotta");
		itemMapping.put(new Item("stained_hardened_clay",12),"brown_terracotta");
		itemMapping.put(new Item("stained_hardened_clay",13),"green_terracotta");
		itemMapping.put(new Item("stained_hardened_clay",14),"red_terracotta");
		itemMapping.put(new Item("stained_hardened_clay",15),"black_terracotta");
		itemMapping.put(new Item("carpet",0),"white_carpet");
		itemMapping.put(new Item("carpet",1),"orange_carpet");
		itemMapping.put(new Item("carpet",2),"magenta_carpet");
		itemMapping.put(new Item("carpet",3),"light_blue_carpet");
		itemMapping.put(new Item("carpet",4),"yellow_carpet");
		itemMapping.put(new Item("carpet",5),"lime_carpet");
		itemMapping.put(new Item("carpet",6),"pink_carpet");
		itemMapping.put(new Item("carpet",7),"gray_carpet");
		itemMapping.put(new Item("carpet",8),"light_gray_carpet");
		itemMapping.put(new Item("carpet",9),"cyan_carpet");
		itemMapping.put(new Item("carpet",10),"purple_carpet");
		itemMapping.put(new Item("carpet",11),"blue_carpet");
		itemMapping.put(new Item("carpet",12),"brown_carpet");
		itemMapping.put(new Item("carpet",13),"green_carpet");
		itemMapping.put(new Item("carpet",14),"red_carpet");
		itemMapping.put(new Item("carpet",15),"black_carpet");
		itemMapping.put(new Item("hardened_clay",0),"terracotta");
		itemMapping.put(new Item("slime",0),"slime_block");
		itemMapping.put(new Item("double_plant",0),"sunflower");
		itemMapping.put(new Item("double_plant",1),"lilac");
		itemMapping.put(new Item("double_plant",2),"tall_grass");
		itemMapping.put(new Item("double_plant",3),"large_fern");
		itemMapping.put(new Item("double_plant",4),"rose_bush");
		itemMapping.put(new Item("double_plant",5),"peony");
		itemMapping.put(new Item("stained_glass",0),"white_stained_glass");
		itemMapping.put(new Item("stained_glass",1),"orange_stained_glass");
		itemMapping.put(new Item("stained_glass",2),"magenta_stained_glass");
		itemMapping.put(new Item("stained_glass",3),"light_blue_stained_glass");
		itemMapping.put(new Item("stained_glass",4),"yellow_stained_glass");
		itemMapping.put(new Item("stained_glass",5),"lime_stained_glass");
		itemMapping.put(new Item("stained_glass",6),"pink_stained_glass");
		itemMapping.put(new Item("stained_glass",7),"gray_stained_glass");
		itemMapping.put(new Item("stained_glass",8),"light_gray_stained_glass");
		itemMapping.put(new Item("stained_glass",9),"cyan_stained_glass");
		itemMapping.put(new Item("stained_glass",10),"purple_stained_glass");
		itemMapping.put(new Item("stained_glass",11),"blue_stained_glass");
		itemMapping.put(new Item("stained_glass",12),"brown_stained_glass");
		itemMapping.put(new Item("stained_glass",13),"green_stained_glass");
		itemMapping.put(new Item("stained_glass",14),"red_stained_glass");
		itemMapping.put(new Item("stained_glass",15),"black_stained_glass");
		itemMapping.put(new Item("stained_glass_pane",0),"white_stained_glass_pane");
		itemMapping.put(new Item("stained_glass_pane",1),"orange_stained_glass_pane");
		itemMapping.put(new Item("stained_glass_pane",2),"magenta_stained_glass_pane");
		itemMapping.put(new Item("stained_glass_pane",3),"light_blue_stained_glass_pane");
		itemMapping.put(new Item("stained_glass_pane",4),"yellow_stained_glass_pane");
		itemMapping.put(new Item("stained_glass_pane",5),"lime_stained_glass_pane");
		itemMapping.put(new Item("stained_glass_pane",6),"pink_stained_glass_pane");
		itemMapping.put(new Item("stained_glass_pane",7),"gray_stained_glass_pane");
		itemMapping.put(new Item("stained_glass_pane",8),"light_gray_stained_glass_pane");
		itemMapping.put(new Item("stained_glass_pane",9),"cyan_stained_glass_pane");
		itemMapping.put(new Item("stained_glass_pane",10),"purple_stained_glass_pane");
		itemMapping.put(new Item("stained_glass_pane",11),"blue_stained_glass_pane");
		itemMapping.put(new Item("stained_glass_pane",12),"brown_stained_glass_pane");
		itemMapping.put(new Item("stained_glass_pane",13),"green_stained_glass_pane");
		itemMapping.put(new Item("stained_glass_pane",14),"red_stained_glass_pane");
		itemMapping.put(new Item("stained_glass_pane",15),"black_stained_glass_pane");
		itemMapping.put(new Item("prismarine",0),"prismarine");
		itemMapping.put(new Item("prismarine",1),"prismarine_bricks");
		itemMapping.put(new Item("prismarine",2),"dark_prismarine");
		itemMapping.put(new Item("red_sandstone",0),"red_sandstone");
		itemMapping.put(new Item("red_sandstone",1),"chiseled_red_sandstone");
		itemMapping.put(new Item("red_sandstone",2),"cut_red_sandstone");
		itemMapping.put(new Item("magma",0),"magma_block");
		itemMapping.put(new Item("red_nether_brick",0),"red_nether_bricks");
		itemMapping.put(new Item("silver_shulker_box",0),"light_gray_shulker_box");
		itemMapping.put(new Item("silver_glazed_terracotta",0),"light_gray_glazed_terracotta");
		itemMapping.put(new Item("concrete",0),"white_concrete");
		itemMapping.put(new Item("concrete",1),"orange_concrete");
		itemMapping.put(new Item("concrete",2),"magenta_concrete");
		itemMapping.put(new Item("concrete",3),"light_blue_concrete");
		itemMapping.put(new Item("concrete",4),"yellow_concrete");
		itemMapping.put(new Item("concrete",5),"lime_concrete");
		itemMapping.put(new Item("concrete",6),"pink_concrete");
		itemMapping.put(new Item("concrete",7),"gray_concrete");
		itemMapping.put(new Item("concrete",8),"light_gray_concrete");
		itemMapping.put(new Item("concrete",9),"cyan_concrete");
		itemMapping.put(new Item("concrete",10),"purple_concrete");
		itemMapping.put(new Item("concrete",11),"blue_concrete");
		itemMapping.put(new Item("concrete",12),"brown_concrete");
		itemMapping.put(new Item("concrete",13),"green_concrete");
		itemMapping.put(new Item("concrete",14),"red_concrete");
		itemMapping.put(new Item("concrete",15),"black_concrete");
		itemMapping.put(new Item("concrete_powder",0),"white_concrete_powder");
		itemMapping.put(new Item("concrete_powder",1),"orange_concrete_powder");
		itemMapping.put(new Item("concrete_powder",2),"magenta_concrete_powder");
		itemMapping.put(new Item("concrete_powder",3),"light_blue_concrete_powder");
		itemMapping.put(new Item("concrete_powder",4),"yellow_concrete_powder");
		itemMapping.put(new Item("concrete_powder",5),"lime_concrete_powder");
		itemMapping.put(new Item("concrete_powder",6),"pink_concrete_powder");
		itemMapping.put(new Item("concrete_powder",7),"gray_concrete_powder");
		itemMapping.put(new Item("concrete_powder",8),"light_gray_concrete_powder");
		itemMapping.put(new Item("concrete_powder",9),"cyan_concrete_powder");
		itemMapping.put(new Item("concrete_powder",10),"purple_concrete_powder");
		itemMapping.put(new Item("concrete_powder",11),"blue_concrete_powder");
		itemMapping.put(new Item("concrete_powder",12),"brown_concrete_powder");
		itemMapping.put(new Item("concrete_powder",13),"green_concrete_powder");
		itemMapping.put(new Item("concrete_powder",14),"red_concrete_powder");
		itemMapping.put(new Item("concrete_powder",15),"black_concrete_powder");
		itemMapping.put(new Item("wooden_door",0),"oak_door");
		itemMapping.put(new Item("coal",0),"coal");
		itemMapping.put(new Item("coal",1),"charcoal");
		itemMapping.put(new Item("golden_apple",0),"golden_apple");
		itemMapping.put(new Item("golden_apple",1),"enchanted_golden_apple");
		itemMapping.put(new Item("boat",0),"oak_boat");
		itemMapping.put(new Item("reeds",0),"sugar_cane");
		itemMapping.put(new Item("fish",0),"cod");
		itemMapping.put(new Item("fish",1),"salmon");
		itemMapping.put(new Item("fish",2),"tropical_fish");
		itemMapping.put(new Item("fish",3),"pufferfish");
		itemMapping.put(new Item("cooked_fish",0),"cooked_cod");
		itemMapping.put(new Item("cooked_fish",1),"cooked_salmon");
		itemMapping.put(new Item("dye",0),"bone_meal");//white_dye for 1.14
		itemMapping.put(new Item("dye",1),"orange_dye");
		itemMapping.put(new Item("dye",2),"magenta_dye");
		itemMapping.put(new Item("dye",3),"light_blue_dye");
		itemMapping.put(new Item("dye",4),"dandelion_yellow");//yellow_dye for 1.14
		itemMapping.put(new Item("dye",5),"lime_dye");
		itemMapping.put(new Item("dye",6),"pink_dye");
		itemMapping.put(new Item("dye",7),"gray_dye");
		itemMapping.put(new Item("dye",8),"light_gray_dye");
		itemMapping.put(new Item("dye",9),"cyan_dye");
		itemMapping.put(new Item("dye",10),"purple_dye");
		itemMapping.put(new Item("dye",11),"lapis_lazuli");//blue_dye for 1.14
		itemMapping.put(new Item("dye",12),"cocoa_beans");//brown_dye for 1.14
		itemMapping.put(new Item("dye",13),"cactus_green");//green_dye for 1.14
		itemMapping.put(new Item("dye",14),"rose_red");//red_dye for 1.14
		itemMapping.put(new Item("dye",15),"ink_sac");//black_dye for 1.14
		itemMapping.put(new Item("bed",0),"white_bed");
		itemMapping.put(new Item("bed",1),"orange_bed");
		itemMapping.put(new Item("bed",2),"magenta_bed");
		itemMapping.put(new Item("bed",3),"light_blue_bed");
		itemMapping.put(new Item("bed",4),"yellow_bed");
		itemMapping.put(new Item("bed",5),"lime_bed");
		itemMapping.put(new Item("bed",6),"pink_bed");
		itemMapping.put(new Item("bed",7),"gray_bed");
		itemMapping.put(new Item("bed",8),"light_gray_bed");
		itemMapping.put(new Item("bed",9),"cyan_bed");
		itemMapping.put(new Item("bed",10),"purple_bed");
		itemMapping.put(new Item("bed",11),"blue_bed");
		itemMapping.put(new Item("bed",12),"brown_bed");
		itemMapping.put(new Item("bed",13),"green_bed");
		itemMapping.put(new Item("bed",14),"red_bed");
		itemMapping.put(new Item("bed",15),"black_bed");
		itemMapping.put(new Item("melon",0),"melon_slice");
		itemMapping.put(new Item("speckled_melon",0),"glistering_melon_slice");
		
		//itemMapping.put(new Item("spawn_egg",0),"");
		S2CB.mobNames.forEach((k,v) -> itemMapping.put(new Item("spawn_egg",k), v + "_spawn_egg"));
		
		itemMapping.put(new Item("skull",0),"skeleton_skull");
		itemMapping.put(new Item("skull",1),"wither_skeleton_skull");
		itemMapping.put(new Item("skull",2),"zombie_head");
		itemMapping.put(new Item("skull",3),"player_head");
		itemMapping.put(new Item("skull",4),"creeper_head");
		itemMapping.put(new Item("skull",5),"dragon_head");
		itemMapping.put(new Item("fireworks",0),"firework_rocket");
		itemMapping.put(new Item("firework_charge",0),"firework_star");
		itemMapping.put(new Item("netherbrick",0),"nether_brick");
		itemMapping.put(new Item("banner",15),"white_banner");
		itemMapping.put(new Item("banner",14),"orange_banner");
		itemMapping.put(new Item("banner",13),"magenta_banner");
		itemMapping.put(new Item("banner",12),"light_blue_banner");
		itemMapping.put(new Item("banner",11),"yellow_banner");
		itemMapping.put(new Item("banner",10),"lime_banner");
		itemMapping.put(new Item("banner",9),"pink_banner");
		itemMapping.put(new Item("banner",8),"gray_banner");
		itemMapping.put(new Item("banner",7),"light_gray_banner");
		itemMapping.put(new Item("banner",6),"cyan_banner");
		itemMapping.put(new Item("banner",5),"purple_banner");
		itemMapping.put(new Item("banner",4),"blue_banner");
		itemMapping.put(new Item("banner",3),"brown_banner");
		itemMapping.put(new Item("banner",2),"green_banner");
		itemMapping.put(new Item("banner",1),"red_banner");
		itemMapping.put(new Item("banner",0),"black_banner");
		itemMapping.put(new Item("chorus_fruit_popped",0),"popped_chorus_fruit");
		itemMapping.put(new Item("record_13",0),"music_disc_13");
		itemMapping.put(new Item("record_cat",0),"music_disc_cat");
		itemMapping.put(new Item("record_blocks",0),"music_disc_blocks");
		itemMapping.put(new Item("record_chirp",0),"music_disc_chirp");
		itemMapping.put(new Item("record_far",0),"music_disc_far");
		itemMapping.put(new Item("record_mall",0),"music_disc_mall");
		itemMapping.put(new Item("record_mellohi",0),"music_disc_mellohi");
		itemMapping.put(new Item("record_stal",0),"music_disc_stal");
		itemMapping.put(new Item("record_strad",0),"music_disc_strad");
		itemMapping.put(new Item("record_ward",0),"music_disc_ward");
		itemMapping.put(new Item("record_11",0),"music_disc_11");
		itemMapping.put(new Item("record_wait",0),"music_disc_wait");
		
	}
	
	static HashMap<Integer,String> enchantments = new HashMap<Integer,String>();
	static {
		enchantments.put(0,"protection");
		enchantments.put(1,"fire_protection");
		enchantments.put(2,"feather_falling");
		enchantments.put(3,"blast_protection");
		enchantments.put(4,"projectile_protection");
		enchantments.put(5,"respiration");
		enchantments.put(6,"aqua_affinity");
		enchantments.put(7,"thorns");
		enchantments.put(8,"depth_strider");
		enchantments.put(9,"frost_walker");
		enchantments.put(10,"binding_curse");
		enchantments.put(16,"sharpness");
		enchantments.put(17,"smite");
		enchantments.put(18,"bane_of_arthropods");
		enchantments.put(19,"knockback");
		enchantments.put(20,"fire_aspect");
		enchantments.put(21,"looting");
		enchantments.put(22,"sweeping");
		enchantments.put(32,"efficiency");
		enchantments.put(33,"silk_touch");
		enchantments.put(34,"unbreaking");
		enchantments.put(35,"fortune");
		enchantments.put(48,"power");
		enchantments.put(49,"punch");
		enchantments.put(50,"flame");
		enchantments.put(51,"infinity");
		enchantments.put(61,"luck_of_the_sea");
		enchantments.put(62,"lure");
		enchantments.put(70,"mending");
		enchantments.put(71,"vanishing_curse");
	}
	
	HashMap<Block,Block> blockCache = new HashMap<Block,Block>();
	HashMap<Integer,TagCompound> tileEntCache = new HashMap<Integer,TagCompound>();
	
	public static final int BANNER_CONVERT_BASE = 0x1;
	public static final int BANNER_CONVERT_PATTERN = 0x2;
	
	int bannerHandling = 0;
	
	public SchematicConverter(int bannerHandling) {
		this.bannerHandling = bannerHandling;
	}

	public void convert(SchematicData data, byte[] blocks, byte[] bdata, List<TagCompound> tileEntities,  int w, int h, int l, JTextPane area) {
		
		buildTileEntCache(tileEntities);
		
		for(int y=0;y<h;y++) {
			for(int x=0;x<w;x++) {
				for(int z=0;z<l;z++) {
					
					Block bl = getBlock(x,y,z,blocks,bdata,tileEntities,w,h,l);
					
					//*
					if(blockCache.containsKey(bl)) {
						bl = blockCache.get(bl);
					}else {
						blockCache.put(bl, bl);
					}
					//*/
					data.setBlockAt(x, y, z, bl);
					
				}
			}
			
			if(area!=null) {
				final int yy = y+1;
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						area.setText(area.getText()+"\n"+yy+" of "+h);
					}
				});
			}
		}
		
		System.out.println(" Unique blocks: "+ blockCache.size());
		blockCache.clear();
		tileEntCache.clear();
		
		fixEntities(data.entities, true);
	}
	
	public void clearCache() {
		blockCache.clear();
		tileEntCache.clear();
	}

	public Block getBlock(int x, int y, int z, byte[] blocks, byte[] bdata, List<TagCompound> tileEntities, int w, int h, int l) {
		
		Block block = new Block();
		
		int blockId = getBlockAt(x,y,z,blocks,w,h,l);//blocks[getCoord(x,y,z,w,h,l)];
		
		int blockdata = getDataAt(x,y,z,bdata,w,h,l);
		
		//now get correct block type
		String name = "";
		switch(blockId) {
			default:
				block.type = S2CB.BLOCK_TYPES.get(materials[blockId]);
				break;

			case 1: //stone
				name = "stone";
				switch(blockdata) {
				case 1: name = "granite"; break;
				case 2: name = "polished_granite"; break;
				case 3: name = "diorite"; break;
				case 4: name = "polished_diorite"; break;
				case 5: name = "andesite"; break;
				case 6: name = "polished_andesite"; break;
				}
				block.type = S2CB.BLOCK_TYPES.get(name);
				break;
			case 3:
				name = "dirt";
				switch(blockdata) {
				case 1: name = "coarse_dirt"; break;
				case 2: name = "podzol"; break;
				}
				block.type = S2CB.BLOCK_TYPES.get(name);
				break;
			case 5:
				name = getWoodType(blockdata)+"_planks";
				block.type = S2CB.BLOCK_TYPES.get(name);
				break;
			case 6:
				name = getWoodType(blockdata)+"_sapling";
				block.type = S2CB.BLOCK_TYPES.get(name);
				break;
			case 8:
				block.type = S2CB.BLOCK_TYPES.get("water");
				block.properties = "level=" + blockdata;//(blockdata & 0x7) + ",falling=" + Boolean.toString((blockdata & 0x8)>0);
				break;
			case 9:
				block.type = S2CB.BLOCK_TYPES.get("water");
				block.properties = "level=" + blockdata;//(blockdata & 0x7) + ",falling=" + Boolean.toString((blockdata & 0x8)>0);
				break;
			case 10:
				block.type = S2CB.BLOCK_TYPES.get("lava");
				block.properties = "level=" + blockdata;//(blockdata & 0x7) + ",falling=" + Boolean.toString((blockdata & 0x8)>0);
				break;
			case 11:
				block.type = S2CB.BLOCK_TYPES.get("lava");
				block.properties = "level=" + blockdata;//(blockdata & 0x7) + ",falling=" + Boolean.toString((blockdata & 0x8)>0);
				break;
			case 12:
				block.type = S2CB.BLOCK_TYPES.get(blockdata==1?"red_sand":"sand");
				break;
			case 17:
				name = getWoodType(blockdata & 0x3)+"_log";
				block.type = S2CB.BLOCK_TYPES.get(name);
				{
					String axis = "";
					switch(blockdata >> 2) {
					case 0: axis="axis=y"; break;
					case 1: axis="axis=x"; break;
					case 2: axis="axis=z"; break;
					case 3: //bark block - command only before 1.13 - no axis specified
					}
					block.properties = axis.length()>0?axis:null;
				}
				break;
			case 18:
				name = getWoodType(blockdata & 0x3)+"_leaves";
				block.type = S2CB.BLOCK_TYPES.get(name);
				block.properties = ((blockdata & 4) > 0)?"persistent=true":"persistent=false";
				break;
			case 19:
				block.type = S2CB.BLOCK_TYPES.get(blockdata==1?"wet_sponge":"sponge");
				break;
			case 23:
				block.type = S2CB.BLOCK_TYPES.get("dispenser");
				{
					String prop = "facing="+getBlockDirection(blockdata);
					prop+=","+(((blockdata & 8)>0)?"triggered=true":"triggered=false");
					block.properties = prop;
				}
				break;
			case 24:
				switch(blockdata) {
				case 1: name = "chiseled_"; break;
				case 2: name = "cut_"; break;
				}
				name += "sandstone";
				block.type = S2CB.BLOCK_TYPES.get(name);
				break;
			case 25:
				block.type = S2CB.BLOCK_TYPES.get("note_block");
				{
					int note = 0;
					boolean powered = false;
					try {
						TagCompound info = getBlockEntityData( x, y, z);
						if(info != null) {
							note = info.getByte("note");
							powered = info.getByte("powered") > 0;
						}
					}catch (UnexpectedTagTypeException | TagNotFoundException e) {
						System.out.println("bad note block tile entity at "+x+","+y+","+z+" : "+e.getMessage());
					}
					block.properties = "note=" + note +",powered=" + Boolean.toString(powered);
				}
				break;
			case 26://bed
				{
					String color = "red";
					try {
						TagCompound info = getBlockEntityData(x,y,z);
						if(info != null) {
							int c = info.getInteger("color");
							
							color = getColorName(c);
						}
					}catch (UnexpectedTagTypeException | TagNotFoundException e) {
						System.out.println("bad tile entity at "+x+","+y+","+z);
						e.printStackTrace();
					}
					block.type = S2CB.BLOCK_TYPES.get(color+"_bed");
					
					boolean head = ((blockdata & 0x8)>0);
					String prop="south";
					switch(blockdata&0x3) {
					case 1: prop="west"; break;
					case 2: prop="north"; break;
					case 3: prop="east"; break;
					}
					block.properties = "facing=" + prop + 
							",occupied=" + (((blockdata & 0x8)>0)?"true":"false") +
							",part=" + (head?"head":"foot");
				}
				break;
			case 27: //powered_rail
			case 28: //detector_rail
			case 157: //activator rail
				block.type = S2CB.BLOCK_TYPES.get(materials[blockId]);
				{
					String shape = "shape=north_south";
					switch(blockdata & 0x7) {
					case 1: shape="shape=east_west"; break;
					case 2: shape="shape=ascending_east"; break;
					case 3: shape="shape=ascending_west"; break;
					case 4: shape="shape=ascending_north"; break;
					case 5: shape="shape=ascending_south"; break;
					}
					block.properties = shape + ",powered=" + (((blockdata & 0x8)>0)?"true":"false");
				}
				break;
			case 29:
				block.type = S2CB.BLOCK_TYPES.get("sticky_piston");
				{
					String prop = "facing="+getBlockDirection(blockdata);
					prop += ",extended=" + (((blockdata & 0x8)>0)?"true":"false");
					block.properties = prop;
				}
				break;
			case 31:
				name = "grass";
				//ok, we have some disagreement here.  Wiki says 1 is fern, however, I have found some older schematics that should be grass, but have values of 1, and I found a page that back that up, saying fren is 2. So, if you really want 1 to be fern, here is where you make that change.
				//if(blockdata == 1) name = "fern";
				if(blockdata == 2) name = "fern";
				block.type = S2CB.BLOCK_TYPES.get(name);
				break;
			case 33:
				block.type = S2CB.BLOCK_TYPES.get("piston");
				{
					String prop = "facing="+getBlockDirection(blockdata);
					prop += ",extended=" + (((blockdata & 0x8)>0)?"true":"false");
					block.properties = prop;
				}
				break;
			case 34: //piston_head
			case 36: //moving_piston  piston-extension  - probably should never be in a schematic, but just in case...
				block.type = S2CB.BLOCK_TYPES.get("piston_head");
				{
					String prop = "facing="+getBlockDirection(blockdata);
					prop += ",type=" + (((blockdata & 0x8)>0)?"sticky":"normal");
					block.properties = prop;
				}
				break;
			case 35:
				name = getColorName(blockdata) + "_wool";
				block.type = S2CB.BLOCK_TYPES.get(name);
				break;
			case 38:
				name = "poppy";
				switch(blockdata & 0x7) {
				case 1: name="blue_orchid"; break;
				case 2: name="allium"; break;
				case 3: name="azure_bluet"; break;
				case 4: name="red_tulip"; break;
				case 5: name="orange_tulip"; break;
				case 6: name="white_tulip"; break;
				case 7: name="pink_tulip"; break;
				case 8: name="oxeye_daisy"; break;
				}
				block.type = S2CB.BLOCK_TYPES.get(name);
				break;
			case 43:
				name = "stone_slab"; //TODO 1.14 need to update stone_slab to smooth_stone_slab
				switch(blockdata) {
				case 1: name="sandstone_slab"; break;
				case 2: name="petrified_oak_slab"; break;
				case 3: name="cobblestone_slab"; break;
				case 4: name="brick_slab"; break;
				case 5: name="stone_brick_slab"; break;
				case 6: name="nether_brick_slab"; break;
				case 7: name="quartz_slab"; break;
				case 8: name="smooth_stone"; break;
				case 9: name="smooth_sandstone"; break;
				}
				block.type = S2CB.BLOCK_TYPES.get(name);
				block.properties = "type=double";
				if(blockdata==8||blockdata==9) block.properties="";
				break;
			case 44:
				name = "stone_slab"; //TODO 1.14 need to update stone_slab to smooth_stone_slab
				switch(blockdata & 0x7) {
				case 1: name="sandstone_slab"; break;
				case 2: name="petrified_oak_slab"; break;
				case 3: name="cobblestone_slab"; break;
				case 4: name="brick_slab"; break;
				case 5: name="stone_brick_slab"; break;
				case 6: name="nether_brick_slab"; break;
				case 7: name="quartz_slab"; break;
				}
				block.type = S2CB.BLOCK_TYPES.get(name);
				block.properties = "type=" + (((blockdata & 0x8)>0)?"top":"bottom");
				break;
			case 45:
				block.type = S2CB.BLOCK_TYPES.get("bricks");
				break;
			case 50:
				name = "torch";
				if(blockdata > 0 && blockdata < 5) {
					name = "wall_torch";
					String prop="north";
					switch(blockdata) {
					case 1: prop="east"; break;
					case 2: prop="west"; break;
					case 3: prop="south"; break;
					}
					block.properties = "facing="+prop;
				}
				block.type = S2CB.BLOCK_TYPES.get(name);
				break;
			case 51:
				block.type = S2CB.BLOCK_TYPES.get("fire");
				{
					//not sure if this is necessary
					block.compound = new TagCompound("fire");
					TagCompound nbt = new TagCompound("nbt");
					nbt.setParent(block.compound);
					TagInteger age = new TagInteger("age",blockdata);
					age.setParent(nbt);
				}
				break;
			case 53:
				block.type = S2CB.BLOCK_TYPES.get("oak_stairs");
				block.properties = getStairProps(blockdata, x, y, z, blocks, w, h, l, bdata);
				break;
			case 54:
			case 146:
				block.type = S2CB.BLOCK_TYPES.get(materials[blockId]);
				{
					String direction = getBlockDirection(blockdata,false);
					String type = "single"; //need to check if it is a double chest
					switch(direction) {
						case "north":{
							if( getBlockAt(x-1,y,z,blocks,w,h,l) == blockId) {
								type = "right";
							}else if ( getBlockAt(x+1,y,z,blocks,w,h,l) == blockId) {
								type = "left";
							}
							break;
						}
						case "south":{
							if( getBlockAt(x-1,y,z,blocks,w,h,l) == blockId) {
								type = "left";
							}else if ( getBlockAt(x+1,y,z,blocks,w,h,l) == blockId) {
								type = "right";
							}
							break;
						}
						case "east":{
							if( getBlockAt(x,y,z+1,blocks,w,h,l) == blockId) {
								type = "left";
							}else if ( getBlockAt(x,y,z-1,blocks,w,h,l) == blockId) {
								type = "right";
							}
							break;
						}
						case "west":{
							if( getBlockAt(x,y,z+1,blocks,w,h,l) == blockId) {
								type = "right";
							}else if ( getBlockAt(x,y,z-1,blocks,w,h,l) == blockId) {
								type = "left";
							}
							break;
						}
					}
					block.properties = "type="+type+",facing="+direction;
					
				}
				break;
			case 55:
				block.type = S2CB.BLOCK_TYPES.get("redstone_wire");
				{

					String prop = "power=" + blockdata;
					prop += ",south=";
					if(isRedstonePart(getBlockAt(x,y,z+1,blocks,w,h,l)) ||
							getBlockAt(x,y-1,z+1,blocks,w,h,l) == 55) {
						prop += "side";
					}else if(getBlockAt(x,y+1,z+1,blocks,w,h,l) == 55){
						prop += "up";
					}else {
						prop += "none";
					}
					prop += ",north=";
					if(isRedstonePart(getBlockAt(x,y,z-1,blocks,w,h,l)) || 
							getBlockAt(x,y-1,z-1,blocks,w,h,l) == 55) {
						prop += "side";
					}else if(getBlockAt(x,y+1,z-1,blocks,w,h,l) == 55){
						prop += "up";
					}else {
						prop += "none";
					}
					prop += ",east=";
					if(isRedstonePart(getBlockAt(x+1,y,z,blocks,w,h,l)) || 
							getBlockAt(x+1,y-1,z,blocks,w,h,l) == 55) {
						prop += "side";
					}else if(getBlockAt(x+1,y+1,z,blocks,w,h,l) == 55){
						prop += "up";
					}else {
						prop += "none";
					}
					prop += ",west=";
					if(isRedstonePart(getBlockAt(x-1,y,z,blocks,w,h,l)) || 
							getBlockAt(x-1,y-1,z,blocks,w,h,l) == 55) {
						prop += "side";
					}else if(getBlockAt(x-1,y+1,z,blocks,w,h,l) == 55){
						prop += "up";
					}else {
						prop += "none";
					}
					block.properties = prop;
				}
				break;
			case 59:
				block.type = S2CB.BLOCK_TYPES.get("wheat");
				block.properties = "age=" + blockdata;
				break;
			case 60:
				block.type = S2CB.BLOCK_TYPES.get("farmland");
				block.properties = "moisture=" + blockdata;
				break;
			case 61:
				block.type = S2CB.BLOCK_TYPES.get("furnace");
				block.properties = "lit=false,facing=" + getBlockDirection(blockdata);
				break;
			case 62:
				block.type = S2CB.BLOCK_TYPES.get("furnace");
				block.properties = "lit=true,facing=" + getBlockDirection(blockdata);
				break;
			case 63:
				block.type = S2CB.BLOCK_TYPES.get("sign");
				block.properties = "rotation=" + blockdata;
				break;
			case 64:
				block.type = S2CB.BLOCK_TYPES.get("oak_door");
				block.properties = getDoorProps(blockdata,x,y,z,bdata,w,h,l);
				break;
			case 65:
				block.type = S2CB.BLOCK_TYPES.get("ladder");
				block.properties = "facing=" + getBlockDirection(blockdata);
				break;
			case 66:
				block.type = S2CB.BLOCK_TYPES.get("rail");
				{
					String prop="north_south";
					switch(blockdata) {
					case 1: prop="east_west"; break;
					case 2: prop="ascending_east"; break;
					case 3: prop="ascending_west"; break;
					case 4: prop="ascending_north"; break;
					case 5: prop="ascending_south"; break;
					case 6: prop="south_east"; break;
					case 7: prop="south_west"; break;
					case 8: prop="north_west"; break;
					case 9: prop="north_east"; break;
					}
					block.properties = "shape="+prop;
				}
				break;
			case 67:
				block.type = S2CB.BLOCK_TYPES.get("cobblestone_stairs");
				block.properties = getStairProps(blockdata, x, y, z, blocks, w, h, l, bdata);
				break;
			case 68:
				block.type = S2CB.BLOCK_TYPES.get("wall_sign");
				block.properties = "facing=" + getBlockDirection(blockdata);
				break;
			case 69:
				block.type = S2CB.BLOCK_TYPES.get("lever");
				{
					boolean powered = (blockdata & 0x8) > 0;
					String prop=",face=ceiling,facing=west";
					switch(blockdata & 0x7) {
					case 1: prop=",face=wall,facing=east"; break;
					case 2: prop=",face=wall,facing=west"; break;
					case 3: prop=",face=wall,facing=south"; break;
					case 4: prop=",face=wall,facing=north"; break;
					case 5: prop=",face=floor,facing=north"; break;
					case 6: prop=",face=floor,facing=west"; break;
					case 7: prop=",face=ceiling,facing=north"; break;
					}
					block.properties = "powered=" + Boolean.toString(powered) + prop;
				}
				break;
			case 70:
				block.type = S2CB.BLOCK_TYPES.get("stone_pressure_plate");
				block.properties = "powered=" + Boolean.toString(blockdata == 1);
				break;
			case 71:
				block.type = S2CB.BLOCK_TYPES.get("iron_door");
				block.properties = getDoorProps(blockdata,x,y,z,bdata,w,h,l);
				break;
			case 72:
				block.type = S2CB.BLOCK_TYPES.get("oak_pressure_plate");
				block.properties = "powered=" + Boolean.toString(blockdata == 1);
				break;
			case 73:
			case 74:
				block.type = S2CB.BLOCK_TYPES.get("redstone_ore");
				block.properties = "lit=" + Boolean.toString(blockId == 74);
				break;
			case 75:
			case 76:
				name = "redstone_torch";
				if(blockdata > 0 && blockdata < 5) {
					name = "redstone_wall_torch";
					String prop="north";
					switch(blockdata) {
					case 1: prop="east"; break;
					case 2: prop="west"; break;
					case 3: prop="south"; break;
					}
					block.properties = "facing="+prop;
				}
				block.type = S2CB.BLOCK_TYPES.get(name);
				if(block.properties!=null && block.properties.length() > 0) {
					block.properties += ",lit=" + Boolean.toString(blockId == 76);
				}else {
					block.properties = "lit=" + Boolean.toString(blockId == 76);
				}
				break;
			case 77:
				block.type = S2CB.BLOCK_TYPES.get("stone_button");
				{
					boolean powered = (blockdata & 0x8) > 0;
					String prop=",face=ceiling,facing=east";
					switch(blockdata & 0x7) {
					case 1: prop=",face=wall,facing=east"; break;
					case 2: prop=",face=wall,facing=west"; break;
					case 3: prop=",face=wall,facing=south"; break;
					case 4: prop=",face=wall,facing=north"; break;
					case 5: prop=",face=floor,facing=east"; break;
					}
					block.properties = "powered=" + Boolean.toString(powered) + prop;
				}
				break;
			case 78:
				block.type = S2CB.BLOCK_TYPES.get("snow");
				block.properties = "layers=" + (blockdata + 1);
				break;
			case 81:
				block.type = S2CB.BLOCK_TYPES.get("cactus");
				block.properties = "age=" + blockdata;
				break;
			case 83:
				block.type = S2CB.BLOCK_TYPES.get("sugar_cane");
				block.properties = "age=" + blockdata;
				break;
			case 84:
				block.type = S2CB.BLOCK_TYPES.get("jukebox");
				block.properties = "has_record=" +  Boolean.toString(blockdata == 1);
				break;
			case 85:
				block.type = S2CB.BLOCK_TYPES.get("oak_fence");
				block.properties = getFenceProperties(blockdata,false,x,y,z,blocks,w,h,l);
				break;
			case 86:
			case 91:
				if(blockId==86) {
					block.type = S2CB.BLOCK_TYPES.get("carved_pumpkin");
				}else{
					block.type = S2CB.BLOCK_TYPES.get("jack_o_lantern");
				}
				{
					String prop="south";
					switch(blockdata) {
					case 1: prop="west"; break;
					case 2: prop="north"; break;
					case 3: prop="east"; break;
					}
					block.properties = "facing=" + prop;
				}
				break;
			case 90:
				block.type = S2CB.BLOCK_TYPES.get("nether_portal");
				if( getBlockAt(x, y, z+1, blocks, w, h, l) == 90 || getBlockAt(x, y, z-1, blocks, w, h, l) == 90) {
					block.properties = "axis=z";
				} else {
					block.properties = "axis=x";
				}
				break;
			case 92: 
				block.type = S2CB.BLOCK_TYPES.get("cake");
				block.properties = "bites=" + blockdata;
				break;
			case 93:
			case 94:
				block.type = S2CB.BLOCK_TYPES.get("repeater");
				{
					String facing = "south";
					switch(blockdata & 0x3) {
					case 1: facing = "west"; break;
					case 2: facing = "north"; break;
					case 3: facing = "east"; break;
					}
					int delay = ((blockdata >> 2) & 0x3) + 1;
					block.properties = "facing=" + facing + ",delay=" + delay + 
							",powered=" + Boolean.toString(blockId==94);	
					//there are also new locked and powered properties, but hopefully the game will fill them out.
				}
				break;
			case 95:
				name = getColorName(blockdata) + "_stained_glass";
				block.type = S2CB.BLOCK_TYPES.get(name);
				break;
			case 96:
				block.type = S2CB.BLOCK_TYPES.get("oak_trapdoor");
				{
					String facing = "north";
					switch(blockdata & 0x3) {
					case 1: facing = "south"; break;
					case 2: facing = "west"; break;
					case 3: facing = "east"; break;
					}
					block.properties = "facing=" + facing + ",half=" + (((blockdata & 0x8)>0)?"top":"bottom") +
							",open=" + Boolean.toString((blockdata & 0x4)>0);
					//there are also new locked and powered properties, but hopefully the game will fill them out.
				}
				break;
			case 97:
				name = "infested_stone";
				switch(blockdata) {
				case 1: name = "infested_cobblestone"; break;
				case 2: name = "infested_stone_bricks"; break;
				case 3: name = "infested_cracked_stone_bricks"; break;
				case 4: name = "infested_mossy_stone_bricks"; break;
				case 5: name = "infested_chiseled_stone_bricks"; break;
				}
				block.type = S2CB.BLOCK_TYPES.get(name);
				break;
			case 98:
				name = "stone_bricks";
				switch(blockdata) {
				case 1: name = "cracked_stone_bricks"; break;
				case 2: name = "mossy_stone_bricks"; break;
				case 3: name = "chiseled_stone_bricks"; break;
				}
				block.type = S2CB.BLOCK_TYPES.get(name);
				break;
			case 99:
			case 100:
				if (blockId == 99) {
					name = "brown_mushroom_block";
				} else {
					name = "red_mushroom_block";
				}
				{
					boolean u,d,n,s,e,ww;
					u = d = n = s = e = ww = false;
					switch(blockdata) {
					default: break;
					case 1: u = ww = n = true; break;
					case 2: u = n = true; break;
					case 3: u = n = e = true; break;
					case 4: u = ww = true; break;
					case 5: u = true; break;
					case 6: u = e =true; break;
					case 7: u = s = ww =true; break;
					case 8: u = s = true; break;
					case 9: u = s = e = true; break;
					case 10: n = s = e = ww =true; name = "mushroom_stem"; break;
					case 14: u = d = n = s = e = ww =true; break;
					case 15: u = d = n = s = e = ww =true; name = "mushroom_stem"; break;
					}
					block.properties = "up=" + Boolean.toString(u) +
							",down=" + Boolean.toString(d) +
							",north=" + Boolean.toString(n) +
							",south=" + Boolean.toString(s) +
							",east=" + Boolean.toString(e) +
							",west=" + Boolean.toString(ww);
				}
				block.type = S2CB.BLOCK_TYPES.get(name);
				break;
			case 101:
				block.type = S2CB.BLOCK_TYPES.get("iron_bars");
				block.properties = getPaneBarProperties(blockdata, x, y, z, blocks, w, h, l);
				break;
			case 102:
				block.type = S2CB.BLOCK_TYPES.get("glass_pane");
				block.properties = getPaneBarProperties(blockdata, x, y, z, blocks, w, h, l);
				break;
			case 104:
				block.type = S2CB.BLOCK_TYPES.get("pumpkin_stem");
				block.properties = "age=" + blockdata;
				break;
			case 105:
				block.type = S2CB.BLOCK_TYPES.get("melon_stem");
				block.properties = "age=" + blockdata;
				break;
			case 106:
				block.type = S2CB.BLOCK_TYPES.get("vine");
				block.properties = "south=" + Boolean.toString((blockdata & 0x1)>0) +
						",west=" + Boolean.toString((blockdata & 0x2)>0) +
						",north=" + Boolean.toString((blockdata & 0x4)>0) +
						",east=" + Boolean.toString((blockdata & 0x8)>0); //hopefully the game figures out "up" itself
				break;
			case 107:
				block.type = S2CB.BLOCK_TYPES.get("oak_fence_gate");
				{
					String facing = "south";
					switch(blockdata & 0x3) {
					case 1: facing = "west"; break;
					case 2: facing = "north"; break;
					case 3: facing = "east"; break;
					}
					block.properties = "facing=" + facing + ",open=" + Boolean.toString((blockdata & 0x4)>0);
				}
				break;
			case 108: //brick stairs
			case 109: //stone brick stairs
			case 114: //nether brick stairs
				block.type = S2CB.BLOCK_TYPES.get(materials[blockId]);
				block.properties = getStairProps(blockdata, x, y, z, blocks, w, h, l, bdata);
				break;
			case 113:
				block.type = S2CB.BLOCK_TYPES.get("nether_brick_fence");
				block.properties = getFenceProperties(blockdata,true,x,y,z,blocks,w,h,l);
				break;
			case 115:
				block.type = S2CB.BLOCK_TYPES.get("nether_wart");
				block.properties = "age=" + blockdata;
				break;
			case 117:
				block.type = S2CB.BLOCK_TYPES.get("brewing_stand");
				block.properties = "has_bottle_0=" + Boolean.toString((blockdata & 0x1)>0) +
						",has_bottle_1=" + Boolean.toString((blockdata & 0x2)>0) +
						",has_bottle_2=" + Boolean.toString((blockdata & 0x4)>0);
				break;
			case 118:
				block.type = S2CB.BLOCK_TYPES.get("cauldron");
				block.properties = "level=" + blockdata;
				break;
			case 120:
				block.type = S2CB.BLOCK_TYPES.get("end_portal_frame");
				{
					String facing = "south";
					switch(blockdata & 0x3) {
					case 1: facing = "west"; break;
					case 2: facing = "north"; break;
					case 3: facing = "east"; break;
					}
					block.properties = "facing=" + facing + ",eye=" + Boolean.toString((blockdata & 0x4)>0);
				}
				break;
			case 123:
			case 124:
				block.type = S2CB.BLOCK_TYPES.get("redstone_lamp");
				block.properties = "lit=" + Boolean.toString(blockId == 124);
				break;
			case 125:
				name = getWoodType(blockdata) + "_slab";
				block.type = S2CB.BLOCK_TYPES.get(name);
				block.properties = "type=double";
				break;
			case 126:
				name = getWoodType(blockdata & 0x7) + "_slab";
				block.type = S2CB.BLOCK_TYPES.get(name);
				block.properties = "type=" + (((blockdata & 0x8)>0)?"top":"bottom");
				break;
			case 127:
				block.type = S2CB.BLOCK_TYPES.get("cocoa");
				{
					String facing = "north";
					switch(blockdata & 0x3) {
					case 1: facing = "east"; break;
					case 2: facing = "south"; break;
					case 3: facing = "west"; break;
					}
					block.properties = "age=" + (blockdata >> 2) +
							",facing=" + facing;
				}
				break;
			case 128: //nether brick stairs
				block.type = S2CB.BLOCK_TYPES.get(materials[blockId]);
				block.properties = getStairProps(blockdata, x, y, z, blocks, w, h, l, bdata);
				break;
			case 130:
				block.type = S2CB.BLOCK_TYPES.get("ender_chest");
				block.properties = "facing=" + getBlockDirection(blockdata);
				break;
			case 131:
				block.type = S2CB.BLOCK_TYPES.get("tripwire_hook");
				{
					String facing = "south";
					switch(blockdata & 0x3) {
					case 1: facing = "west"; break;
					case 2: facing = "north"; break;
					case 3: facing = "east"; break;
					}
					block.properties = "facing=" + facing + 
							",attached=" + Boolean.toString((blockdata & 0x4)>0) +
							",powered=" + Boolean.toString((blockdata & 0x8)>0);
				}
				
				break;
			case 132:
				block.type = S2CB.BLOCK_TYPES.get("tripwire");
				block.properties = "powered=" + Boolean.toString((blockdata & 0x1)>0) +
						",attached=" + Boolean.toString((blockdata & 0x4)>0) +
						",disarmed=" + Boolean.toString((blockdata & 0x8)>0);
				break;
			case 134:
			case 135:
			case 136: //3 wood stairs
				block.type = S2CB.BLOCK_TYPES.get(materials[blockId]);
				block.properties = getStairProps(blockdata, x, y, z, blocks, w, h, l, bdata);
				break;
			case 137:
			case 210:
			case 211: // command blocks
				block.type = S2CB.BLOCK_TYPES.get(materials[blockId]);
				block.properties = "facing=" + getBlockDirection(blockdata) + 
						",conditional=" + Boolean.toString((blockdata & 0x8)>0);
				break;
			case 139:
				name = "cobblestone_wall";
				if(blockdata == 1) {
					name = "mossy_" + name;
				}
				block.type = S2CB.BLOCK_TYPES.get(name);
				block.properties = getWallProperties(blockdata, x, y, z, blocks, w, h, l);
				break;
			case 140:
				name = "flower_pot";
				{
					//saved flower is in block entity, but not in a straightfoward way
					/*
contents		item		data
empty			air			0
poppy			red_flower	0
blue orchid		red_flower	1
allium			red_flower	2
houstonia		red_flower	3
red tulip		red_flower	4
orange tulip	red_flower	5
white tulip		red_flower	6
pink tulip		red_flower	7
oxeye daisy		red_flower	8
dandelion		yellow_flower	0
red mushroom	red_mushroom	0
brown mushroom	brown_mushroom	0
oak sapling		sapling		0
spruce sapling	sapling		1
birch sapling	sapling		2
jungle sapling	sapling		3
acacia sapling	sapling		4
dark oak sapling	sapling	5
dead bush			0
fern			tallgrass	2
cactus			cactus		0
					 */
					try {
						TagCompound info = getBlockEntityData(x,y,z);
						if(info !=null) {
							int data = info.getInteger("Data");
							String item = info.getString("Item");
							if(item.startsWith("minecraft:")) {
								item = item.substring(10);
							}
							switch(item) {
							case "air": break;
							case "red_flower":
								switch(data) {
								default: name = "potted_poppy"; break;
								case 1: name = "potted_blue_orchid"; break;
								case 2: name = "potted_allium"; break;
								case 3: name = "potted_azure_bluet"; break;
								case 4: name = "potted_red_tulip"; break;
								case 5: name = "potted_orange_tulip"; break;
								case 6: name = "potted_white_tulip"; break;
								case 7: name = "potted_pink_tulip"; break;
								case 8: name = "potted_oxeye_daisy"; break;
								}
								break;
							case "yellow_flower": name = "potted_dandelion"; break;
							case "red_mushroom": name = "potted_red_mushroom"; break;
							case "brown_mushroom": name = "potted_brown_mushroom"; break;
							case "sapling":
								switch(data) {
								default: name = "potted_oak_sapling"; break;
								case 1: name = "potted_spruce_sapling"; break;
								case 2: name = "potted_birch_sapling"; break;
								case 3: name = "potted_jungle_sapling"; break;
								case 4: name = "potted_acacia_sapling"; break;
								case 5: name = "potted_dark_oak_sapling"; break;
								}
								break;
							case "deadbush": name = "potted_dead_bush"; break;
							case "tallgrass": name = "potted_fern"; break;
							case "cactus": name = "potted_cactus"; break;
							}
						}
					}catch (UnexpectedTagTypeException | TagNotFoundException e) {
						System.out.println("bad tile entity at "+x+","+y+","+z);
						e.printStackTrace();
					}
					block.type = S2CB.BLOCK_TYPES.get(name);
				}
				break;
			case 141: //carrots
			case 142: //potato
				block.type = S2CB.BLOCK_TYPES.get(materials[blockId]);
				block.properties = "age=" + blockdata;
				break;
			case 143:
				block.type = S2CB.BLOCK_TYPES.get("oak_button");
				{
					boolean powered = (blockdata & 0x8) > 0;
					String prop=",face=ceiling,facing=east";
					switch(blockdata & 0x7) {
					case 1: prop=",face=wall,facing=east"; break;
					case 2: prop=",face=wall,facing=west"; break;
					case 3: prop=",face=wall,facing=south"; break;
					case 4: prop=",face=wall,facing=north"; break;
					case 5: prop=",face=floor,facing=east"; break;
					}
					block.properties = "powered=" + Boolean.toString(powered) + prop;
				}
				break;
			case 144: // mob head / skull
				name = "wither_skeleton_@skull";
				{
					try {
						TagCompound info = getBlockEntityData(x,y,z);
						if(info !=null) {
							byte rot = info.getByte("Rot");
							byte type = info.getByte("SkullType");
							
							switch(type) {
							case 0: name = "skeleton_@skull"; break;
							case 2: name = "player_@head"; break;
							case 3: name = "zombie_@head"; break;
							case 4: name = "creeper_@head"; break;
							case 5: name = "dragon_@head"; break;
							}
					
							String facing = "";
							if(blockdata == 1) {
								name = name.replace("@", "");
							}else {
								name = name.replace("@", "wall_");
							}
							switch(blockdata) {
							case 2: facing = "north"; break;
							case 3: facing = "south"; break;
							case 4: facing = "west"; break;
							case 5: facing = "east"; break;
							}
							if(facing.length()>1) {
								block.properties = "facing=" + facing;
							}else {
								block.properties = "rotation=" + rot;
							}
						}else {
							//no info, assume skeleton?
							name = "skeleton_@skull";
							int rot = 0;
							
							String facing = "";
							if(blockdata == 1) {
								name = name.replace("@", "");
							}else {
								name = name.replace("@", "wall_");
							}
							switch(blockdata) {
							case 2: facing = "north"; break;
							case 3: facing = "south"; break;
							case 4: facing = "west"; break;
							case 5: facing = "east"; break;
							}
							if(facing.length()>1) {
								block.properties = "facing=" + facing;
							}else {
								block.properties = "rotation=" + rot;
							}
							
						}
					}catch (UnexpectedTagTypeException | TagNotFoundException e) {
						System.out.println("bad tile entity at "+x+","+y+","+z);
						e.printStackTrace();
					}
				}
				block.type = S2CB.BLOCK_TYPES.get(name);
				break;
			case 145:
				name = "anvil";
				switch(blockdata / 4) {
				case 1: name = "chipped_anvil";
				case 2: name = "damaged_anvil";
				}
				block.type = S2CB.BLOCK_TYPES.get(name);
				{
					String facing = "south";
					switch(blockdata & 0x3) {
					case 1: facing = "west"; break;
					case 2: facing = "north"; break;
					case 3: facing = "east"; break;
					}
					block.properties = "facing=" + facing;
				}
				break;
			case 147: //gold pressure plate
			case 148: //iron pressure plate
				block.type = S2CB.BLOCK_TYPES.get(materials[blockId]);
				block.properties = "power=" + blockdata;
				break;
			case 149:
			case 150:
				block.type = S2CB.BLOCK_TYPES.get("comparator");
				{
					String facing = "south";
					switch(blockdata & 0x3) {
					case 1: facing = "west"; break;
					case 2: facing = "north"; break;
					case 3: facing = "east"; break;
					}
					block.properties = "facing=" + facing + ",powered=" + Boolean.toString((blockdata & 0x8)>0) +
							",mode=" + ((blockdata & 0x4)>0?"subtract":"compare");
					//there are also new locked and powered properties, but hopefully the game will fill them out.
				}
				break;
			case 151:
			case 178:
				block.type = S2CB.BLOCK_TYPES.get("daylight_detector");
				block.properties = "power=" + blockdata + ",inverted=" + Boolean.toString(blockId==178);
				break;
			case 154:
				block.type = S2CB.BLOCK_TYPES.get("hopper");
				block.properties = "facing=" + getBlockDirection(blockdata) + ",enabled=" + Boolean.toString((blockdata & 0x8)>0);
				break;
			case 155:
				name = "quartz_block";
				if(blockdata>0) {
					String dir = "";
					switch(blockdata) {
					case 1: name = "chiseled_quartz_block"; break;
					case 2: name = "quartz_pillar"; dir = "y"; break;
					case 3: name = "quartz_pillar"; dir = "x"; break;
					case 4: name = "quartz_pillar"; dir = "z"; break;
					}
					if(dir.length()>0) {
						block.properties = "axis=" + dir;
					}
				}
				block.type = S2CB.BLOCK_TYPES.get(name);
				break;
			case 156:
				block.type = S2CB.BLOCK_TYPES.get("quartz_stairs");
				block.properties = getStairProps(blockdata, x, y, z, blocks, w, h, l, bdata);
				break;
			case 158:
				block.type = S2CB.BLOCK_TYPES.get("dropper");
				block.properties = "facing=" + getBlockDirection(blockdata) + ",triggered=" + Boolean.toString((blockdata & 0x8)>0);
				break;
			case 159:
			case 172:
				name = "terracotta";
				if(blockId == 159) {
					name = getColorName(blockdata) + "_terracotta";
				}
				block.type = S2CB.BLOCK_TYPES.get(name);
				break;
			case 160:
				name = getColorName(blockdata) + "_stained_glass_pane";
				block.type = S2CB.BLOCK_TYPES.get(name);
				block.properties = getPaneBarProperties(blockdata, x, y, z, blocks, w, h, l);
				break;
			case 161:
				name = getWoodType((blockdata & 0x3) + 4)+"_leaves";
				block.type = S2CB.BLOCK_TYPES.get(name);
				block.properties = ((blockdata & 4) > 0)?"persistent=true":"persistent=false";
				break;
			case 162:
				name = getWoodType((blockdata & 0x3) + 4)+"_log";
				block.type = S2CB.BLOCK_TYPES.get(name);
				{
					String axis = "";
					switch(blockdata >> 2) {
					case 0: axis="axis=y"; break;
					case 1: axis="axis=x"; break;
					case 2: axis="axis=z"; break;
					case 3: //bark block - command only before 1.13 - no axis specified
					}
					block.properties = axis.length()>0?axis:null;
				}
				break;
			case 163:
			case 164:
				block.type = S2CB.BLOCK_TYPES.get(getWoodType(blockId-159) + "_stairs");
				block.properties = getStairProps(blockdata, x, y, z, blocks, w, h, l, bdata);
				break;
			case 165:
				block.type = S2CB.BLOCK_TYPES.get("slime_block");
				break;
			case 167: 
				block.type = S2CB.BLOCK_TYPES.get("iron_trapdoor");
				{
					String facing = "north";
					switch(blockdata & 0x3) {
					case 1: facing = "south"; break;
					case 2: facing = "west"; break;
					case 3: facing = "east"; break;
					}
					block.properties = "facing=" + facing + ",half=" + (((blockdata & 0x8)>0)?"top":"bottom") +
							",open=" + Boolean.toString((blockdata & 0x4)>0);
					//there are also new locked and powered properties, but hopefully the game will fill them out.
				}
				break;
			case 168:
				name = "prismarine";
				switch(blockdata) {
				case 1: name = "prismarine_bricks"; break;
				case 2: name = "dark_prismarine"; break;
				}
				block.type = S2CB.BLOCK_TYPES.get(name);
				break;
			case 170:
				block.type = S2CB.BLOCK_TYPES.get("hay_block");
				{
					String axis = "";
					switch(blockdata/4) {
					case 0: axis="axis=y"; break;
					case 1: axis="axis=x"; break;
					case 2: axis="axis=z"; break;
					}
					block.properties = axis.length()>0?axis:null;
				}
				break;
			case 171:
				name = getColorName(blockdata) + "_carpet";
				block.type = S2CB.BLOCK_TYPES.get(name);
				break;
			case 175:
				name = "sunflower";
				boolean tophalf = ((blockdata & 0x8) > 0) ;
				if(tophalf) {
					blockdata = getDataAt(x, y-1, z, bdata, w, h, l);
				}
				switch(blockdata&0x7) {
				case 1: name = "lilac"; break;
				case 2: name = "tall_grass"; break;
				case 3: name = "large_fern"; break;
				case 4: name = "rose_bush"; break;
				case 5: name = "peony"; break;
				}
				block.type = S2CB.BLOCK_TYPES.get(name);
				block.properties = "half=" + (tophalf?"upper":"lower");
				break;
			case 176: 
				{
					TagCompound info = getBlockEntityData( x, y, z);
					int color = 0;
					try {
						if(info!=null) {
							color = info.getInteger("Base");
						}
					}catch (UnexpectedTagTypeException | TagNotFoundException e) {
						System.out.println("bad tile entity at "+x+","+y+","+z);
						e.printStackTrace();
					}
					//need to invert the value as banners originally they were stored inverted, but no longer in 1.13 (?)  Patterns may also need to be inverted, depending on minecraft version?  Leaving as an option for now.  
					if((this.bannerHandling & BANNER_CONVERT_BASE) != 0) {
						color = 15 - color;
					}
					
					block.type = S2CB.BLOCK_TYPES.get(getColorName(color) + "_banner"); //why mess with colors?
					block.properties = "rotation=" + blockdata;
					
					if(info!=null && info.getTags().containsKey("Patterns")) {
						List<TagCompound> patternlist;
						try {
							patternlist = info.getList("Patterns", TagCompound.class);
							for(TagCompound pat : patternlist ) {
								if(pat.getTags().containsKey("Color")) {
									TagInteger colortag = (TagInteger) pat.getTag("Color");
									color = colortag.getValue();
									if((this.bannerHandling & BANNER_CONVERT_PATTERN) != 0) {
										color = 15 - color;
									}
									colortag.setValue(color);  
								}
							}
						} catch (UnexpectedTagTypeException | TagNotFoundException e) {
							System.out.println("bad tile entity at "+x+","+y+","+z);
						}
					}
				}
				break;
			case 177: 
				{
					TagCompound info = getBlockEntityData( x, y, z);
					int color = 0;
					try {
						if(info!=null) {
							color = info.getInteger("Base");
						}
					}catch (UnexpectedTagTypeException | TagNotFoundException e) {
						System.out.println("bad tile entity at "+x+","+y+","+z);
						e.printStackTrace();
					}
					
					//need to invert the value as banners originally they were stored inverted, but no longer in 1.13 (?)  Patterns may also need to be inverted, depending on minecraft version?  Leaving as an option for now.  
					if((this.bannerHandling & BANNER_CONVERT_BASE) != 0) {
						color = 15 - color;
					}
					
					block.type = S2CB.BLOCK_TYPES.get(getColorName(color) + "_wall_banner");
					block.properties = "facing=" + getBlockDirection(blockdata);
					
					if(info!=null && info.getTags().containsKey("Patterns")) {
						List<TagCompound> patternlist;
						try {
							patternlist = info.getList("Patterns", TagCompound.class);
							for(TagCompound pat : patternlist ) {
								if(pat.getTags().containsKey("Color")) {
									TagInteger colortag = (TagInteger) pat.getTag("Color");
									color = colortag.getValue();
									if((this.bannerHandling & BANNER_CONVERT_PATTERN) != 0) {
										color = 15 - color;
									}
									colortag.setValue(color);  
								}
							}
						} catch (UnexpectedTagTypeException | TagNotFoundException e) {
							System.out.println("bad tile entity at "+x+","+y+","+z);
						}
					}
				}
				break;
			case 179:
				switch(blockdata) {
				case 1: name = "chiseled_"; break;
				case 2: name = "cut_"; break;
				}
				name += "red_sandstone";
				block.type = S2CB.BLOCK_TYPES.get(name);
				break;
			case 180:
				block.type = S2CB.BLOCK_TYPES.get("red_sandstone_stairs");
				block.properties = getStairProps(blockdata, x, y, z, blocks, w, h, l, bdata);
				break;
			case 181:
				name = "red_sandstone_slab"; 
				switch(blockdata) {
				case 1: name="purpur_slab"; break;
				case 2: name="prismarine_slab"; break;
				case 3: name="prismarine_brick_slab"; break;
				case 4: name="dark_prismarine_slab"; break;
				case 8: name="smooth_red_sandstone"; break;
				}
				block.type = S2CB.BLOCK_TYPES.get(name);
				block.properties = "type=double";
				if(blockdata==8) block.properties="";
				break;
			case 182:
				name = "red_sandstone_slab"; 
				switch(blockdata  & 0x7) {
				case 1: name="purpur_slab"; break;
				case 2: name="prismarine_slab"; break;
				case 3: name="prismarine_brick_slab"; break;
				case 4: name="dark_prismarine_slab"; break;
				}
				block.type = S2CB.BLOCK_TYPES.get(name);
				block.properties = "type=" + (((blockdata & 0x8)>0)?"top":"bottom");
				break;
			case 183:
			case 184:
			case 185:
			case 186:
			case 187:
				block.type = S2CB.BLOCK_TYPES.get(getWoodType(blockId - 182,true) + "_fence_gate");
				{
					String facing = "south";
					switch(blockdata & 0x3) {
					case 1: facing = "west"; break;
					case 2: facing = "north"; break;
					case 3: facing = "east"; break;
					}
					block.properties = "facing=" + facing + ",open=" + Boolean.toString((blockdata & 0x4)>0);
				}
				break;
			case 188:
			case 189:
			case 190:
			case 191:
			case 192:
				block.type = S2CB.BLOCK_TYPES.get(getWoodType(blockId - 187,true) + "_fence");
				block.properties = getFenceProperties(blockdata,false,x,y,z,blocks,w,h,l);
				break;
			case 193:
			case 194:
			case 195:
			case 196:
			case 197:
				block.type = S2CB.BLOCK_TYPES.get(getWoodType(blockId - 192) + "_door");
				block.properties = getDoorProps(blockdata,x,y,z,bdata,w,h,l);
				break;
			case 198:
				block.type = S2CB.BLOCK_TYPES.get("end_rod");
				block.properties = "facing=" + getBlockDirection(blockdata);
				break;
			case 199:
				block.type = S2CB.BLOCK_TYPES.get("chorus_plant");
				block.properties = getChorusProperties(blockdata, x, y, z, blocks, w, h, l);
				break;
			case 200:
				block.type = S2CB.BLOCK_TYPES.get("chorus_flower");
				block.properties = "age=" + blockdata;
				break;
			case 202:
				name = "purpur_pillar";
				if(blockdata > 0) {
					String axis = "";
					switch(blockdata) {
					case 0: axis="axis=y"; break;
					case 4: axis="axis=x"; break;
					case 8: axis="axis=z"; break;
					}
					block.properties = axis;
				}
				block.type = S2CB.BLOCK_TYPES.get(name);
				break;
			case 203:
				block.type = S2CB.BLOCK_TYPES.get("purpur_stairs");
				block.properties = getStairProps(blockdata, x, y, z, blocks, w, h, l, bdata);
				break;
			case 204:
				block.type = S2CB.BLOCK_TYPES.get("purpur_slab");
				block.properties = "type=double";
				break;
			case 205:
				block.type = S2CB.BLOCK_TYPES.get("purpur_slab");
				block.properties = "type=" + (((blockdata)>0)?"top":"bottom");
				break;
			case 207:
				block.type = S2CB.BLOCK_TYPES.get("beetroots");
				block.properties = "age=" + blockdata;
				break;
			case 216:
				name = "bone_block";
				if(blockdata > 0) {
					String axis = "";
					switch(blockdata) {
					case 0: axis="axis=y"; break;
					case 4: axis="axis=x"; break;
					case 8: axis="axis=z"; break;
					}
					block.properties = axis;
				}
				block.type = S2CB.BLOCK_TYPES.get(name);
				break;
			case 218:
				block.type = S2CB.BLOCK_TYPES.get("observer");
				block.properties = "facing=" + getBlockDirection(blockdata) + 
						",powered=" + Boolean.toString((blockdata & 0x8)>0);
				break;
			case 219:
			case 220:
			case 221:
			case 222:
			case 223:
			case 224:
			case 225:
			case 226:
			case 227:
			case 228:
			case 229:
			case 230:
			case 231:
			case 232:
			case 233:
			case 234:
				block.type = S2CB.BLOCK_TYPES.get(getColorName(blockId - 219) + "_shulker_box");
				block.properties = "facing=" + getBlockDirection(blockdata);
				break;
			case 235:
			case 236:
			case 237:
			case 238:
			case 239:
			case 240:
			case 241:
			case 242:
			case 243:
			case 244:
			case 245:
			case 246:
			case 247:
			case 248:
			case 249:
			case 250:
				block.type = S2CB.BLOCK_TYPES.get(getColorName(blockId - 235) + "_glazed_terracotta");
				{
					String facing = "south";
					switch(blockdata & 0x3) {
					case 1: facing = "west"; break;
					case 2: facing = "north"; break;
					case 3: facing = "east"; break;
					}
					block.properties = "facing=" + facing;
				}
				break;
			case 251:
				block.type = S2CB.BLOCK_TYPES.get(getColorName(blockdata) + "_concrete");
				break;
			case 252:
				block.type = S2CB.BLOCK_TYPES.get(getColorName(blockdata) + "_concrete_powder");
				break;
			case 255:
				block.type = S2CB.BLOCK_TYPES.get("structure_block");
				{
					String mode = "data";
					switch(blockdata & 0x3) {
					case 1: mode = "save"; break;
					case 2: mode = "load"; break;
					case 3: mode = "corner"; break;
					}
					block.properties = "mode=" + mode;
				}
				break;
		}
		
		//add block entity data to the block
		TagCompound data = getBlockEntityData( x, y, z);
		if(data != null) {
			block.compound = new TagCompound("block");
			data.setName("nbt");
			block.compound.setTag(data);
		}
		if(block.properties == null) {
			block.properties = "";
		}
		
		if(S2CB.intern) {
			block.properties = block.properties.intern();
		}
		
		if(block.type == null) {
			//shouldn't happen
			System.out.println("Unknown Block Id: "+blockId);
			block.type = S2CB.BLOCK_TYPES.get("air");
		}
		
		
		return block;
	}

	private static int getBlockAt(int x, int y, int z,byte[] blocks, int w, int h, int l) {
		if( x>=0 && x<w && y>=0 && y<h && z>=0 && z<l) {
			int blockId = blocks[getCoord(x,y,z,w,h,l)];
			blockId = blockId>=0?blockId:256+((int)blockId);
			return blockId;
		}
		return -1;
	}
	
	private static int getDataAt(int x, int y, int z, byte[] bdata, int w, int h, int l) {
		if( x>=0 && x<w && y>=0 && y<h && z>=0 && z<l) {
			return bdata[getCoord(x,y,z,w,h,l)] & 0xf;
		}
		return -1;
	}
	
	public static int getCoord(int x, int y, int z, int w, int h, int l) {
		return y*w*l + z*w + x;
	}

	public static String getWoodType(int blockdata) {
		return getWoodType(blockdata,false);
	}
	
	public static String getWoodType(int blockdata,boolean fence) {
		switch(blockdata) {
		default: return "oak"; 
		case 1: return "spruce"; 
		case 2: return "birch"; 
		case 3: return "jungle"; 
		case 4: return fence?"dark_oak":"acacia"; 
		case 5: return fence?"acacia":"dark_oak"; 
		}
	}
	
	public static String getBlockDirection(int blockdata) {
		return getBlockDirection(blockdata, true);
	}
	
	public static String getBlockDirection(int blockdata, boolean upDown) {
		int dir = blockdata & 0x7;
		switch(dir) {
		default: return "north"; 
		case 0: if(upDown) { return "down"; } 
		case 1: if(upDown) { return "up"; }
		case 3: return "south"; 
		case 4: return "west"; 
		case 5: return "east"; 
		}
	}
	
	private int getLocation(int x, int y, int z) {
		return ((y)<<24) + ((z)<<12) + (x);
	}
	
	private void buildTileEntCache(List<TagCompound> tileEntities) {
		for(TagCompound te:tileEntities) {
    		
			int x=0,y=0,z=0;
    		try {
    			x = te.getInteger("x");
    			y = te.getInteger("y");
    			z = te.getInteger("z");
    			
    			tileEntCache.put(getLocation(x,y,z), te);
    		}catch (UnexpectedTagTypeException | TagNotFoundException e) {
				System.out.println("bad tile entity at "+x+","+y+","+z);
				e.printStackTrace();
			}
		}	
	}
	
	private TagCompound getBlockEntityData(int x, int y, int z) {
		
    	TagCompound te = tileEntCache.get(getLocation(x,y,z));
		try {
			if(te!=null && te.getInteger("x")==x && te.getInteger("y")==y && te.getInteger("z")==z /*&& te.getString("id").equalsIgnoreCase(blocktype)*/) {

				fixEntity(te);
				return te;
			}
		} catch (UnexpectedTagTypeException | TagNotFoundException e) {
			System.out.println("bad tile entity at "+x+","+y+","+z);
			e.printStackTrace();
		}
		return null;
	}
	
	public static String getColorName(int c) {
		switch(c) {
		default: return "white";
		case 1: return "orange";
		case 2: return "magenta";
		case 3: return "light_blue";
		case 4: return "yellow";
		case 5: return "lime";
		case 6: return "pink";
		case 7: return "gray";
		case 8: return "light_gray";
		case 9: return "cyan";
		case 10: return "purple";
		case 11: return "blue";
		case 12: return "brown";
		case 13: return "green";
		case 14: return "red";
		case 15: return "black";
		}
	}
	
	
	public static String getStairProps(int blockdata, int x, int y, int z, byte[] blocks, int w, int h, int l, byte[] bdata) {
		String prop = "facing=";
		switch(blockdata&0x3) {
		case 0: prop+="east"; break;
		case 1: prop+="west"; break;
		case 2: prop+="south"; break;
		default: prop+="north"; break;
		}
		prop += ",half=" + (((blockdata & 0x4)>0)?"top":"bottom");
	
		
		switch(blockdata&0x3) {
		case 0: {
			//back to east
			if( isStairBlock(getBlockAt(x, y, z+1, blocks, w, h, l)) &&
					isStairBlock(getBlockAt(x, y, z-1, blocks, w, h, l)) ) {
				//straight - can ignore;
				//prop += ",shape=straight";
			}else if( isStairBlock(getBlockAt(x+1, y, z, blocks, w, h, l)) ) {
				//there is a stair to the east and not to both sides
				int dir = getDataAt(x+1, y, z, bdata, w, h, l);
				if(dir == 3 && (!isStairBlock(getBlockAt(x, y, z+1, blocks, w, h, l)) || getDataAt(x,y,z+1,bdata,w,h,l) != 0) ) {
					//turn toward block behind us to the right
					prop += ",shape=outer_left";
				}
				if(dir == 2 && (!isStairBlock(getBlockAt(x, y, z-1, blocks, w, h, l)) || getDataAt(x,y,z-1,bdata,w,h,l) != 0) ) {
					//turn toward block behind us to the right
					prop += ",shape=outer_right";
				}
			}else if( isStairBlock(getBlockAt(x-1, y, z, blocks, w, h, l))) {
				//there is a stair to the west
				int dir = getDataAt(x-1, y, z, bdata, w, h, l);
				if(dir == 3 && (!isStairBlock(getBlockAt(x, y, z+1, blocks, w, h, l)) || getDataAt(x+1,y,z+1,bdata,w,h,l) != 0) ) {
					prop += ",shape=inner_left";
				}
				if(dir == 2 && (!isStairBlock(getBlockAt(x, y, z-1, blocks, w, h, l)) || getDataAt(x,y,z-1,bdata,w,h,l) != 0) ) {
					//turn toward block behind us to the right
					prop += ",shape=inner_right";
				}
			}
			break;
		}
		case 1: {
			//back to west
			if( isStairBlock(getBlockAt(x, y, z+1, blocks, w, h, l)) &&
					isStairBlock(getBlockAt(x, y, z-1, blocks, w, h, l)) ) {
				//straight - can ignore;
				//prop += ",shape=straight";
			}else if( isStairBlock(getBlockAt(x-1, y, z, blocks, w, h, l)) ) {
				//there is a stair to the west and not to both sides
				int dir = getDataAt(x-1, y, z, bdata, w, h, l);
				if(dir == 3 && (!isStairBlock(getBlockAt(x, y, z+1, blocks, w, h, l)) || getDataAt(x,y,z+1,bdata,w,h,l) != 0) ) {
					//turn toward block behind us to the right
					prop += ",shape=outer_right";
				}
				if(dir == 2 && (!isStairBlock(getBlockAt(x, y, z-1, blocks, w, h, l)) || getDataAt(x,y,z-1,bdata,w,h,l) != 0) ) {
					//turn toward block behind us to the right
					prop += ",shape=outer_left";
				}
			}else if( isStairBlock(getBlockAt(x+1, y, z, blocks, w, h, l))) {
				//there is a stair to the west
				int dir = getDataAt(x+1, y, z, bdata, w, h, l);
				if(dir == 3 && (!isStairBlock(getBlockAt(x, y, z+1, blocks, w, h, l)) || getDataAt(x+1,y,z+1,bdata,w,h,l) != 0) ) {
					prop += ",shape=inner_right";
				}
				if(dir == 2 && (!isStairBlock(getBlockAt(x, y, z-1, blocks, w, h, l)) || getDataAt(x,y,z-1,bdata,w,h,l) != 0) ) {
					//turn toward block behind us to the right
					prop += ",shape=inner_left";
				}
			}
			break;
		}
		case 2: {
			//back to south
			if( isStairBlock(getBlockAt(x+1, y, z, blocks, w, h, l)) &&
					isStairBlock(getBlockAt(x-1, y, z, blocks, w, h, l)) ) {
				//straight - can ignore;
				//prop += ",shape=straight";
			}else if( isStairBlock(getBlockAt(x, y, z-1, blocks, w, h, l)) ) {
				//there is a stair to the north and not to both sides
				int dir = getDataAt(x, y, z-1, bdata, w, h, l);
				if(dir == 1 && (!isStairBlock(getBlockAt(x-1, y, z, blocks, w, h, l)) || getDataAt(x+1,y,z,bdata,w,h,l) != 2) ) {
					//turn toward block behind us to the right
					prop += ",shape=inner_right";
				}
				if(dir == 0 && (!isStairBlock(getBlockAt(x+1, y, z, blocks, w, h, l)) || getDataAt(x-1,y,z,bdata,w,h,l) != 2) ) {
					//turn toward block behind us to the right
					prop += ",shape=inner_left";
				}
			}else if( isStairBlock(getBlockAt(x, y, z+1, blocks, w, h, l))) {
				//there is a stair to the south
				int dir = getDataAt(x, y, z+1, bdata, w, h, l);
				if(dir == 1 && (!isStairBlock(getBlockAt(x-1, y, z, blocks, w, h, l)) || getDataAt(x+1,y,z,bdata,w,h,l) != 2) ) {
					prop += ",shape=outer_right";
				}
				if(dir == 0 && (!isStairBlock(getBlockAt(x+1, y, z, blocks, w, h, l)) || getDataAt(x-1,y,z,bdata,w,h,l) != 2) ) {
					//turn toward block behind us to the right
					prop += ",shape=outer_left";
				}
			}
			break;
		}
		case 3: {
			//back to north
			if( isStairBlock(getBlockAt(x+1, y, z, blocks, w, h, l)) &&
					isStairBlock(getBlockAt(x-1, y, z, blocks, w, h, l)) ) {
				//straight - can ignore;
				//prop += ",shape=straight";
			}else if( isStairBlock(getBlockAt(x, y, z+1, blocks, w, h, l)) ) {
				//there is a stair to the south and not to both sides
				int dir = getDataAt(x, y, z+1, bdata, w, h, l);
				if(dir == 1 && (!isStairBlock(getBlockAt(x-1, y, z, blocks, w, h, l)) || getDataAt(x+1,y,z,bdata,w,h,l) != 3) ) {
					//turn toward block behind us to the right
					prop += ",shape=inner_left";
				}
				if(dir == 0 && (!isStairBlock(getBlockAt(x+1, y, z, blocks, w, h, l)) || getDataAt(x-1,y,z,bdata,w,h,l) != 3) ) {
					//turn toward block behind us to the right
					prop += ",shape=inner_right";
				}
			}else if( isStairBlock(getBlockAt(x, y, z-1, blocks, w, h, l))) {
				//there is a stair to the north
				int dir = getDataAt(x, y, z-1, bdata, w, h, l);
				if(dir == 1 && (!isStairBlock(getBlockAt(x-1, y, z, blocks, w, h, l)) || getDataAt(x+1,y,z,bdata,w,h,l) != 3) ) {
					prop += ",shape=outer_left";
				}
				if(dir == 0 && (!isStairBlock(getBlockAt(x+1, y, z, blocks, w, h, l)) || getDataAt(x-1,y,z,bdata,w,h,l) != 3) ) {
					//turn toward block behind us to the right
					prop += ",shape=outer_right";
				}
			}
			break;
		}
		}
		
		return prop;
	}
	
	public static boolean isStairBlock(int block) {
		switch(block) {
		default: return false;
		case 53:
		case 67:
		case 108:
		case 109:
		case 114:
		case 128:
		case 134:
		case 135:
		case 136:
		case 156:
		case 163:
		case 164:
		case 180:
		case 203:
			return true;
		}
	}
	
	public static boolean isRedstonePart(int block) {
		switch(block) {
		default: return false;
		case 55:
		case 69:
		case 70:
		case 72:
		case 75:
		case 76:
		case 77:
		case 93:
		case 94:
		case 131:
		case 143:
		case 146:
		case 147:
		case 148:
		case 149:
		case 150:
		case 151:
		case 152:
		case 178:
			return true;
		}
	}
	
	public static String getDoorProps(int blockdata, int x, int y, int z, byte[] bdata, int w, int h, int l) {
		boolean top = (blockdata & 0x8) > 0;
		int topdata = 8;
		int bottomdata = 0;
		if(top) {
			topdata = blockdata;
			bottomdata = getDataAt(x,y-1,z,bdata,w,h,l);
		}else {
			topdata = getDataAt(x,y+1,z,bdata,w,h,l);
			bottomdata = blockdata;
		}
		boolean lefthinge = (topdata & 0x1) == 0;
		boolean open = (bottomdata & 0x4) > 0;
		boolean powered = (topdata & 0x2) > 0;
		String props = "facing=";
		switch(bottomdata & 0x3) {
		default: props += "east"; break;
		case 1: props += "south"; break;
		case 2: props += "west"; break;
		case 3: props += "north"; break;
		}
		props += ",half=" + (top?"upper":"lower");
		props += ",hinge=" + (lefthinge?"left":"right");
		props += ",open=" + (open?"true":"false");
		props += ",powered=" + (powered?"true":"false");
		return props;
	}
	
	public static String getFenceProperties(int blockdata,boolean nether, int x, int y, int z, byte[] blocks, int w, int h, int l) {
		
		int northblock = getBlockAt(x,y,z-1,blocks,w,h,l);
		int southblock = getBlockAt(x,y,z+1,blocks,w,h,l);
		int eastblock = getBlockAt(x+1,y,z,blocks,w,h,l);
		int westblock = getBlockAt(x-1,y,z,blocks,w,h,l);

		//not actually correct = only connect to other fences or gates, except nether fence only connects to nether fence or any gate
		boolean north = (nether && (northblock == 113)) || 
				(!nether && (northblock == 85 || northblock == 188 || northblock == 189 || northblock == 190 || northblock == 191 || northblock == 192) ) ||
						northblock == 107 || northblock == 183 || northblock == 184 || northblock == 185 || northblock == 186 || northblock == 187; 
		boolean south = (nether && (southblock == 113)) || 
				(!nether && (southblock == 85 || southblock == 188 || southblock == 189 || southblock == 190 || southblock == 191 || southblock == 192) ) || 
						southblock == 107 || southblock == 183 || southblock == 184 || southblock == 185 || southblock == 186 || southblock == 187; 
		boolean east = (nether && (eastblock == 113)) || 
				(!nether && (eastblock == 85 || eastblock == 188 || eastblock == 189 || eastblock == 190 || eastblock == 191 || eastblock == 192) ) || 
						eastblock == 107 || eastblock == 183 || eastblock == 184 || eastblock == 185 || eastblock == 186 || eastblock == 187; 
		boolean west = (nether && (westblock == 113)) || 
				(!nether && (westblock == 85 || westblock == 188 || westblock == 189 || westblock == 190 || westblock == 191 || westblock == 192) ) || 
						westblock == 107 || westblock == 183 || westblock == 184 || westblock == 185 || westblock == 186 || westblock == 187; 
		
		//more correct, but not totally - connect to non-transparent blocks (in particular, they will connect to the back of stairs, but I'm not implementing that currently
		north = north || ((materialIssue[northblock>-1?northblock:0]) & tr) == 0;
		south = south || ((materialIssue[southblock>-1?southblock:0]) & tr) == 0;
		east = east || ((materialIssue[eastblock>-1?eastblock:0]) & tr) == 0;
		west = west || ((materialIssue[westblock>-1?westblock:0]) & tr) == 0;
		
		return "north=" + Boolean.toString(north) + ",south=" + Boolean.toString(south) +
				",east=" + Boolean.toString(east) + ",west=" + Boolean.toString(west); 
	}
	
	public static String getPaneBarProperties(int blockdata, int x, int y, int z, byte[] blocks, int w, int h, int l) {
		//very similar to above, but iron bars and glass panes connect to each other and to glass blocks of all colors in addition to all non-transparent
		
		int northblock = getBlockAt(x,y,z-1,blocks,w,h,l);
		int southblock = getBlockAt(x,y,z+1,blocks,w,h,l);
		int eastblock = getBlockAt(x+1,y,z,blocks,w,h,l);
		int westblock = getBlockAt(x-1,y,z,blocks,w,h,l);

		//not actually correct = only connect to iron bars, panes, and full glass blocks
		boolean north = (northblock == 101 || northblock == 102 || northblock == 160 || northblock == 20 || northblock == 95);
		boolean south = (southblock == 101 || southblock == 102 || southblock == 160 || southblock == 20 || southblock == 95);
		boolean east = (eastblock == 101 || eastblock == 102 || eastblock == 160 || eastblock == 20 || eastblock == 95);
		boolean west = (westblock == 101 || westblock == 102 || westblock == 160 || westblock == 20 || westblock == 95);
		
		//more correct, but not totally - connect to non-transparent blocks (in particular, they will connect to the back of stairs, but I'm not implementing that currently
		north = north || ((materialIssue[northblock>-1?northblock:0]) & tr) == 0;
		south = south || ((materialIssue[southblock>-1?southblock:0]) & tr) == 0;
		east = east || ((materialIssue[eastblock>-1?eastblock:0]) & tr) == 0;
		west = west || ((materialIssue[westblock>-1?westblock:0]) & tr) == 0;
		
		return "north=" + Boolean.toString(north) + ",south=" + Boolean.toString(south) +
				",east=" + Boolean.toString(east) + ",west=" + Boolean.toString(west);
	}

	public static String getWallProperties(int blockdata, int x, int y, int z, byte[] blocks, int w, int h, int l) {
		int northblock = getBlockAt(x,y,z-1,blocks,w,h,l);
		//other walls or fence gates
		boolean north = (northblock == 139 || northblock == 107 || northblock == 183 || northblock == 184 || northblock == 185 || northblock == 186 || northblock == 187); 
		int southblock = getBlockAt(x,y,z+1,blocks,w,h,l);
		boolean south = (southblock == 139 || southblock == 107 || southblock == 183 || southblock == 184 || southblock == 185 || southblock == 186 || southblock == 187); 
		int eastblock = getBlockAt(x+1,y,z,blocks,w,h,l);
		boolean east = (eastblock == 139 || eastblock == 107 || eastblock == 183 || eastblock == 184 || eastblock == 185 || eastblock == 186 || eastblock == 187); 
		int westblock = getBlockAt(x-1,y,z,blocks,w,h,l);
		boolean west = (westblock == 139 || westblock == 107 || westblock == 183 || westblock == 184 || westblock == 185 || westblock == 186 || westblock == 187); 
		int upblock = getBlockAt(x,y+1,z,blocks,w,h,l);
		boolean up = (upblock == 139) || ((north || south || east || west) == false );
		
		//more correct, but not totally - connect to non-transparent blocks (in particular, they will connect to the back of stairs, but I'm not implementing that currently
				north = north || ((materialIssue[northblock>-1?northblock:0]) & tr) == 0;
				south = south || ((materialIssue[southblock>-1?southblock:0]) & tr) == 0;
				east = east || ((materialIssue[eastblock>-1?eastblock:0]) & tr) == 0;
				west = west || ((materialIssue[westblock>-1?westblock:0]) & tr) == 0;
		
		return "north=" + Boolean.toString(north) + ",south=" + Boolean.toString(south) +
				",east=" + Boolean.toString(east) + ",west=" + Boolean.toString(west) +
				",up=" + Boolean.toString(up); 
	}
	
	public static String getChorusProperties(int blockdata, int x, int y, int z, byte[] blocks, int w, int h, int l) {
		int northblock = getBlockAt(x,y,z-1,blocks,w,h,l);
		boolean north = (northblock == 199); 
		int southblock = getBlockAt(x,y,z+1,blocks,w,h,l);
		boolean south = (southblock == 199);
		int eastblock = getBlockAt(x+1,y,z,blocks,w,h,l);
		boolean east = (eastblock == 199);
		int westblock = getBlockAt(x-1,y,z,blocks,w,h,l);
		boolean west = (westblock == 199);
		int upblock = getBlockAt(x,y+1,z,blocks,w,h,l);
		boolean up = (upblock == 199);
		int downblock = getBlockAt(x,y-1,z,blocks,w,h,l);
		boolean down = (downblock == 199) || (downblock == 121); //end stone
		return "north=" + Boolean.toString(north) + ",south=" + Boolean.toString(south) +
				",east=" + Boolean.toString(east) + ",west=" + Boolean.toString(west) +
				",up=" + Boolean.toString(up) + ",down=" + Boolean.toString(down); 
	}
	
	
	private void fixEntities(List<TagCompound> entities,boolean mob) {
		//fix entities - correcting id name changes, and other issues so that we can handle them in 1.13
		
		try {
			for(TagCompound ent:entities) {
				
				fixEntity(ent,mob);
				
			}
			
		}catch (UnexpectedTagTypeException | TagNotFoundException e) {
			System.out.println("bad entity! ");
			e.printStackTrace();
		}
		
	}
	
	private void fixEntity(TagCompound ent) throws UnexpectedTagTypeException, TagNotFoundException {
		fixEntity(ent,false);
	}


	private void fixEntity(TagCompound ent,boolean mob) throws UnexpectedTagTypeException, TagNotFoundException {
		String name = "";
		TagString itemid = null;
		if(ent.getTags().get("id") instanceof TagString) {
			itemid = (TagString) ent.getTag("id");
			name = itemid.getValue();
		}else if(ent.getTags().get("id") instanceof TagShort) {
			TagShort iid = (TagShort) ent.getTag("id");
			int val = iid.getValue();
			if(val < 256) {
				name = materials[val];
			}else if(val < 448) {
				name = items[val - 256];
			}else if (val >= 2256 && val <= 2267) {
				switch(val - 2256) {
				case 0: name = "music_disc_13"; break;
				case 1: name = "music_disc_cat"; break;
				case 2: name = "music_disc_block"; break;
				case 3: name = "music_disc_chirp"; break;
				case 4: name = "music_disc_far"; break;
				case 5: name = "music_disc_mall"; break;
				case 6: name = "music_disc_mellohi"; break;
				case 7: name = "music_disc_stal"; break;
				case 8: name = "music_disc_strad"; break;
				case 9: name = "music_disc_ward"; break;
				case 10: name = "music_disc_11"; break;
				case 11: name = "music_disc_wait"; break;
				}
			}
			ent.removeTag(iid);
			itemid = new TagString("id",name);
			ent.setTag(itemid);
		}
		
		int damage = 0;
		if(ent.getTags().containsKey("Damage")) {
			damage = ent.getShort("Damage");
		}
		
		if(name.startsWith("minecraft:")) {
			name = name.substring(10);
		}
		
		if(S2CB.entityNameMap.keySet().contains(name) && itemid != null) {
			name = S2CB.entityNameMap.get(name);
			itemid.setValue(name);
		}
		
		if(!mob && itemid != null) {
			Item item = new Item(name,damage);
			if(itemMapping.containsKey(item)) {
				name = itemMapping.get(item);
				itemid.setValue("minecraft:" + name);
			}
			
		}else {
			if(name.contains("shulker")) {
				ent.removeTag("APX");
				ent.removeTag("APY");
				ent.removeTag("APZ");
			}
		}
		
		if(name.toLowerCase().contains("banner")) {
			
			int color = damage;
			//need to invert the value as banners originally they were stored inverted, but no longer in 1.13 (?)  Patterns may also need to be inverted, depending on minecraft version?  Leaving as an option for now.  
			if((this.bannerHandling & BANNER_CONVERT_BASE) != 0) {
				color = 15 - color;
			}
			
			if(ent.getTag("Base")==null) {
				ent.setTag(new TagInteger("Base",color));
			}
			// iterate through patterns fixing colors.  tag(compound)->BlockEntityTag(compound)->Patterns(list)->compound->Color(int)
			if(ent.getTags().containsKey("tag")) {
				TagCompound tag = ent.getCompound("tag");
				if(tag.getTags().containsKey("BlockEntityTag")) {
					TagCompound bet = tag.getCompound("BlockEntityTag");
					if(bet.getTags().containsKey("Patterns")) {
						List<TagCompound> patternlist = bet.getList("Patterns", TagCompound.class);
						for(TagCompound pat : patternlist ) {
							if(pat.getTags().containsKey("Color")) {
								TagInteger colorTag = (TagInteger) pat.getTag("Color");
								color = colorTag.getValue();
								if((this.bannerHandling & BANNER_CONVERT_PATTERN) != 0) {
									color = 15 - color;
								}
								colorTag.setValue(color); //need to invert the value as banners originally they were stored inverted, but no longer in 1.13 
							}
						}
					}
				}
			}
		}
		
		

		
		if(ent.getTags().containsKey("Motive")) {
			TagString motive = (TagString)ent.getTag("Motive");
			String m = motive.getValue();
			if(m.equalsIgnoreCase("burningskull")) {
				m = "burning_skull";
			}else if(m.equalsIgnoreCase("skullandroses")) {
				m = "skull_and_roses";
			}else if(m.equalsIgnoreCase("donkeykong")){
				m = "donkey_kong";
			}
			if(!m.contains("minecraft:")) {
				motive.setValue("minecraft:"+m.toLowerCase());
			}
		}
		
		if(name.contains("item_frame")) {
			if(ent.getTags().containsKey("Facing")) {
				TagByte facing = (TagByte)ent.getTag("Facing");
				//1.12 - 0 is south, 1 is west, 2 is north, and 3 is east.
				//1.13 - 3 is south, 4 is west, 2 is north, and 5 is east.  With 1 being up and 0 being down.
				
				switch(facing.getValue()) {
				case 0: facing.setValue((byte) 3); break;
				case 1: facing.setValue((byte) 4); break;
				case 2: facing.setValue((byte) 2); break;
				case 3: facing.setValue((byte) 5); break;
				}
			}
		}
		
		if(ent.getTags().containsKey("Pos")) {
			TagList tag = (TagList)ent.getTag("Pos");
			List<ITag> list = tag.getTags();
			if(list.get(0) instanceof TagDouble) {
				//this is a valid position list.  We need to update the position because running commands in a command block causes a 1/2 block offset (I think).
				TagDouble x = (TagDouble)list.get(0);
				x.setValue(x.getValue() - 0.5);
				TagDouble z = (TagDouble)list.get(2);
				z.setValue(z.getValue() - 0.5);
			}
		}
		
		
		if(ent.getTags().containsKey("Items")) {
			//has an inventory of some sort - chest, shulker box, furnace, etc. - need to fix old item ids
			List<TagCompound> list = ent.getList("Items", TagCompound.class);
			
			for(TagCompound it:list) {
				if(it.getTags().containsKey("id")) {
					
					fixEntity(it);
					
				}
			}
		}
		
		if(ent.getTags().containsKey("Item")) {
			ITag it = ent.getTag("Item");
			
			if(it instanceof TagCompound) {
				TagCompound itm =(TagCompound)it;
			
				fixEntity(itm);
			} else if(it instanceof TagString) {
				TagString itm = (TagString)it;
				String itemstr = itm.getValue();
				if(S2CB.entityNameMap.keySet().contains(itemstr)) {
					itm.setValue(S2CB.entityNameMap.get(itemstr));
				}
			}
			
		}
		
		if(ent.getTags().containsKey("DecorItem")) {
			//llama carpet
			ITag it = ent.getTag("DecorItem");
			
			if(it instanceof TagCompound) {
				TagCompound itm =(TagCompound)it;
			
				fixEntity(itm);
			} else if(it instanceof TagString) {
				TagString itm = (TagString)it;
				String itemstr = itm.getValue();
				if(S2CB.entityNameMap.keySet().contains(itemstr)) {
					itm.setValue(S2CB.entityNameMap.get(itemstr));
				}
			}
			
		}
		
		if(ent.getTags().containsKey("carried")) {
			//enderman carried block - only converting the block id for now - state shouldn't matter (much)
			int block = ent.getShort("carried");
			ent.removeTag("carried");
			TagCompound carried = new TagCompound("carriedBlockState");
			TagString nametag = new TagString("Name",materials[block]);
			carried.setTag(nametag);
			ent.setTag(carried);
		}
		
		
		
		if(ent.getTags().containsKey("ArmorItems")) {
			List<TagCompound> list = ent.getList("ArmorItems", TagCompound.class);
			//TagList armorItems = ent.getTag("ArmorItems", TagList.class);
			for(TagCompound armor:list) {
				try {
					if(armor.getTags().isEmpty()) {
						//armorItems.removeTag(armor); //just skip an empty item - it just an empty armor slot
						continue;
					}
					
					fixEntity(armor);
					
				}catch (UnexpectedTagTypeException | TagNotFoundException e) {
					System.out.println("bad armor item! ");
					e.printStackTrace();
				}
			}
		}
		
		
		if(ent.getTags().containsKey("HandItems")) {
			List<TagCompound> list = ent.getList("HandItems", TagCompound.class);
			//TagList handItems = ent.getTag("HandItems", TagList.class);
			for(TagCompound hand:list) {
				try {
					if(hand.getTags().isEmpty()) {
						//handItems.removeTag(hand);//just skip an empty item - it just an empty hand slot (left or right hand is empty)
						continue;
					}
					
					fixEntity(hand);
				}catch (UnexpectedTagTypeException | TagNotFoundException e) {
					System.out.println("bad hand item! ");
					e.printStackTrace();
				}
			}
		}
		
		if(ent.getTags().containsKey("Equipment")) {
			//this isn't correct, but main program will convert into hand and armor items, but we still need to fix any name changes (specifically skull)
			List<TagCompound> list = ent.getList("Equipment", TagCompound.class);
			for(TagCompound armor:list) {
				try {
					if(armor.getTags().isEmpty()) {
						//just skip an empty item - it just an empty armor slot
						continue;
					}
					
					fixEntity(armor);
					
				}catch (UnexpectedTagTypeException | TagNotFoundException e) {
					System.out.println("bad armor item! ");
					e.printStackTrace();
				}
			}
		}
		
		
		if(name.equals("spawn_egg")) {
			if(ent.getTags().containsKey("tag")) {
				TagCompound tag = ent.getCompound("tag");
				if(tag.getTags().containsKey("EntityTag")) {
					TagCompound et = tag.getCompound("EntityTag");
					String entname = et.getString("id");
					if(entname.startsWith("minecraft:")) {
						entname = entname.substring(10);
					}
					if(S2CB.entityNameMap.keySet().contains(entname)) {
						entname = S2CB.entityNameMap.get(entname);
					}
					TagString itmid = ent.getTag("id",TagString.class);
					itmid.setValue("minecraft:"+entname.toLowerCase()+"_spawn_egg");
					ent.removeTag("tag");
				}
			}
		}
		
		if(ent.getTag("tag")!=null) {
			
			TagCompound tag = ent.getCompound("tag");
			
			if(tag.getTags().containsKey("ench")) {
				// tag in now "Enchantment" and now the value is a string
				try {
					TagList ench = (TagList) tag.getTag("ench");
					tag.removeTag(ench);
					ench.setName("Enchantments");
					ench.setParent(null);
					tag.setTag(ench);//apparently setting the name removes it, so need to add it back, but first we needed to set the parent to null or it wouldn't add back correctly
					List<TagCompound> enchs = ench.getTags(TagCompound.class);
					for(TagCompound en : enchs) {
						if(en.getTags().containsKey("id") && en.getTag("id") instanceof TagShort ) {
							short idbyte = en.getShort("id");
							en.removeTag("id");
							String enchstr = enchantments.get(new Integer(idbyte));
							TagString newid = new TagString("id",enchstr);
							en.setTag(newid);
						}
					}
					
				}catch (Exception e) {
					System.out.println("bad enchanted item! ");
					e.printStackTrace();
				}
			}
			
			if(tag.getTags().containsKey("StoredEnchantments")) {
				//similar to above
				try {
					List<TagCompound> enchs = tag.getList("StoredEnchantments",TagCompound.class);
					for(TagCompound en : enchs) {
						if(en.getTags().containsKey("id") && en.getTag("id") instanceof TagShort ) {
							short idbyte = en.getShort("id");
							en.removeTag("id");
							String enchstr = enchantments.get(new Integer(idbyte));
							TagString newid = new TagString("id",enchstr);
							en.setTag(newid);
						}
					}
					
				}catch (Exception e) {
					System.out.println("bad enchanted book! ");
					e.printStackTrace();
				}
			}
			
			fixEntity(tag); //to handle display or custom name tags inside the tag tag
		}
		
		if(ent.getTags().containsKey("CustomName")) {
			//custom names need to be quoted or in JSON text format
			ITag cNameTag = ent.getTag("CustomName");
			if(cNameTag instanceof TagString) {
				TagString cname = (TagString)cNameTag;
				String cNameStr = cname.getValue();
				if(!cNameStr.startsWith("\"")) {
					cname.setValue("\""+cNameStr+"\"");
				}
			}
		}
		
		
		if(ent.getTags().containsKey("Riding")) {
			fixEntity(ent.getCompound("Riding"),true);
		}
		if(ent.getTags().containsKey("Passengers")) {
			List<TagCompound> pass = ent.getList("Passengers", TagCompound.class);
			for(TagCompound e:pass) {
				fixEntity(e,true);
			}
		}
		
		if(ent.getTags().containsKey("display") && ent.getTag("display") instanceof TagCompound) {
			TagCompound display = ent.getCompound("display");
			if(display.getTags().containsKey("Name") && display.getTag("Name") instanceof TagString) {
				TagString str = display.getTag("Name", TagString.class);
				String s = str.getValue();
				if(!s.startsWith("\"")) {
					str.setValue("\""+s+"\"");
				}
			}
			if(display.getTags().containsKey("LocName") && display.getTag("LocName") instanceof TagString) {
				TagString str = display.getTag("LocName", TagString.class);
				String s = str.getValue();
				if(!s.startsWith("\"")) {
					str.setValue("\""+s+"\"");
				}
			}
			
		}
		
		if(ent.getTags().containsKey("BlockEntityTag")) {
			TagCompound bet = ent.getCompound("BlockEntityTag");
		
			fixEntity(bet, false);
		}
		
	}
}


