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

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Stack;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkEvent.EventType;
import javax.swing.event.HyperlinkListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringEscapeUtils;

import com.evilco.mc.nbt.error.TagNotFoundException;
import com.evilco.mc.nbt.error.UnexpectedTagTypeException;
import com.evilco.mc.nbt.stream.NbtInputStream;
import com.evilco.mc.nbt.tag.*;


//TODO: test mob placement is correct (1/2 block off?)  put bunch of chicken in 1x1 glass   -  done, the 0.5 x,z seems to help, but not completely preventing the chickens from escaping...

//TODO: efficiency improvement: do an initial pass that attempts to find the largest fills possible, even if all the blocks are not the same type (to some ratio, for example only 50% of the blocks are the same type).  For example, a stone brick wall with a few cracked stone bricks and a door in it would still likely be more efficient as a single large fill of stone bricks, with later passes (i.e. the current 2 passes) adding in the cracked stone brick blocks and the door, instead of the patchwork of fills it would currently be. Would have to give air blocks an additional penalty (as they are currently 'free' as we do an initial big air fill currently, so that would be new commands not previously needed (maybe count them as 5 blocks? would also need to somehow mark them for placing). Assuming the fill correctly sets some percentage (50%?) of the blocks in the area, it is beneficial.
//the above todo is done in doBigBlocks, but can likely be optimized somehow.  Currently takes a long time to run.  The fill is done if correct blocks - (air needing to be fixed * 2) > 5 . 

/**
 * S2CB - Schematic To Command Block
 * 
 * An application for converting Minecraft .schematic files (unofficial file format for saving parts of worlds used by tools like MCEdit)
 * to a series of one or more command block commands which will re-create the schematic as closely as possible. .NBT saved structure files
 * supported since 1.12
 * 
 * Version 1.13a for Minecraft 1.13 - For the moment no longer supporting .schematic files, .nbt saved structure files supported.
 * Well, Minecraft 1.13 happened.  This changed a lot!   
 * Since data values aren't a thing anymore (replaced with block state), .schematic files don't really make sense to support anymore.
 * So, for the moment, we are only supporting .nbt structure files.
 * I would like to support .schematic files, but I would need to find, or write, code that converts .schematic files to structure .nbt files.
 * 
 * Version 1.13b for Minecraft 1.13.(0-2) - .schematic files are supported again!
 * Wrote a conversion routine to convert .schematic files to something close to the .nbt structure format 
 * (or at least my internal representation of the .nbt format)
 * Learned a lot about the .structure format, or at least the block IDs and Block Data that was used before 1.13.
 * Like, wood is normally in the order: oak, spruce, birch, jungle, acacia, dark oak, except for fences and fence gates, which swap the order of acacia and dark oak.
 * Also banner colors were the reverse of everything else (like the color on a banner was 15 - <equivalent wool color>. (wool, glass, terracotta, etc. all used the same numbers for the same colors).
 * Now banners seem to be changed to be the same as everything else (at least the pattern colors as the base color is now in the banner name (red_wall_banner)
 * 
 * Version 1.13c for Minecraft 1.13.(0-2) 
 * A bunch of minor fixes.  Sugar cane fixed as well as nether portals. Added an option to include projectiles (I found a schematic using tipped arrows bouncing on slime to produce particle effects for a fountain)
 * A bunch of fixes for NBT tags of blocks and entities, especially custom named items / mobs in the inventory of block / being worn by mobs / or passenger of another entity
 * Fixed import of experience orbs (name changed from xp_orb). 
 * 
 * @author Brian Risinger  aka TroZ
 *
 */
@SuppressWarnings("serial")
public class S2CB extends JFrame {
	
	private static final String programVersion = "1.13c";
	private static final String minecraftVersion = "1.13";
	
	static final String PACK_MCMETA = "{\r\n" + 
			"   \"pack\":{\r\n" + 
			"      \"pack_format\":3,\r\n" + 
			"      \"description\":\"S2CB Schematic to Command Block - Spawn Structure: %DESC%  - Size: %w%,%h%,%l%  Offset: %ox%,%oy%,%oz%\"\r\n" + 
			"   }\r\n" + 
			"}";
	

	private static final int MAX_OFFSET_H = 1024;
	private static final int MAX_OFFSET_V = 255;
	
	//private static final int MAXCOMMANDLENGTH = 32500; //?  Thought it was 32767, but it seems to run out at 32500
	private static final int MAXMAINCOMMANDLENGTH = 32000;
	private static final int MAXMAINCOMMANDLENGTHSAFE = 30000;
	private int maxMainCommandLength = MAXMAINCOMMANDLENGTH;
	private static final int MAXFILLSIZE = 32768; // 32 * 32 * 32
	
	private static final int MINCLONESIZE = 3; //side length of minimum clone area
	private static final double CLONEMINBLOCKPERCENT = 0.4;
	
	private static final byte DONE_NOTDONE 	= 0;//block not encoded, or is air
	private static final byte DONE_FORCEAIR 	= 1;//block is air that needs to be encoded (they normally aren't as area is assumed clear, or we have automatically cleared it.
	private static final byte DONE_DONE 		= 127;//block is encoded
	
	static final boolean intern = false;
	
	private JFileChooser chooser = new JFileChooser();
	private static final String[] options = {"NONE","Dirt", "Grass", "Stone", "Cobblestone", "Sandstone", "Glass", "Barrier"};
	private JComboBox<String> base = new JComboBox<String>(options);
	private String[] BASE_BLOCK = {"","dirt","grass_block","stone","cobblestone","sandstone","glass","barrier"};
	private static final String[] optMoreCmds = {"Same Cmd Block","North No Space", "North One Space", "West No Space", "West One Space", "East No Space", "East One Space", "South No Space", "South One Space", "Minecart Command Blocks"}; //take care changing this order - the order is hardcoded in places - sorry
	private JComboBox<String> moreCmds = new JComboBox<String>(optMoreCmds);
	private static final int[] moreCmdsX = 		{0, 0, 0, 1, 2,-1,-2, 0, 0, 0};
	private static final int[] moreCmdsZ = 		{0, 1, 2, 0, 0, 0, 0,-1,-2, 0};
	private static final int[] newLineCmdsX = 	{0,-1,-1, 0, 0, 0, 0,-1,-1, 0};
	private static final int[] newLineCmdsZ = 	{0, 0, 0,-1,-1,-1,-1, 0, 0, 0};
	private static final int newLineCmdsY = 3;
	private static final String[] outputTypes = {"Command Block", "Data Pack"};
	private JComboBox<String> outputType = new JComboBox<String>(outputTypes);
	private String[] oldBannerOptions = {"No Conversion","Convert Base Color Only","Convert Pattern Colors Only","Convert All Colors"};
	private JComboBox<String> oldBannerConversion = new JComboBox<String>(oldBannerOptions);
	
	private JCheckBox quiet = new JCheckBox("Quiet");
	private JCheckBox clear = new JCheckBox("Clear");
	private JCheckBox chain = new JCheckBox("Chain");
	private JCheckBox noDangerousBlocks = new JCheckBox("No Dangerous Blocks");
	private JCheckBox removeBarriers = new JCheckBox("Remove Barriers");
	private JCheckBox removeEMobs = new JCheckBox("No Monsters");
	private JCheckBox removeMobs = new JCheckBox("No Mobs");
	private JCheckBox removeProjectiles = new JCheckBox("No Projectiles");
	private JCheckBox ignoreWirePower = new JCheckBox("Ignore Wire Power");
	private JCheckBox complexRails = new JCheckBox("Complex Rails");
	private JCheckBox minimizeWater = new JCheckBox("Min Water");
	private JCheckBox minimizeEntities = new JCheckBox("Min Entities");
	private JCheckBox serverSafe = new JCheckBox("Server Safe");
	private JCheckBox hollowOut = new JCheckBox("Hollow Out");
	private JCheckBox limitDistance = new JCheckBox("Limit Cmd Distance");
	private JCheckBox imperfectFills = new JCheckBox("Imperfect Fills");
	private JCheckBox checkClones = new JCheckBox("Clone Areas");
	
	private JLabel offsetLabel = new JLabel("Build Offset:");
	private JSpinner offsetX = new JSpinner(new SpinnerNumberModel(0,-MAX_OFFSET_H,MAX_OFFSET_H,1));
	private JSpinner offsetY = new JSpinner(new SpinnerNumberModel(0,-MAX_OFFSET_H,MAX_OFFSET_V,1));
	private JSpinner offsetZ = new JSpinner(new SpinnerNumberModel(0,-MAX_OFFSET_H,MAX_OFFSET_H,1));
	
	private JButton copyNext = new JButton("Copy Command");
	private JButton resetCopy = new JButton("Reset");
	
	private JTextPane out = new JTextPane();
	private JScrollPane jsp = new JScrollPane(out);
	
	private NumberFormat percentFormater = NumberFormat.getInstance();
	
	
	
	private SchematicData data = null;
	
	private int curCommand = 0;
	
	private static final String fallingBlock = "falling_block"; //was FallingSand pre-1.11
	
	
	
	
	private static String[] dangerousBlocks = {"fire", "tnt", "flowing_lava", "lava"}; 
	
	//private static HashMap<Integer,String> itemNames = new HashMap<Integer,String>();
	
	/*  Not used anymore
	
	private static final int tr = 0x10000;
	/ **
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
	 * /
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
	
	public static final String[] materials = { "air","stone","grass","dirt","cobblestone","planks","sapling","bedrock",
			"flowing_water","water","flowing_lava","lava","sand","gravel","gold_ore","iron_ore",
			"coal_ore","log","leaves","sponge","glass","lapis_ore","lapis_block","dispenser",
			"sandstone","noteblock","bed","golden_rail","detector_rail","sticky_piston","web","tallgrass",
			
			"deadbush","piston","piston_head","wool","piston_extension","yellow_flower","red_flower","brown_mushroom",
			"red_mushroom","gold_block","iron_block","double_stone_slab","stone_slab","brick_block","tnt","bookshelf",
			"mossy_cobblestone","obsidian","torch","fire","mob_spawner","oak_stairs","chest","redstone_wire",
			"diamond_ore","diamond_block","crafting_table","wheat","farmland","furnace","lit_furnace","standing_sign",
			
			"wooden_door","ladder","rail","stone_stairs","wall_sign","lever","stone_pressure_plate","iron_door",
			"wooden_pressure_plate","redstone_ore","lit_redstone_ore","unlit_redstone_torch","redstone_torch","stone_button","snow_layer","ice",
			"snow","cactus","clay","reeds","jukebox","fence","pumpkin","netherrack",
			"soul_sand","glowstone","portal","lit_pumpkin","cake","unpowered_repeater","powered_repeater","stained_glass", 
			
			"trapdoor","monster_egg","stonebrick","brown_mushroom_block","red_mushroom_block","iron_bars","glass_pane","melon_block",
			"pumpkin_stem","melon_stem","vine","fence_gate","brick_stairs","stone_brick_stairs","mycelium","waterlily", 
			"nether_brick","nether_brick_fence","nether_brick_stairs","nether_wart","enchanting_table","brewing_stand","cauldron","end_portal",
			"end_portal_frame","end_stone","dragon_egg","redstone_lamp","lit_redstone_lamp","double_wooden_slab","wooden_slab","cocoa", 
			
			"sandstone_stairs","emerald_ore","ender_chest","tripwire_hook","tripwire","emerald_block","spruce_stairs","birch_stairs",
			"jungle_stairs","command_block","beacon","cobblestone_wall","flower_pot","carrots","potatoes","wooden_button", 
			"skull","anvil","trapped_chest","light_weighted_pressure_plate","heavy_weighted_pressure_plate","unpowered_comparator","powered_comparator","daylight_detector",
			"redstone_block","quartz_ore","hopper","quartz_block","quartz_stairs","activator_rail","dropper","stained_hardened_clay", 
			
			"stained_glass_pane","leaves2","log2","acacia_stairs","dark_oak_stairs","slime","barrier","iron_trapdoor",
			"prismarine","sea_lantern","hay_block","carpet","hardened_clay","coal_block","packed_ice","double_plant",
			"standing_banner","wall_banner","daylight_detector_inverted","red_sandstone","red_sandstone_stairs","double_stone_slab2","stone_slab2","spruce_fence_gate",
			"birch_fence_gate","jungle_fence_gate","dark_oak_fence_gate","acacia_fence_gate","spruce_fence","birch_fence","jungle_fence","dark_oak_fence", 
			
			"acacia_fence","spruce_door","birch_door","jungle_door","acacia_door","dark_oak_door","end_rod","chorus_plant",
			"chorus_flower","purpur_block","purpur_pillar","purpur_stairs","purpur_double_slab","purpur_slab","end_bricks","beetroots",
			"grass_path","end_gateway","repeating_command_block","chain_command_block","frosted_ice","magma","nether_wart_block","red_nether_brick",
			"bone_block","structure_void","observer","white_shulker_box","orange_shulker_box","magenta_shulker_box","light_blue_shulker_box","yellow_shulker_box",
			
			"lime_shulker_box","pink_shulker_box","gray_shulker_box","silver_shulker_box","cyan_shulker_box","purple_shulker_box","blue_shulker_box","brown_shulker_box",
			"green_shukler_box","red_shulker_box","black_shulker_box","white_glazed_terracotta","orange_glazed_terracotta","magenta_glazed_terracotta","light_blue_glazed_terracotta","yellow_glazed_terracotta",
			"lime_glazed_terracotta","pink_glazed_terracotta","gray_glazed_terracotta","silver_glazed_terracotta","cyan_glazed_terracotta","purple_glazed_terracotta","blue_glazed_terracotta","brown_glazed_terracotta",
			"green_glazed_terracotta","red_glazed_terracotta","black_glazed_terracotta","concrete","concrete_powder","","","structure_block"
	};
	
	*/
	
	public static final HashMap<String,BlockType> BLOCK_TYPES =  new HashMap<String,BlockType>();
	
	static {
		BlockType t;
		
		t = new BlockType("air",true,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("cave_air",true,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("void_air",true,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("stone"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("granite"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("polished_granite"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("diorite"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("polished_diorite"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("andesite"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("polished_andesite"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("grass_block"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dirt"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("coarse_dirt"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("podzol"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("cobblestone"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("oak_planks"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("spruce_planks"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("birch_planks"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("jungle_planks"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("acacia_planks"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dark_oak_planks"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("oak_sapling",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("spruce_sapling",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("birch_sapling",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("jungle_sapling",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("acacia_sapling",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dark_oak_sapling",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("bedrock"); 	BLOCK_TYPES.put(t.name,t);
		
		t = new BlockType("flowing_water",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("water",false,false,true,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("flowing_lava",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("lava",false,false,true,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("sand"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("red_sand"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("gravel"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("gold_ore"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("iron_ore"); 	BLOCK_TYPES.put(t.name,t);
		
		t = new BlockType("coal_ore"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("oak_log"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("spruce_log"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("birch_log"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("jungle_log"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("acacia_log"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dark_oak_log"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("stripped_oak_log"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("stripped_spruce_log"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("stripped_birch_log"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("stripped_jungle_log"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("stripped_acacia_log"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("stripped_dark_oak_log"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("oak_wood"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("spruce_wood"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("birch_wood"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("jungle_wood"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("acacia_wood"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dark_oak_wood"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("stripped_oak_wood"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("stripped_spruce_wood"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("stripped_birch_wood"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("stripped_jungle_wood"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("stripped_acacia_wood"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("stripped_dark_oak_wood"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("oak_leaves",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("spruce_leaves",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("birch_leaves",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("jungle_leaves",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("acacia_leaves",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dark_oak_leaves",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("sponge"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("wet_sponge"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("glass",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("lapis_ore"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("lapis_block"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dispenser",false,true,false,false,false,false,false); 	BLOCK_TYPES.put(t.name,t);
		
		t = new BlockType("sandstone"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("cut_sandstone"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("chiseled_sandstone"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("smooth_sandstone"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("note_block"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("white_bed",false,false,false,true,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("orange_bed",false,false,false,true,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("magenta_bed",false,false,false,true,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("light_blue_bed",false,false,false,true,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("yellow_bed",false,false,false,true,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("lime_bed",false,false,false,true,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("pink_bed",false,false,false,true,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("gray_bed",false,false,false,true,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("light_gray_bed",false,false,false,true,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("cyan_bed",false,false,false,true,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("purple_bed",false,false,false,true,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("blue_bed",false,false,false,true,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("brown_bed",false,false,false,true,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("green_bed",false,false,false,true,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("red_bed",false,false,false,true,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("black_bed",false,false,false,true,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("golden_rail",false,false,false,false,true,true,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("powered_rail",false,false,false,false,true,true,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("detector_rail",false,false,false,false,true,true,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("sticky_piston",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("cobweb",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("grass",false,false,false,true,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("fern",false,false,false,true,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("tall_grass",false,false,false,true,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("large_fern",false,false,false,true,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		
		
		t = new BlockType("dead_bush",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("piston",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("piston_head",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("white_wool"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("orange_wool"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("magenta_wool"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("light_blue_wool"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("yellow_wool"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("lime_wool"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("pink_wool"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("gray_wool"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("light_gray_wool"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("cyan_wool"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("purple_wool"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("blue_wool"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("brown_wool"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("green_wool"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("red_wool"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("black_wool"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("piston_extension",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dandelion",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("poppy",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("blue_orchid",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("allium",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("azure_bluet",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("red_tulip",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("orange_tulip",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("white_tulip",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("pink_tulip",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("oxeye_daisy",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("brown_mushroom",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		
		t = new BlockType("red_mushroom",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("gold_block"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("iron_block"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("smooth_stone"); 	BLOCK_TYPES.put(t.name,t);//double-stone_slab?
		t = new BlockType("stone_slab",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("sandstone_slab",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("red_sandstone_slab",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("cobblestone_slab",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("brick_slab",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("stone_brick_slab",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("nether_brick_slab",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("quartz_slab",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("prismarine_slab",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("prismarine_brick_slab",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dark_prismarine_slab",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("bricks"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("tnt"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("bookshelf"); 	BLOCK_TYPES.put(t.name,t);
		
		t = new BlockType("mossy_cobblestone"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("obsidian"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("torch",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("wall_torch",false,false,true,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("fire",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("spawner",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("oak_stairs",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("chest",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("redstone_wire",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		
		t = new BlockType("diamond_ore"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("diamond_block"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("crafting_table"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("wheat",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("farmland",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("furnace",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);  //no more lit_furnace
		t = new BlockType("sign",false,true,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		
		
		t = new BlockType("oak_door",false,false,false,true,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("spruce_door",false,false,false,true,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("birch_door",false,false,false,true,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("jungle_door",false,false,false,true,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("acacia_door",false,false,false,true,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dark_oak_door",false,false,false,true,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("ladder",false,false,true,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("rail",false,false,false,false,true,true,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("cobblestone_stairs",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("wall_sign",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("lever",false,false,true,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("stone_pressure_plate",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("iron_door",false,false,false,true,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		
		t = new BlockType("oak_pressure_plate",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("spruce_pressure_plate",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("birch_pressure_plate",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("jungle_pressure_plate",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("acacia_pressure_plate",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dark_oak_pressure_plate",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("redstone_ore"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("redstone_torch",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("redstone_wall_torch",false,false,true,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("stone_button",false,false,true,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("snow",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("ice",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		
		
		t = new BlockType("snow_block"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("cactus",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("clay"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("sugar_cane",false,false,false,false,true,false,true);	t.setPassTwo(); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("jukebox"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("oak_fence",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("spruce_fence",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("birch_fence",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("jungle_fence",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("acacia_fence",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dark_oak_fence",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("pumpkin"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("carved_pumpkin"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("netherrack"); 	BLOCK_TYPES.put(t.name,t);
		
		t = new BlockType("soul_sand"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("glowstone"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("nether_portal",false,false,false,false,false,false,true); 	t.setPassTwo(); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("jack_o_lantern"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("cake",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("repeater",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("white_stained_glass",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("orange_stained_glass",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("magenta_stained_glass",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("light_blue_stained_glass",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("yellow_stained_glass",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("lime_stained_glass",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("pink_stained_glass",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("gray_stained_glass",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("light_gray_stained_glass",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("cyan_stained_glass",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("purple_stained_glass",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("blue_stained_glass",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("brown_stained_glass",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("green_stained_glass",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("red_stained_glass",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("black_stained_glass",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		
		
		t = new BlockType("oak_trapdoor",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("spruce_trapdoor",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("birch_trapdoor",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("jungle_trapdoor",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("acacia_trapdoor",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dark_oak_trapdoor",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("infested_stone"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("infested_cobblestone"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("infested_stone_bricks"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("infested_cracked_stone_bricks"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("infested_mossy_stone_bricks"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("infested_chiseled_stone_bricks"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("stone_bricks"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("cracked_stone_bricks"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("mossy_stone_bricks"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("chiseled_stone_bricks"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("brown_mushroom_block"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("red_mushroom_block"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("mushroom_stem"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("iron_bars",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("glass_pane",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("melon"); 	BLOCK_TYPES.put(t.name,t);
		
		t = new BlockType("pumpkin_stem",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("melon_stem",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("vine",false,false,true,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("oak_fence_gate",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("spruce_fence_gate",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("birch_fence_gate",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("jungle_fence_gate",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("acacia_fence_gate",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dark_oak_fence_gate",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("brick_stairs",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("stone_brick_stairs",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("mycelium"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("lily_pad",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		
		t = new BlockType("nether_bricks"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("nether_brick_fence",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("nether_brick_stairs",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("nether_wart",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("enchanting_table",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("brewing_stand",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("cauldron",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("end_portal",false,true,false,false,false,false,true); 	t.setPassTwo(); 	BLOCK_TYPES.put(t.name,t);
		
		t = new BlockType("end_portal_frame",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("end_stone"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dragon_egg",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("redstone_lamp"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("oak_slab",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("spruce_slab",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("birch_slab",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("jungle_slab",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("acacia_slab",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dark_oak_slab",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("petrified_oak_slab",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("cocoa",false,false,true,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);

		
		t = new BlockType("sandstone_stairs",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("emerald_ore"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("ender_chest",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("tripwire_hook",false,false,true,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("tripwire",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("emerald_block"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("spruce_stairs",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("birch_stairs",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		
		t = new BlockType("jungle_stairs",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("command_block",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("beacon",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("cobblestone_wall",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("mossy_cobblestone_wall",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("flower_pot",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		
		t = new BlockType("potted_dandelion",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("potted_poppy",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("potted_blue_orchid",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("potted_allium",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("potted_azure_bluet",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("potted_red_tulip",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("potted_orange_tulip",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("potted_white_tulip",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("potted_pink_tulip",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("potted_oxeye_daisy",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("potted_oak_sapling",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("potted_spruce_sapling",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("potted_birch_sapling",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("potted_jungle_sapling",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("potted_acacia_sapling",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("potted_dark_oak_sapling",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("potted_oxeye_daisy",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("potted_fern",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("potted_dead_bush",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("potted_red_mushroom",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("potted_brown_mushroom",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("potted_cactus",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		
		t = new BlockType("carrots",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("potatoes",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("oak_button",false,false,true,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("spruce_button",false,false,true,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("birch_button",false,false,true,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("jungle_button",false,false,true,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("acacia_button",false,false,true,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dark_oak_button",false,false,true,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		
		t = new BlockType("player_head",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("player_wall_head",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("skeleton_skull",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("skeleton_wall_skull",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("wither_skeleton_skull",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("wither_skeleton_wall_skull",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("creeper_head",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("creeper_wall_head",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("zombie_head",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("zombie_wall_head",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dragon_head",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dragon_wall_head",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("anvil",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("chipped_anvil",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("damaged_anvil",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("trapped_chest",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("light_weighted_pressure_plate",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("heavy_weighted_pressure_plate",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("comparator",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("daylight_detector",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		
		t = new BlockType("redstone_block"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("nether_quartz_ore"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("hopper",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("quartz_block"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("quartz_pillar"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("chiseled_quartz_block"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("quartz_stairs",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("activator_rail",false,false,false,false,true,true,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dropper",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("white_terracotta"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("orange_terracotta"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("magenta_terracotta"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("light_blue_terracotta"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("yellow_terracotta"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("lime_terracotta"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("pink_terracotta"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("gray_terracotta"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("light_gray_terracotta"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("cyan_terracotta"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("purple_terracotta"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("blue_terracotta"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("brown_terracotta"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("green_terracotta"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("red_terracotta"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("black_terracotta"); 	BLOCK_TYPES.put(t.name,t);
		
		t = new BlockType("white_stained_glass_pane",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("orange_stained_glass_pane",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("magenta_stained_glass_pane",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("light_blue_stained_glass_pane",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("yellow_stained_glass_pane",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("lime_stained_glass_pane",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("pink_stained_glass_pane",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("gray_stained_glass_pane",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("light_gray_stained_glass_pane",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("cyan_stained_glass_pane",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("purple_stained_glass_pane",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("blue_stained_glass_pane",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("brown_stained_glass_pane",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("green_stained_glass_pane",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("red_stained_glass_pane",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("black_stained_glass_pane",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("acacia_stairs",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dark_oak_stairs",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("slime_block",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("barrier",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("iron_trapdoor",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		
		t = new BlockType("prismarine"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("prismarine_bricks"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dark_prismarine"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("sea_lantern"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("hay_block"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("white_carpet",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("orange_carpet",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("magenta_carpet",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("light_blue_carpet",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("yellow_carpet",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("lime_carpet",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("pink_carpet",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("gray_carpet",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("light_gray_carpet",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("cyan_carpet",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("purple_carpet",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("blue_carpet",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("brown_carpet",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("green_carpet",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("red_carpet",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("black_carpet",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("terracotta"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("coal_block"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("packed_ice"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("sunflower",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("lilac",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("rose_bush",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("peony",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("tall_grass",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("large_fern",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
				
		t = new BlockType("white_banner",false,true,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("orange_banner",false,true,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("magenta_banner",false,true,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("light_blue_banner",false,true,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("yellow_banner",false,true,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("lime_banner",false,true,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("pink_banner",false,true,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("gray_banner",false,true,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("light_gray_banner",false,true,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("cyan_banner",false,true,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("purple_banner",false,true,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("blue_banner",false,true,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("brown_banner",false,true,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("green_banner",false,true,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("red_banner",false,true,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("black_banner",false,true,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("white_wall_banner",false,true,true,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("orange_wall_banner",false,true,true,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("magenta_wall_banner",false,true,true,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("light_blue_wall_banner",false,true,true,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("yellow_wall_banner",false,true,true,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("lime_wall_banner",false,true,true,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("pink_wall_banner",false,true,true,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("gray_wall_banner",false,true,true,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("light_gray_wall_banner",false,true,true,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("cyan_wall_banner",false,true,true,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("purple_wall_banner",false,true,true,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("blue_wall_banner",false,true,true,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("brown_wall_banner",false,true,true,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("green_wall_banner",false,true,true,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("red_wall_banner",false,true,true,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("black_wall_banner",false,true,true,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("red_sandstone"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("cut_red_sandstone"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("chiseled_red_sandstone"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("smooth_red_sandstone"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("red_sandstone_stairs",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("spruce_fence_gate",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		
		t = new BlockType("birch_fence_gate",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("jungle_fence_gate",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dark_oak_fence_gate",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("acacia_fence_gate",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("spruce_fence",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("birch_fence",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("jungle_fence",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dark_oak_fence",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		
		t = new BlockType("acacia_fence",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("spruce_door",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("birch_door",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("jungle_door",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("acacia_door",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dark_oak_door",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("end_rod",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("chorus_plant",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		
		t = new BlockType("chorus_flower",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("purpur_block"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("purpur_pillar"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("purpur_stairs",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("purpur_slab",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("end_stone_bricks"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("beetroots",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		
		t = new BlockType("grass_path",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("end_gateway",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("repeating_command_block"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("chain_command_block"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("frosted_ice",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("magma_block",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("nether_wart_block"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("red_nether_bricks"); 	BLOCK_TYPES.put(t.name,t);
		
		t = new BlockType("bone_block"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("structure_void",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("observer"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("white_shulker_box",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("orange_shulker_box",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("magenta_shulker_box",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("light_blue_shulker_box",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("yellow_shulker_box",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		
		t = new BlockType("lime_shulker_box",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("pink_shulker_box",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("gray_shulker_box",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("light_gray_shulker_box",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("cyan_shulker_box",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("purple_shulker_box",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("blue_shulker_box",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("brown_shulker_box",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		
		t = new BlockType("green_shulker_box",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("red_shulker_box",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("black_shulker_box",false,true,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("white_glazed_terracotta"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("orange_glazed_terracotta"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("magenta_glazed_terracotta"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("light_blue_glazed_terracotta");	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("yellow_glazed_terracotta"); 	BLOCK_TYPES.put(t.name,t);
		
		t = new BlockType("lime_glazed_terracotta"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("pink_glazed_terracotta"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("gray_glazed_terracotta"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("light_gray_glazed_terracotta"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("cyan_glazed_terracotta"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("purple_glazed_terracotta"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("blue_glazed_terracotta"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("brown_glazed_terracotta"); 	BLOCK_TYPES.put(t.name,t);
		
		t = new BlockType("green_glazed_terracotta"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("red_glazed_terracotta"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("black_glazed_terracotta"); 	BLOCK_TYPES.put(t.name,t);
		
		t = new BlockType("white_concrete"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("orange_concrete"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("magenta_concrete"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("light_blue_concrete"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("yellow_concrete"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("lime_concrete"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("pink_concrete"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("gray_concrete"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("light_gray_concrete"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("cyan_concrete"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("purple_concrete"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("blue_concrete"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("brown_concrete"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("green_concrete"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("red_concrete"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("black_concrete"); 	BLOCK_TYPES.put(t.name,t);
		
		t = new BlockType("white_concrete_powder",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("orange_concrete_powder",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("magenta_concrete_powder",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("light_blue_concrete_powder",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("yellow_concrete_powder",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("lime_concrete_powder",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("pink_concrete_powder",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("gray_concrete_powder",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("light_gray_concrete_powder",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("cyan_concrete_powder",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("purple_concrete_powder",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("blue_concrete_powder",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("brown_concrete_powder",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("green_concrete_powder",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("red_concrete_powder",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("black_concrete_powder",false,false,false,false,true,false,true); 	BLOCK_TYPES.put(t.name,t);
		
		
		t = new BlockType("structure_block",false,true,false,false,false,false,false); 	BLOCK_TYPES.put(t.name,t);
		
		//1.13
		t = new BlockType("tube_coral_block"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("brain_coral_block"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("bubble_coral_block"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("fire_coral_block"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("horn_coral_block"); 	BLOCK_TYPES.put(t.name,t);
		
		t = new BlockType("dead_tube_coral_block"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dead_brain_coral_block"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dead_bubble_coral_block"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dead_fire_coral_block"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dead_horn_coral_block"); 	BLOCK_TYPES.put(t.name,t);
		
		t = new BlockType("tube_coral",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("brain_coral",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("bubble_coral",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("fire_coral",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("horn_coral",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		
		t = new BlockType("dead_tube_coral",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dead_brain_coral",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dead_bubble_coral",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dead_fire_coral",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dead_horn_coral",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		
		t = new BlockType("tube_coral_fan",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("brain_coral_fan",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("bubble_coral_fan",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("fire_coral_fan",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("horn_coral_fan",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		
		t = new BlockType("dead_tube_coral_fan",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dead_brain_coral_fan",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dead_bubble_coral_fan",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dead_fire_coral_fan",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dead_horn_coral_fan",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		
		t = new BlockType("tube_coral_wall_fan",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("brain_coral_wall_fan",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("bubble_coral_wall_fan",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("fire_coral_wall_fan",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("horn_coral_wall_fan",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		
		t = new BlockType("dead_tube_coral_wall_fan",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dead_brain_coral_wall_fan",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dead_bubble_coral_wall_fan",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dead_fire_coral_wall_fan",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dead_horn_coral_wall_fan",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);

		
		t = new BlockType("seagrass",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("tall_seagrass",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("kelp",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("kelp_plant",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("dried_kelp_block"); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("sea_pickle",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("conduit",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		t = new BlockType("bubble_column",false,false,false,false,false,false,true); 	BLOCK_TYPES.put(t.name,t);
		
		
	}
	
	private static final ArrayList<BlockType> AIR_BLOCKS = new ArrayList<BlockType>();
	static {
		AIR_BLOCKS.add(BLOCK_TYPES.get("air"));
		AIR_BLOCKS.add(BLOCK_TYPES.get("cave_air"));
		AIR_BLOCKS.add(BLOCK_TYPES.get("void_air"));
	}
	private static final ArrayList<BlockType> WATER_BLOCKS = new ArrayList<BlockType>();
	static {
		WATER_BLOCKS.add(BLOCK_TYPES.get("water"));
		WATER_BLOCKS.add(BLOCK_TYPES.get("flowing_water"));
	}
	private static final ArrayList<BlockType> DOOR_BLOCKS = new ArrayList<BlockType>();
	static {
		DOOR_BLOCKS.add(BLOCK_TYPES.get("oak_door"));
		DOOR_BLOCKS.add(BLOCK_TYPES.get("spruce_door"));
		DOOR_BLOCKS.add(BLOCK_TYPES.get("birch_door"));
		DOOR_BLOCKS.add(BLOCK_TYPES.get("jungle_door"));
		DOOR_BLOCKS.add(BLOCK_TYPES.get("acacia_door"));
		DOOR_BLOCKS.add(BLOCK_TYPES.get("dark_oak_door"));
		DOOR_BLOCKS.add(BLOCK_TYPES.get("iron_door"));
	}
	private static final ArrayList<BlockType> DOUBLEPLANT_BLOCKS = new ArrayList<BlockType>();
	static {
		DOUBLEPLANT_BLOCKS.add(BLOCK_TYPES.get("sunflower"));
		DOUBLEPLANT_BLOCKS.add(BLOCK_TYPES.get("lilac"));
		DOUBLEPLANT_BLOCKS.add(BLOCK_TYPES.get("rose_bush"));
		DOUBLEPLANT_BLOCKS.add(BLOCK_TYPES.get("peony"));
		DOUBLEPLANT_BLOCKS.add(BLOCK_TYPES.get("tall_grass"));
		DOUBLEPLANT_BLOCKS.add(BLOCK_TYPES.get("large_fern"));
	}
	
	/*
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
		"fermented_spider_eye","blaze_bowder","magma_cream","brewing_stand","cauldron","ender_eye","speckled_melon","spawn_egg",

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
	*/
	
	/*
	static final String[] potions = {
		"mundane",
		"regeneration",
		"swiftness",
		"fire_resistance",
		"poison",
		"healing",
		"night_vision",
		"mundane",//clear
		"weakness",
		"strength",
		"slowness",
		"leaping",
		"harming",
		"water_breathing",
		"invisibility",
		"mundane",//thin
		"awkward","regeneration","swiftness","fire_resistance","poison","healing","night_vision","awkward","weakness","strength","slowness","leaping","harming","water_breathing","invisibility","awkward",
		"thick","strong_regeneration","strong_swiftness","fire_resistance","strong_poison","strong_healing","night_vision","thick","weakness","strong_strength","slowness","strong_leaping","strong_harming","water_breathing","invisibility","thick",
		"thick","strong_regeneration","strong_swiftness","fire_resistance","strong_poison","strong_healing","night_vision","thick","weakness","strong_strength","slowness","strong_leaping","strong_harming","water_breathing","invisibility","thick",
		"awkward","long_regeneration","long_swiftness","long_fire_resistance","long_poison","healing","long_night_vision","awkward","long_weakness","long_strength","long_slowness","long_leaping","harming","long_water_breathing","long_invisibility","awkward",
		"awkward","long_regeneration","long_swiftness","long_fire_resistance","long_poison","healing","long_night_vision","awkward","long_weakness","long_strength","long_slowness","long_leaping","harming","long_water_breathing","long_invisibility","awkward",
		"awkward","long_regeneration","long_swiftness","long_fire_resistance","long_poison","healing","long_night_vision","awkward","long_weakness","long_strength","long_slowness","long_leaping","harming","long_water_breathing","long_invisibility","awkward",
		"awkward","long_regeneration","long_swiftness","long_fire_resistance","long_poison","healing","long_night_vision","awkward","long_weakness","long_strength","long_slowness","long_leaping","harming","long_water_breathing","long_invisibility","awkward",
	};
	*/
	
	
	// map of old entity ids to names
	static final HashMap<Integer,String> mobNames = new HashMap<Integer,String>();
	static {
		mobNames.put(50,"creeper");
		mobNames.put(51,"skeleton");
		mobNames.put(52,"spider");
		mobNames.put(53,"giant");
		mobNames.put(54,"zombie");
		mobNames.put(55,"slime");
		mobNames.put(56,"ghast");
		mobNames.put(57,"zombie_pigman");
		mobNames.put(58,"enderman");
		mobNames.put(59,"cave_spider");
		mobNames.put(60,"silverfish");
		mobNames.put(61,"blaze");
		mobNames.put(62,"magma_cube");
		mobNames.put(63,"ender_dragon");
		mobNames.put(64,"wither");
		mobNames.put(65,"bat");
		mobNames.put(66,"witch");
		mobNames.put(67,"endermite");
		mobNames.put(68,"guardian");
		mobNames.put(69,"shulker");
		//1.13 - no official numbers anymore, but for logic reasons, hostile mobs are numbered below 90
		mobNames.put(70,"phantom");
		
		mobNames.put(90,"pig");
		mobNames.put(91,"sheep");
		mobNames.put(92,"cow");
		mobNames.put(93,"chicken");
		mobNames.put(94,"squid");
		mobNames.put(95,"wolf");
		mobNames.put(96,"mooshroom");
		mobNames.put(97,"snowman");
		mobNames.put(98,"ocelot");
		mobNames.put(99,"villager_golem");
		mobNames.put(100,"horse");
		mobNames.put(101,"rabbit");
		
		mobNames.put(120,"villager");
		
		//1.13 - no official numbers anymore, but for logic reasons, hostile mobs are numbered below 90
		mobNames.put(150,"dolphin");
		//fish seem to have actual number in bedrock ver. but not going to use them
		mobNames.put(151,"cod");		//112
		mobNames.put(152,"salmon");		//109
		mobNames.put(153,"tropicalfish");//111
		mobNames.put(154,"pufferfish");	//108
		
		mobNames.put(155,"turtle");
	}
	
	
	/*
	private static final int[][] railDataX = 		{{0,0},  {1,-1}, {1,-1}, {-1,1}, {0,0},  {0,0},  {0,1}, {0,-1}, {0,-1}, {0,1}};
	private static final int[][] railDataZ = 		{{-1,1}, {0,0},  {0,0},  {0,0},  {1,-1}, {-1,1}, {1,0}, {1,0},  {-1,0}, {-1,0}};
	private static final int[][] railDataY = 		{{0,0},  {0,0},  {1,-1}, {1,-1}, {1,-1}, {1,-1}, {0,0}, {0,0},  {0,0},  {0,0}};
	private static final boolean[] railDataSlope = 	{false,  false,  true,   true,   true,   true,   false, false,  false,  false};
	*/
	
	private static enum RailDirections { 
		NORTH(0,0,-1), 
		SOUTH(0,0,1), 
		EAST(1,0,0), 
		WEST(-1,0,0), 
		EASTUP(1,1,0), 
		EASTDOWN(1,-1,0), 
		WESTUP(-1,1,0), 
		WESTDOWN(-1,-1,0), 
		NORTHUP(0,1,-1), 
		NORTHDOWN(0,-1,-1), 
		SOUTHUP(0,1,1), 
		SOUTHDOWN(0,-1,1);
		//NOTE a Y of -1 (down) can really be -1 or 0, as the down side of a slope may continue down, or level out
		
		private final Point3D point;
		
		public Point3D getOffset() {
			return point;
		}
		
		public RailDirections getOpposite() {
			switch(this) {
				default:
				case NORTH: return SOUTH;
				case SOUTH: return NORTH;
				case EAST: return WEST;
				case WEST: return EAST;
				case EASTUP: return WESTDOWN;
				case EASTDOWN: return WESTUP;
				case WESTUP: return WESTDOWN;
				case WESTDOWN: return EASTUP;
				case NORTHUP: return SOUTHDOWN;
				case NORTHDOWN: return SOUTHUP;
				case SOUTHUP: return NORTHDOWN;
				case SOUTHDOWN: return NORTHUP;
			}
		}
		
		public boolean canBeOpposite(RailDirections o) {
			switch(this) {
				default:
				case NORTH:
				case NORTHUP:
				case NORTHDOWN:
					if(o == SOUTH || o == SOUTHUP || o == SOUTHDOWN)
						return true;
					break;
				case SOUTH:
				case SOUTHUP:
				case SOUTHDOWN:
					if(o == NORTH || o == NORTHUP || o == NORTHDOWN)
						return true;
					break;
				case EAST:
				case EASTUP:
				case EASTDOWN:
					if(o == WEST || o == WESTUP || o == WESTDOWN)
						return true;
					break;
				case WEST:
				case WESTUP:
				case WESTDOWN:
					if(o == EAST || o == EASTUP || o == EASTDOWN)
						return true;
					break;
			}
			return false;
		}
		
		public boolean isSlope() {
			return point.y != 0;
		}

		RailDirections(int x, int y, int z){
			point = new Point3D(x,y,z);
		}
	};
	
	private static RailDirections[][] railDataDir= {
		{RailDirections.NORTH, RailDirections.SOUTH},
		{RailDirections.EAST,  RailDirections.WEST},
		{RailDirections.EASTUP, RailDirections.WESTDOWN},
		{RailDirections.WESTUP, RailDirections.EASTDOWN},
		{RailDirections.NORTHUP, RailDirections.SOUTHDOWN},
		{RailDirections.SOUTHUP, RailDirections.NORTHDOWN},
		{RailDirections.SOUTH, RailDirections.EAST},
		{RailDirections.SOUTH, RailDirections.WEST},
		{RailDirections.NORTH, RailDirections.WEST},
		{RailDirections.NORTH, RailDirections.EAST},
	};
	
	
	private static class NextRail {
		Point3D point;
		RailDirections dir;
		
		public NextRail(int x, int y, int z, int data, boolean first) {
			this(x,y,z,railDataDir[data][first?0:1]);
		}
		public NextRail(int x, int y, int z, RailDirections rd) {
			point = new Point3D(x,y,z);
			dir = rd;
		}
		public Point3D getNextRailPos() {
			return point.add(dir.getOffset());
		}
	}
	
	
	//1.11 updated some entity names - this map will convert old to new so old schematics still work
	static final HashMap<String, String> entityNameMap = new HashMap<String, String>();
	
	
	//aligned to block grid - all others have float coords
	private static final String[] strEntityBlocks = {"item_frame","painting","ender_crystal","leash_knot",};//item frame, paintings, armor stand, ender crystal, lead knot   -   Armor stand is supposed to apply here, but apparently needs adjusted coords.
	private static final HashSet<String> entityBlocks = new HashSet<String>(Arrays.asList(strEntityBlocks));
	//may be excluded from conversion
	private static HashSet<String> entityMob = new HashSet<String>();
	//also may be excluded from conversion
	private static HashSet<String> entityPassive = new HashSet<String>();
	//never converted
	private static final String[] strEntityProjectile = {"egg","arrow","snowball","fireball","small_fireball","ender_pearl","eye_of_ender_signal","potion","xp_bottle","wither_skull","fireworks_rocket"};
	private static final HashSet<String> entityProjectile = new HashSet<String>(Arrays.asList(strEntityProjectile));
	
	
	//order to test fill possibilities
	private static final String[] testdir = {"xyz","xzy","yxz","yzx","zxy","zyx"};
	
	
	private static final String cmdStartFirst = "summon "+fallingBlock+" ~%OX% ~1 ~%OZ% {BlockState:{Name:oak_leaves},Time:1,Passengers:[{id:"+fallingBlock+",BlockState:{Name:redstone_block},Time:1,Passengers:[{id:"+fallingBlock+",BlockState:{Name:activator_rail},Time:1,Passengers:[%MINECARTS%]}]}]}";
	
	private static final String cmdStartOther = "summon "+fallingBlock+" ~%OX% ~%OY% ~%OZ% {BlockState:{Name:sand},Time:1,Passengers:[%MINECARTS%]}";
	
	//private static final String psngrQuiet = "{id:command_block_minecart,Command:\"gamerule commandBlockOutput false\"}";
	private static final String cmdQuiet = "gamerule commandBlockOutput false";
	
	//private static final String psngrClear = "{id:command_block_minecart,Command:\"fill ~%ox% ~%oy% ~%oz% ~%ow% ~%oh% ~%ol% air\"}";
	private static final String cmdClear = "fill ~%ox% ~%oy% ~%oz% ~%ow% ~%oh% ~%ol% air";
	
	//private static final String psngrBase = "{id:command_block_minecart,Command:\"fill ~%ox% ~%oy1% ~%oz% ~%ow% ~%oy1% ~%ol% %base%\"}";
	private static final String cmdBase = "fill ~%ox% ~%oy1% ~%oz% ~%ow% ~%oy1% ~%ol% %base%";
	
	//private static final String psngrClearBarriers = "{id:command_block_minecart,Command:\"fill ~%ox% ~%oy% ~%oz% ~%ow% ~%oh% ~%ol% air replace barrier\"}";
	private static final String cmdClearBarriers = "fill ~%ox% ~%oy% ~%oz% ~%ow% ~%oh% ~%ol% air replace barrier";
	
	//private static final String psngrEndCleanup = "{id:MinecartCommandBlock,Command:setblock ~ ~ ~1 command_block 0 replace {Command:fill ~ ~-2 ~-1 ~ ~ ~ air}},{id:MinecartCommandBlock,Command:setblock ~ ~-1 ~1 redstone_block},{id:MinecartCommandBlock,Command:kill @e[type=MinecartCommandBlock,r=1]}";
	private static final String psngrEndCleanup = "{id:command_block_minecart,Command:\"setblock ~ ~-2 ~1 command_block{Command:\\\"fill ~ ~0 ~-1 ~ ~3 ~ air\\\"}\"},{id:command_block_minecart,Command:\"summon "+fallingBlock+" ~ ~2 ~1 {BlockState:{Name:redstone_block},Time:1}\"},{id:command_block_minecart,Command:\"kill @e[type=command_block_minecart,distance=..2]\"}";
	
	//private static final String psngrMidCleanup = "{id:MinecartCommandBlock,Command:kill @e[type=MinecartCommandBlock,r=1]}";
	private static final String psngrMidCleanup = "{id:command_block_minecart,Command:\"kill @e[type=command_block_minecart,distance=..2]\"}";
	
	private static final String psngrCMD = "{id:command_block_minecart,Command:\"%CMD%\"}";
	
	
	//private static final String psngrCmdBlockList = "{id:commandblock_minecart,Command:\"fill ~ ~-3 ~ ~%len% ~-3 ~ command_block 2 replace {Command:\\\"\\\"}\"}";
	//private static final String psngrCmdBlockSingle = "{id:commandblock_minecart,Command:\"setblock ~%ox% ~-3 ~ command_block 1\"}";
	//private static final String psngrCmdBlockSingle25 = "{id:commandblock_minecart,Command:\"setblock ~%ox% ~-3 ~ command_block 3\"}";
	private static final String psngrCmdBlockFMBlocker = "{id:command_block_minecart,Command:\"setblock ~%ox% ~-2 ~-1 furnace\"}";
	private static final String psngrCmdBlockFlyingMachine = "{id:command_block_minecart,Command:\"setblock ~-3 ~-3 ~-1 redstone_block\"},{id:command_block_minecart,Command:\"setblock ~-5 ~-2 ~-3 observer[facing=west]\"},{id:command_block_minecart,Command:\"setblock ~-4 ~-2 ~-3 slime_block\"},{id:command_block_minecart,Command:\"setblock ~-3 ~-2 ~-3 sticky_piston[facing=west]\"},{id:command_block_minecart,Command:\"setblock ~-5 ~-2 ~-2 furnace\"},{id:command_block_minecart,Command:\"setblock ~-4 ~-2 ~-2 sticky_piston[facing=east]\"},{id:command_block_minecart,Command:\"fill ~-3 ~-2 ~-2 ~-3 ~-2 ~-1 slime_block\"},{id:command_block_minecart,Command:\"setblock ~-2 ~-2 ~-2 observer[facing=east]\"}";
	
	private static final String psngrCmdBlockListA = "{id:command_block_minecart,Command:\"fill ~%ox% ~%oy% ~%oz% ~%ow% ~%oy% ~%ol% command_block[facing=%dir%]{Command:\\\"\\\"}\"}";
	private static final String psngrCmdBlockSingleA = "{id:command_block_minecart,Command:\"setblock ~%ox% ~%oy% ~%oz% command_block[facing=%dir%]\"}";
	private static final String psngrCmdBlockSingleA25 = "{id:command_block_minecart,Command:\"setblock ~%ox% ~%oy% ~%oz% command_block[facing=%dir%]\"}";
	
	//private static final int[] cmdblkFacingNormal = 		{0, 4, 4, 2, 2, 2, 2, 4, 4, 0};
	//private static final int[] cmdblkFacingFive = 			{0, 1, 1, 1, 1, 1, 1, 1, 1, 0};
	//private static final int[] cmdblkFacingTwentyFive = 	{0, 5, 5, 3, 3, 3, 3, 5, 5, 0};
	
	private static final String[] cmdblkFacingNormalStr = 		{"down", 	"west", 	"west", 	"north", 	"north", 	"north", 	"north", 	"west", 	"west", 	"down"};
	private static final String[] cmdblkFacingFiveStr = 		{"down", 	"up", 		"up", 		"up", 		"up", 		"up", 		"up", 		"up", 		"up", 		"down"};
	private static final String[] cmdblkFacingTwentyFiveStr = 	{"down", 	"east", 	"east", 	"south", 	"south", 	"south", 	"south", 	"east", 	"east", 	"down"};
	
	private static class SearchResults{
		int bestPoints;
		int x,y,z;
		int okBlocks,badBlocks;
	}
	

	public static void main(String[] args) {
		
		
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
//			for(UIManager.LookAndFeelInfo l:UIManager.getInstalledLookAndFeels()) {
//				//System.out.println(l.getName());
//				if(l.getName().contains("Windows")) {
//					UIManager.setLookAndFeel(l.getClassName());
//				}
//			}
		}catch (Exception e) {
			e.printStackTrace();
		}
		
		S2CB s2cb = new S2CB();
		
		if(args.length > 1) {
			if(args[0].equals("terracotta")){
				int id = Integer.parseInt(args[1]);
				
				//s2cb.doTerracotta(id, false);
			}
			if(args[0].equals("cterracotta")){
				int id = Integer.parseInt(args[1]);
				
				//s2cb.doTerracotta(id, true);
			}
		}
	}


	public S2CB() {
		//initItems();
		initEntities();
		
		
		//init mobs - this is really only needed for determining passive and hostile mobs
		for(Map.Entry<Integer, String>e : mobNames.entrySet()) {
			String name = e.getValue();
			if(e.getKey() < 90) {
				entityMob.add(name);
			}else {
				entityPassive.add(name);
			}
		}
		
		
		init();
		pack();
		setLocationByPlatform(true);
		setVisible(true);
		
	}

	private void init() {
		
		setTitle("S2CB - Schematic To Command Block - S2CBv"+programVersion);
	
		URL url = ClassLoader.getSystemResource("s2cb.png");
		if(url==null)
			url = this.getClass().getClassLoader().getResource("s2cb.png");
		if(url==null)
			url = ClassLoader.getSystemResource("icon.png");
		if(url==null)
			url = this.getClass().getClassLoader().getResource("icon.png");
		if(url!=null) {
			ImageIcon icon = new ImageIcon(url);
			setIconImage(icon.getImage());
		}
		
		percentFormater.setMaximumFractionDigits(3);
		percentFormater.setMinimumIntegerDigits(0);
		percentFormater.setMinimumIntegerDigits(1);
		
		quiet.setSelected(true);
		clear.setSelected(true);
		chain.setSelected(false);
		noDangerousBlocks.setSelected(false);
		removeMobs.setSelected(false);
		removeEMobs.setSelected(true);
		removeProjectiles.setSelected(true);
		removeBarriers.setSelected(false);
		ignoreWirePower.setSelected(true);
		imperfectFills.setSelected(false);
		checkClones.setSelected(false);
		complexRails.setSelected(false);
		minimizeWater.setSelected(false);
		minimizeEntities.setSelected(true);
		serverSafe.setSelected(false);
		hollowOut.setSelected(false);
		limitDistance.setSelected(false);
		setLayout(new BorderLayout());
		add(jsp,BorderLayout.CENTER);
		
		JPanel controls = new JPanel();
		add(controls,BorderLayout.PAGE_START);
		
		GridBagLayout gbl = new GridBagLayout();
		controls.setLayout(gbl);
		GridBagConstraints gbcl = new GridBagConstraints();
		gbcl.gridwidth = 1;
		gbcl.gridheight = 1;
		gbcl.gridx = 0;
		gbcl.gridy = 1;
		gbcl.insets = new Insets(2,2,2,0);
		GridBagConstraints gbcc = (GridBagConstraints) gbcl.clone();
		gbcc.insets=new Insets(2,0,2,2);
		gbcc.anchor = GridBagConstraints.LINE_START;
		gbcl.anchor = GridBagConstraints.LINE_END;
		gbcl.weightx = 10;
		gbcl.weighty = 10;
		
		JLabel temp;
		JPanel tempp;
		
		gbl.setConstraints(offsetLabel, gbcl);
		controls.add(offsetLabel);
		
		gbcc.gridx=1;
		gbcc.weightx=10;
		tempp = new JPanel();
		tempp.add(new JLabel("X:"));
		tempp.add(offsetX);
		gbl.setConstraints(tempp, gbcc);
		controls.add(tempp);
		
		gbcc.gridx=2;
		gbcc.anchor = GridBagConstraints.CENTER;
		tempp = new JPanel();
		tempp.add(new JLabel("Y:"));
		tempp.add(offsetY);
		gbl.setConstraints(tempp, gbcc);
		controls.add(tempp);
		
		gbcc.gridx=3;
		gbcc.anchor = GridBagConstraints.LINE_START;
		tempp = new JPanel();
		tempp.add(new JLabel("Z:"));
		tempp.add(offsetZ);
		gbl.setConstraints(tempp, gbcc);
		controls.add(tempp);
		
		
		gbcl.gridy = gbcc.gridy = 0;
		gbcl.weightx = gbcc.weightx = 0;
		gbcl.gridwidth = 2;
		tempp = new JPanel();
		temp = new JLabel("Output:");
		tempp.add(temp);
		tempp.add(outputType);
		gbl.setConstraints(tempp, gbcl);
		controls.add(tempp);
		outputType.setToolTipText("<html><body>Sets the output type of the generated commands.<br>Command Blocks will format the output commands to be run in command blocks.<br>Data Pack will format the output as a data pack file.<br>You will need access to the save files to use a data pack,<br>but can use command blocks as long as you have creative access / ops.");
		
		gbcl.gridx = 2;
		gbcl.gridwidth = 1;
		tempp = new JPanel();
		temp = new JLabel("New Command Offset:");
		tempp.add(temp);
		tempp.add(moreCmds);
		gbl.setConstraints(tempp, gbcl);
		controls.add(tempp);
		moreCmds.setToolTipText("<html><body>If more than one command block is needed,<br> do you want to reuse the same command block,<br> or use command blocks in a row in a particular direction,<br> or use command blocks every other block in a particular direction?<br><br>The command blocks should be triggered in number order,<br>and the position of the build is 1 block south and 1 block east of the First command block.<br>(positive one X and positive one Z from the first command).</body></html>");

		
		gbcl.gridx = 3;
		tempp = new JPanel();
		temp = new JLabel("Base:");
		tempp.add(temp);
		tempp.add(base);
		gbl.setConstraints(tempp, gbcl);
		controls.add(tempp);
		base.setToolTipText("The material under the schematic (will be placed so the schematic has a smooth base)");
		
		
		gbcl.gridy = gbcc.gridy = 2;
		gbcl.gridx = 0;
		gbcl.weighty = 10;
		gbcl.gridwidth = 4;
		JPanel cbPanel = new JPanel();
		cbPanel.add(quiet);
		cbPanel.add(clear);
		//cbPanel.add(chain);
		cbPanel.add(noDangerousBlocks);
		cbPanel.add(removeEMobs);
		cbPanel.add(removeMobs);
		cbPanel.add(removeProjectiles);
		cbPanel.add(limitDistance);
		cbPanel.add(serverSafe);
		gbl.setConstraints(cbPanel, gbcl);
		controls.add(cbPanel);
		
		gbcl.gridy = gbcc.gridy = 3;
		cbPanel = new JPanel();
		cbPanel.add(ignoreWirePower);
		cbPanel.add(complexRails);
		cbPanel.add(removeBarriers);
		cbPanel.add(minimizeWater);
		cbPanel.add(minimizeEntities);
		cbPanel.add(hollowOut);
		cbPanel.add(checkClones);
		cbPanel.add(imperfectFills);
		
		gbl.setConstraints(cbPanel, gbcl);
		controls.add(cbPanel);
		quiet.setToolTipText("<html><body>Whether to stop command block output from showing in the chat.</body></html>");
		clear.setToolTipText("<html><body>Whether to fill the area for the schematic with air initially.</body></html>");
		chain.setToolTipText("<html><body>Whether to set up the command blocks so the can be triggered in a chain.<br>May not work properly if there are 5 or more commands!<br>Make sure the initial block is a normal inpulse command block and the others are chain command blocks that are always active,<br>and that each command block points at the next, starting from the north-west corner.</body></html>");
		noDangerousBlocks.setToolTipText("<html><body>If enabled, won't place 'dangerous' blocks, such as lava, tnt and fire.</body></html>");
		removeMobs.setToolTipText("<html><body>Whether to remove ALL mobs from the schematic.</body></html>");
		removeEMobs.setToolTipText("<html><body>Whether to remove hostile mobs from the schematic.</body></html>");
		removeProjectiles.setToolTipText("<html><body>Whether to remove projectiles (arrows, fireballs, etc.) from the schematic.</body></html>");
		removeBarriers.setToolTipText("<html><body>Whether to remove barrier blocks from the schematic.<br>Useful for importing slimeblock machines that have been 'jammed'.</body></html>");
		removeMobs.setToolTipText("<html><body>Whether More carefully place rails for complex rails system but create bigger commands (checked), or to just place rails like other blocks (smaller commands) but potentially have them not work right (unchecked).</body></html>");
		minimizeWater.setToolTipText("<html><body>If selected, only places source blocks. Unselected, water of all levels is placed.<br>Unselect if water was shaped by placing / removing blocks after it flowed into place.</body></html>");
		minimizeEntities.setToolTipText("<html><body>Removes most unneeded (default) properties from entities for smaller commands.</body></html>");
		complexRails.setToolTipText("<html><body>If checked, will more carefully place rails, one at a time, following the rail line directions,<br>compared to just placing them along with other blocks, west to east, north to south, bottom to top.<br>Checked generates larger commands and is only needed if you have rail lines right next to each other or complex rail intersections.<br>Checked may still have a few rails placed incorrectly in some circumstances where the rails needed to be placed in a specific order or you needed to pace a rail before another and then delete the first rail to get the wanted configuration.</body></html>");
		ignoreWirePower.setToolTipText("<html><body>Unchecked, restores redstone wire with the exact power from schematic. Checked, redstone wire is placed unpowered, relying on the game to recalculate power level (creates smaller commands).</body></html>");
		serverSafe.setToolTipText("<html><body>Limits the length of commands to about 80% of the maximum length normally allowed as some server kick players using max length commands.</body></html>");
		hollowOut.setToolTipText("<html><body>If enabled, removes completely hidden blocks, which should reduce the number of commands needed for schematics with large solid areas, but can also increase it for certain schematics.</body></html>");
		limitDistance.setToolTipText("<html><body>If enabled, this option makes sure that the line of command block needed to recreate the schematic does not exceed the width of the schematic (depending on build direction) by creating additional lines of command blocks.</body></html>");
		imperfectFills.setToolTipText("<html><body>Attempts to fine areas of mostly one type of block to do as a large fill that later is partially replaces by blocks of other types.<br>Only really useful for large schematics. <b>This takes a lot of time when turned on</b>, possibly 10 minutes for a 256x256x256 schematic.<br>May not result in fewer commands in all cases.  Works well with 'hollow'.</body></html>");
		checkClones.setToolTipText("<html><body>If checked, scans the schematic for areas that are duplicates of each other, and reproduces the copies by cloning the original.<br>This search takes time, a few minutes for a 256x256x256 schematic.</body></html>");
		
		gbcl.gridy = gbcc.gridy = 4;
		gbcl.gridx = 2;
		gbcl.gridwidth = 1;
		gbcl.weighty = 0;
		//temp = new JLabel("Choose Schematic:");
		//gbl.setConstraints(temp, gbcl);
		//controls.add(temp);
		
		gbcc.gridx = 3;
		tempp = new JPanel();
		temp = new JLabel("Choose Schematic:");
		tempp.add(temp);
		JButton run = new JButton("Choose...");
		tempp.add(run);
		gbl.setConstraints(tempp, gbcc);
		controls.add(tempp);
		
		gbcl.gridx = 1;
		gbcl.gridwidth = 2;
		/*
		tempp = new JPanel();
		temp = new JLabel("Schematic Banner Conversion");
		tempp.add(temp);
		tempp.add(oldBannerConversion);
		gbl.setConstraints(tempp, gbcl);
		controls.add(tempp);
		oldBannerConversion.setToolTipText("<html><body>How to handle Banners is .schematic files.<br>Schematics from different version of Minecraft<br>or possibly generated by different toools,<br>seem to need different conversion options.");
		*/
		oldBannerConversion.setSelectedIndex(3);
		
		JPanel copy = new JPanel(new FlowLayout(FlowLayout.TRAILING));
		add(copy,BorderLayout.PAGE_END);
		copy.add(copyNext);
		copy.add(resetCopy);
		
		copyNext.setEnabled(false);
		copyNext.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				copyNext();	
			}
		});
		resetCopy.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				resetCopy();	
			}
		});
		
		
		
		
		jsp.setMinimumSize(new Dimension(500,400));
		jsp.setPreferredSize(new Dimension(500,400));
		moreCmds.setSelectedIndex(5);
		moreCmds.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if(moreCmds.getSelectedIndex()==0 || moreCmds.getSelectedIndex()==9) {
					//same command block or minecart - no need for limit command distance
					limitDistance.setEnabled(false);
				}else {
					limitDistance.setEnabled(true);
				}
			}
		});
		
		outputType.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				boolean enable = outputType.getSelectedIndex() == 0;
				
				moreCmds.setEnabled(enable);
				limitDistance.setEnabled(enable);
				serverSafe.setEnabled(enable);
			}
		});
		
		
		run.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				chooseFile();
			}
		});
		
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		chooser.setCurrentDirectory(new File("D:\\Downloads\\Downloads from Web\\Games\\MineCraft\\MCEdit_dev-0.1.8build692.win-amd64\\appdata\\MCEdit-schematics"));
		FileFilter filter = new FileFilter() {
			@Override
			public boolean accept(File f) {
				if(f.isDirectory())
					return true;
				return f.getName().endsWith(".schematic") || f.getName().endsWith(".nbt");
				//return f.getName().endsWith(".nbt");
			}
			@Override
			public String getDescription() {
				return "Minecraft Schematic or Saved Structure";
				//return "NBT Saved Structure";
			}
		};
		chooser.setFileFilter(filter);
		chooser.addChoosableFileFilter(filter);
		
		
		//display info html page
		try {
			String src = readFile(new File("info.html"));
			URL u = this.getClass().getClassLoader().getResource("guide.png");
			if(u==null) {
				u = (new File("guide.png")).toURI().toURL();
			}
			src = src.replace("guide.png", u.toString());
			src = src.replace("%VER%", minecraftVersion);
			src = src.replace("%PRGVER%", programVersion);
			Reader reader = new StringReader(src);
			HTMLEditorKit htmlKit = new HTMLEditorKit();
			HTMLDocument htmlDoc = (HTMLDocument) htmlKit.createDefaultDocument();
			htmlKit.read(reader, htmlDoc, 0);
			out.setEditorKit(htmlKit);
			out.setDocument(htmlDoc);
			out.setEditable(false);
			out.setEnabled(true);
			out.addHyperlinkListener(new HyperlinkListener() {
				@Override
				public void hyperlinkUpdate(HyperlinkEvent e) {
					if(e.getEventType()!=EventType.ACTIVATED)
						return;
					// magic html handling code here
					URL url = e.getURL();
					if(Desktop.isDesktopSupported()&&Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)){
			            Desktop desktop = Desktop.getDesktop();
			            try {
			                desktop.browse(url.toURI());
			            } catch (Exception he) {
			                he.printStackTrace();
			            }
			        }else{
			            Runtime runtime = Runtime.getRuntime();
			            String os = System.getProperty("os.name").toLowerCase();
			            if(os.indexOf("win")>=0) {
			            	Runtime rt = Runtime.getRuntime();
			            	try {
								rt.exec("start " + url.toExternalForm());
							} catch (IOException e1) {
								e1.printStackTrace();
							}
			            }else if(os.indexOf("mac")>=0) {
			            	Runtime rt = Runtime.getRuntime();
			            	try {
								rt.exec("open " + url.toExternalForm());
							} catch (IOException e1) {
								e1.printStackTrace();
							}
			            }else if(os.indexOf("nix")>=0 || os.indexOf("nux")>=0) {
				            try {
				                runtime.exec("xdg-open " + url.toExternalForm());//no idea if this actually works
				            } catch (IOException e1) {
				            	e1.printStackTrace();
				            }
			            }
			        }
				}
			});
		} catch (IOException | BadLocationException e1) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e1.printStackTrace(pw);
			StringBuilder errorbuffer = new StringBuilder();
			errorbuffer.append(e1.getMessage());
			errorbuffer.append("\n\n");
			errorbuffer.append(sw.getBuffer());
			out.setText(errorbuffer.toString());
		}
		
		
	}
	
	private String readFile( File file ) throws IOException {
		
		Reader r;
		if(file.exists()) {
			r = new FileReader (file);
		}else {
			InputStream is = this.getClass().getResourceAsStream(file.getName());
			if(is==null)
				return "";
			r = new InputStreamReader(is, "UTF-8");
		}
		
	    BufferedReader reader = new BufferedReader( r );
	    String         line = null;
	    StringBuilder  stringBuilder = new StringBuilder();
	    String         ls = System.getProperty("line.separator");

	    while( ( line = reader.readLine() ) != null ) {
	        stringBuilder.append( line );
	        stringBuilder.append( ls );
	    }

	    reader.close();
	    
	    return stringBuilder.toString();
	}
	
	private void chooseFile() {
		
		ITag sch = null;
		
		
		int result = chooser.showOpenDialog(this);
		if(result == JFileChooser.APPROVE_OPTION) {
			NbtInputStream nis = null;
			try {
				try {
					nis = new NbtInputStream(new GZIPInputStream(new FileInputStream(chooser.getSelectedFile())));
				}catch(ZipException ze) {
					try {
						//maybe not gzipped?  really rare (as in not a proper nbt file)
						nis = new NbtInputStream(new FileInputStream(chooser.getSelectedFile()));
					}catch(Exception e) {
						throw ze;
					}
				}
				
				sch = nis.readTag();
				
			}catch(IOException e) {
				out.setText("An IOException Occured: "+e.getLocalizedMessage());
				e.printStackTrace();
			}catch(Exception e) {
				out.setText("An Exception Occured: "+e.getLocalizedMessage());
				e.printStackTrace();
			}finally {
				if(nis!=null) {
					try {
						nis.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
		
		if(sch!=null) {
			
			try {
				if(sch instanceof TagCompound && (sch.getName().equals("Schematic") || 
						(sch.getName().equals("") && ((TagCompound)sch).getTags().containsKey("DataVersion") && ((TagCompound)sch).getInteger("DataVersion") >= 1519 )) ) {
					out.setText("Running...");
					out.setEditorKit(new StyledEditorKit());
					
					out.setDocument(new DefaultStyledDocument());
					
					out.setText("Running...");
					setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
					out.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
					
					final TagCompound schem = (TagCompound)sch; 
					(new Thread(new Runnable(){

						@Override
						public void run() {
							convert(schem,chooser.getSelectedFile().getName());
						}
					})).start();
					
					
				}else {
				
					printData(sch);
				
				}
			} catch (UnexpectedTagTypeException | TagNotFoundException e) {
				e.printStackTrace();
				printData(sch);
			}
		}
	}
	
	private void printData(ITag tag) {
		
		if(tag instanceof TagCompound) {
			//expected
			TagCompound ctag = (TagCompound)tag;
			Map<String,ITag> vals = ctag.getTags();
			StringBuilder sb = new StringBuilder();
			
			sb.append("Unknown file type found:\n");
			
			for(String s:vals.keySet()) {
				ITag t = vals.get(s);
				if(t instanceof TagString) {
					sb.append(s);
					sb.append(" = ");
					sb.append(((TagString)t).getValue());
					
				}else if(t instanceof TagInteger) {
					sb.append(s);
					sb.append(" = ");
					sb.append(((TagInteger)t).getValue());
					
				}else if(t instanceof TagDouble) {
					sb.append(s);
					sb.append(" = ");
					sb.append(((TagDouble)t).getValue());
					
				}else if(t instanceof TagFloat) {
					sb.append(s);
					sb.append(" = ");
					sb.append(((TagFloat)t).getValue());
					
				}else if(t instanceof TagShort) {
					sb.append(s);
					sb.append(" = ");
					sb.append(((TagShort)t).getValue());
					
				}else if(t instanceof TagByte) {
					sb.append(s);
					sb.append(" = ");
					sb.append(0+((TagByte)t).getValue());
					
				}else if(t instanceof TagLong) {
					sb.append(s);
					sb.append(" = ");
					sb.append(((TagLong)t).getValue());
					
				}else if(t instanceof TagByteArray) {
					sb.append(s);
					sb.append(" ByteArray ");
					
				}else if(t instanceof TagIntegerArray) {
					sb.append(s);
					sb.append(" IntegerArray ");
					
				}else if(t instanceof TagList) {
					sb.append(s);
					sb.append(" List ");
					
				}else if(t instanceof TagCompound) {
					sb.append(s);
					sb.append(" Compound ");
					
				}else {
					
					sb.append(s);
					sb.append(" unknown tag ");
					sb.append(t.getClass().getName());
				}
				
				sb.append("\n");
			}
			sb.append("\n\nExpected File with DataVersion >= 1519\n");
			
			out.setText(sb.toString());
			
		}else {
			out.setText("Found tag named '"+tag.getName()+"' but not a compound tag.  Aborting.");
		}
		
		/*
		short width = tag..getShort("Width");
        System.out.println((new StringBuilder()).append("Width: ").append(width).toString());
        short length = nbttagcompound.getShort("Length");
        System.out.println((new StringBuilder()).append("Length: ").append(length).toString());
        short height = nbttagcompound.getShort("Height");
        System.out.println((new StringBuilder()).append("Height: ").append(height).toString());
        String h = nbttagcompound.getString("Materials");
        byte abyte0[] = nbttagcompound.getByteArray("Blocks");
        byte abyte1[] = nbttagcompound.getByteArray("Data");
        */
	}
	

	/**
	 * Converts a schematic to a series of command blocks
	 * @param tag the root NBT tag of the schematic
	 * @param filename the schematic file name 
	 */
	private void convert(TagCompound tag, String filename) {
		
		StringBuilder sb = new StringBuilder();
		int w,l,h;
		data = new SchematicData();
		data.out = sb;
		data.outputType = outputType.getSelectedIndex();
		
		if(filename.endsWith(".nbt") || filename.endsWith(".NBT")) {
			data.format = Format.STRUCTURE;
			if(filename.length()>4) {
				data.filename = filename.substring(0, filename.length() - 4);
			}
		}else {
			data.format = Format.SCHEMATIC;
			if(filename.length()>10) {
				data.filename = filename.substring(0, filename.length() - 10);
			}
		}
		
		try {

			if(data.format == Format.SCHEMATIC) {
				
				sb.append("Schematic File: ");
				sb.append(filename);
				sb.append("\n\n");
				
				w = tag.getShort("Width");
				l = tag.getShort("Length");
				h = tag.getShort("Height");
				
				if(h>256 || w>4000 || h > 4000) {
					sb.append("Width: ");
					sb.append(w);
					sb.append("  Length: ");
					sb.append(l);
					sb.append("  Height: ");
					sb.append(h);
					sb.append("\n");
					
					sb.append("\nSchematic is too large!");
					showText(sb);
					return;
				}
				
				appendTextNow("width: "+w+"  length: "+l+"  height: "+h);
				
				byte[] blocks = tag.getByteArray("Blocks");
				byte[] bdata = tag.getByteArray("Data");
				List<TagCompound> tileEntities = tag.getList("TileEntities", TagCompound.class);
				data.entities = tag.getList("Entities", TagCompound.class);
				
				
				appendTextNow("\n\nConverting Schematic format...");
				SchematicConverter sc = new SchematicConverter(oldBannerConversion.getSelectedIndex()); 
				sc.convert(data,blocks,bdata,tileEntities,w,h,l,out);
				sc = null;
				System.gc();
				
				System.out.println("conversion done");
				
			}else {
				
				sb.append("Structure File: ");
				sb.append(filename);
				sb.append("\n\n");
				try {
					String author = tag.getString("author");
					sb.append("\nby ");
					sb.append(author);
				}catch(TagNotFoundException e) {
					//ignore - MCEdit apparently adds an author tag, but Minecraft itself doesn't.
				}
				
				List<TagInteger> size = tag.getList("size", TagInteger.class);
				w = size.get(0).getValue();
				h = size.get(1).getValue();
				l = size.get(2).getValue();
				
				if(h>256 || w>4000 || h > 4000) {
					sb.append("Width: ");
					sb.append(w);
					sb.append("  Length: ");
					sb.append(l);
					sb.append("  Height: ");
					sb.append(h);
					sb.append("\n");
					
					sb.append("\nStructure is too large!");
					showText(sb);
					return;
				}
				
				appendTextNow("width: "+w+"  length: "+l+"  height: "+h);
				
				data.blockList = new ArrayList<TagCompound>();
				data.blockList.addAll(tag.getList("blocks", TagCompound.class));//so that this list is modifiable
				data.palette = new ArrayList<TagCompound>();
				data.palette.addAll(tag.getList("palette", TagCompound.class));//so that this list is modifiable
				data.entities = tag.getList("entities", TagCompound.class);
				
				buildBlockCache();
			}
			
			
			data.w = w;
			data.h = h;
			data.l = l;
			
		
			try {
				data.weOriginX = tag.getInteger("WEOriginX");
				data.weOriginY = tag.getInteger("WEOriginY");
				data.weOriginZ = tag.getInteger("WEOriginZ");
			}catch(TagNotFoundException tnfe) {
				//not a WorldEdit .schematic, ignore
			}
		
			//actually convert the data now.
			convert(data, sb);
			
		} catch (Exception e) {
			sb.append("\n\nERROR: ");
			sb.append(e.getClass().getName());
			sb.append("  ");
			sb.append(e.getLocalizedMessage());
			e.printStackTrace();
			showText(sb);
		}
	}
			
	/**
	 * Converts a schematic to a series of command blocks
	 * @param tag the root NBT tag of the schematic
	 * @param filename the schematic file name 
	 */
	private void convert(SchematicData data, StringBuilder sb) {
		StringBuilder psngrs = new StringBuilder();
		ArrayList<String> cmds = new ArrayList<String>();
		int cmdc=0;
		byte[][][] done;
		
		try {
			done = new byte[data.w][data.h][data.l];
			for(int x=0;x<data.w;x++) {
				for(int y=0;y<data.h;y++) {
					for(int z=0;z<data.l;z++) {
						done[x][y][z] = DONE_NOTDONE;
					}
				}
			}
			
			int volume = (data.w*data.l*data.h);
			data.volume = volume;
			
			int cmdDist = 0;
			switch(moreCmds.getSelectedIndex() * ((limitDistance.isEnabled()&&limitDistance.isSelected())?1:0)) {
				default:
					cmdDist = 0;//we don't care
					break;
				case 1:
				case 2:
				case 5:
				case 6:
					//commands go east - west
					cmdDist = data.w;
					break;
				case 3:
				case 4:
				case 7:
				case 8:
					//commands go north - south
					cmdDist = data.l;
					break;
			}
			if(cmdDist>0) {
				if(cmdDist < 100) 
					cmdDist = 100;
				//limit to multiples of 25
				cmdDist = (cmdDist/25)*25;
			}
			data.maxCmdBlockLineLength = cmdDist;
			
			
			sb.append("Width: ");
			sb.append(data.w);
			sb.append("  Length: ");
			sb.append(data.l);
			sb.append("  Height: ");
			sb.append(data.h);
			sb.append("\n");
			
			sb.append("Volume: ");
			sb.append( volume );
			sb.append(" blocks\n\n");
			
			int hyp = (int)Math.sqrt((((double)data.w)*data.w) + (((double)data.l)*data.l));
			if (hyp > 128) {
				//show view distance warning
				int dist = (hyp/16)+1;
				sb.append("You will likely need a view distance of ");
				sb.append(dist);
				sb.append(" to properly recreate this schematic with command blocks.\n");
				sb.append("You can set this yourself for single player worlds, but it is set by the server for multiplayer.\n");
				sb.append("Otherwise you will need additional players to keep the entire build area loaded.\n\n");
			}
			
			if(serverSafe.isSelected()) {
				maxMainCommandLength = MAXMAINCOMMANDLENGTHSAFE;
			}else {
				maxMainCommandLength = MAXMAINCOMMANDLENGTH;
			}
			if(data.outputType == 0) {
				sb.append("\nLimiting commands to about ");
				sb.append( maxMainCommandLength );
				sb.append(" characters\n\n");
			}
			
			if(data.maxCmdBlockLineLength > 0) {
				sb.append("\nLimiting command block row lengths to ");
				sb.append( data.maxCmdBlockLineLength );
				sb.append(" command blocks\n");
				
			}
			
			
			if(hollowOut.isSelected()) {
				appendTextNow("Hollowing build...");
				hollowOut(data);
			}
			
			
			//setup offsets
			int OX,OY,OZ,ox,oy,oz,ow,oh,ol,oy1;
			if(data.outputType == 0) {
				OX = moreCmdsX[moreCmds.getSelectedIndex()]*cmdc;
				OZ = moreCmdsZ[moreCmds.getSelectedIndex()]*cmdc;
				OY = 4;
				if(moreCmds.getSelectedIndex()==9) {
					//minecart - always 1,1
					OX = 1;
					OZ = 1;		
				}
				
				ox = 1;
				oy = -3;
				oz = 1;
			}else {
				OX = OY = OZ = 0;
				
				ox = 1;
				oy = 0;
				oz = 1;
			}
			
			Object ofx = offsetX.getValue();
			Object ofy = offsetY.getValue();
			Object ofz = offsetZ.getValue();
			if(ofx instanceof Number) {
				ox+=((Number) ofx).intValue();
			}
			if(ofy instanceof Number) {
				oy+=((Number) ofy).intValue();
			}
			if(ofz instanceof Number) {
				oz+=((Number) ofz).intValue();
			}
			oy1 = oy-1;
			ow = ox+data.w;
			oh = oy+data.h;
			ol = oz+data.l;
			data.ox = ox;
			data.oy = oy;
			data.oz = oz;
			
			
			AppendVars v = doInitialCommands(data,cmds,psngrs,cmdc,volume,OX,OY,OZ,ox,oy,oz,ow,oh,ol,oy1);
			cmdc = v.cmdc;
			OX = v.OX;
			OY = v.OY;
			OZ = v.OZ;
			
			
			
			ArrayList<String> cloneCmds = new ArrayList<String>();
			if(checkClones.isSelected()) {
				if(data.w*data.l>(384*384)) {
					doCloneBig(data,cloneCmds,done,ox,oy,oz);
				}else {
					doClone(data,cloneCmds,done,ox,oy,oz);
				}
			};
			
			
			
			//do big block areas
			if(imperfectFills.isSelected()) {
				v = doBigBlocks(data,cmds,psngrs,done,cmdc,OX,OY,OZ,ox,oy,oz,ow,oh,ol,oy1);
				cmdc = v.cmdc;
				OX = v.OX;
				OY = v.OY;
				OZ = v.OZ;
			}
			
			
			//normal passes
			v = doBuildPasses(data,cmds,psngrs,done,cmdc,volume,OX,OY,OZ,ox,oy,oz,ow,oh,ol,oy1);
			cmdc = v.cmdc;
			OX = v.OX;
			OY = v.OY;
			OZ = v.OZ;
			
			
			//do rails now (issue 8)
			if(complexRails.isSelected() ){  
				appendTextNow("Doing complex rails...");
				v = doRails(cmds, psngrs, cmdc, OX, OY, OZ, ox, oy, oz, ow, oh, ol, oy1,done);
				cmdc = v.cmdc;
				OX = v.OX;
				OY = v.OY;
				OZ = v.OZ;
			}
			
			
			//ok, now add the clone commands into the actual commands (as all block have now been placed)
			for(String cmd:cloneCmds) {
				
				v = appendPassenger(cmds, psngrs, cmd, cmdc, OX, OY, OZ, ox, oy, oz, ow, oh, ol, oy1);
				cmdc = v.cmdc;
				OX = v.OX;
				OY = v.OY;
				OZ = v.OZ;
			}
			
			
			//add entities
			v = doEntities(data,cmds,psngrs,cmdc,volume,OX,OY,OZ,ox,oy,oz,ow,oh,ol,oy1, sb);
			cmdc = v.cmdc;
			OX = v.OX;
			OY = v.OY;
			OZ = v.OZ;
			
			
			if(removeBarriers.isSelected()) {
				v = doRemoveBarriers(data,cmds,psngrs,cmdc,volume,OX,OY,OZ,ox,oy,oz,ow,oh,ol,oy1);
				cmdc = v.cmdc;
				OX = v.OX;
				OY = v.OY;
				OZ = v.OZ;
			}
			
			
			
			//if any psngrs left, add to final command
			if(psngrs.length()>0) {
				if(psngrs.length()>0) {
					psngrs.append(',');
				}
				psngrs.append(psngrEndCleanup);
				
				//create command
				String c;
				if(cmdc==0) {
					c = cmdStartFirst;
				}else {
					c = cmdStartOther;
				}
				
				c =replaceCoords(c,OX,OY,OZ,ox,oy,oz,ow,oh,ol,oy1).replace("%MINECARTS%", psngrs);
				//add command
				cmds.add(c);
			}
			
			data.cmds = cmds;
			
			//conversion done - do output
			
			if(data.outputType == 0) {
			
				if(data.maxCmdBlockLineLength > 0) {
					int numRows = (cmds.size()/data.maxCmdBlockLineLength)+1;
					sb.append(numRows);
					sb.append(" rows of command blocks needed.\n");
					if(numRows>1) {
						sb.append("Each additional row is three blocks above and one block further back from the build area compared to the previous row.\n");
					}
					//sb.append("\n\n");
					
				}
				
				sb.append("\n\n");
				sb.append(cmds.size());
				sb.append(" command blocks to generate.  ");
				sb.append(data.cmdCount);
				sb.append(" total commands.  About ");
				sb.append(data.cmdCount / cmds.size());
				sb.append(" productive commands per command block\n\n");
				
				if((moreCmds.getSelectedIndex()%2) == 1 && moreCmds.getSelectedIndex() < 9) {  //hardcoded moreCmds options - basically, any actual direction without spaces
					sb.append("Command block generator command:\n");
					sb.append(generateEmptyCommandBlocks());
				}
				
				sb.append("\n\n\n");
			
				
				if(cmds.size()<257) {
					sb.append("Commands to generate structure:");
					for(int i=0;i<cmds.size();i++) {
						String c = cmds.get(i);
						sb.append("\n\n\nCommand ");
						sb.append(i+1);
						sb.append(": (");
						sb.append(c.length());
						sb.append(" chars)\n");
						sb.append(c);
					}
				}else {
					sb.append("\n\nTOO MANY COMMANDS FOR TEXT DISPLAY!\n\nCopy the commands using the button at the bottom of the window.");
				}
				
			} else {
				//data pack
				
				sb.append(data.cmdCount);
				sb.append(" commands to generate structure.\n");
				
				appendTextNow("DONE!   Saving...");
				
				int pages = saveDataPack(data,sb);
				
				if( pages > 0) {
					sb.append("\n\nData Pack saved as "+data.outFile+"\n");
					sb.append("Copy the file into the save for your world, into the /datapacks/ subfolder.\n");
					sb.append("Leave it there as a .zip file, you don't have to unzip it.\n");
					sb.append("In game, either restart the game, or type \"/reload\" as an operator, to have the datapack be loaded.\n\n");
				}
				
				if(pages > 1) {
					sb.append("There are multiple functions needed to recreate this schematic, but you should only need to run the first one.\n");
					sb.append("These functions are named \"spawn\" through \"spawn"+pages+"\". (The unnumbered function is technically #1.)\n");
					sb.append("You should need to just run the first command in a command block, and it should automatically call the others,\n");
					sb.append("as long as the server doesn't lag badly and players stay in the area while the commands run.\n");
					sb.append("Run it like this, in a command block (so the alignment of everything is correct): \n");
					sb.append("function s2cb:"+data.filename+"/spawn\n");
					sb.append("The command \"function s2cb:"+data.filename+"/help\" will tell you this in game.\n");
					sb.append("The structure will be built " + 
							"starting " + ((data.ox>=0)?(""+(data.ox)+((data.ox!=1)?" blocks ":" block ")+" south "):(""+(-data.ox)+((data.ox!=-1)?" blocks ":" block ")+" north ")) +
							" and " + ((data.oz>=0)?(""+(data.oz)+((data.oz!=1)?" blocks ":" block ")+" east "):(""+(-data.oz)+((data.oz!=-1)?" blocks ":" block ")+" west ")) + 
							" of the command block, and from " + 
							((data.oy==0)?"the level of the command block and up.":((data.oy>0)?(""+data.oy+((data.oy>1)?" blocks ":" block ")+" above  the command block and up."):(""+(-data.oy)+((data.oy<-1)?" blocks ":" block ")+" below  the command block and up."))));
					sb.append("Then power the command block to create the structure.\n");
					sb.append("You may have to wait some time for the effects of the commands to appear.\n");
					sb.append("Once the structure is re-created, you can remove the data pack from the world.\n");
				}else if(pages == 1){
					sb.append("In game, place a command block at the north west corner of where you want the schematic to be built.\n");
					sb.append("Open the command block and enter the command \"function s2cb:"+data.filename+"/spawn\".\n");
					sb.append("Then power the command block to create the structure.\n");
					sb.append("The structure will be built " + 
							"starting " + ((data.ox>=0)?(""+(data.ox)+((data.ox!=1)?" blocks ":" block ")+" south "):(""+(-data.ox)+((data.ox!=-1)?" blocks ":" block ")+" north ")) +
							" and " + ((data.oz>=0)?(""+(data.oz)+((data.oz!=1)?" blocks ":" block ")+" east "):(""+(-data.oz)+((data.oz!=-1)?" blocks ":" block ")+" west ")) + 
							" of the command block, and from " + 
							((data.oy==0)?"the level of the command block and up.":((data.oy>0)?(""+data.oy+((data.oy>1)?" blocks ":" block ")+" above  the command block and up."):(""+(-data.oy)+((data.oy<-1)?" blocks ":" block ")+" below  the command block and up."))));
					sb.append("\nYou may have to wait some time for the effects of the command to appear.\n");
					sb.append("You may remove the command block and data pack when done.");
				} else if(pages > -10){
					sb.append(" SAVE CANCELED!");
				}
				
			}
			
			
			
			sb.append("\n\n");
			
			
			
		} catch (Exception e) {
			sb.append("\n\nERROR: ");
			sb.append(e.getClass().getName());
			sb.append("  ");
			sb.append(e.getLocalizedMessage());
			e.printStackTrace();
		}
		
		appendTextNow("DONE!   Word Wrapping text...");
		try {
			Thread.sleep(250);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		

		if(data.outputType == 0) {
			resetCopy();
		}else {
			copyNext.setEnabled(false);
			resetCopy.setEnabled(false);
		}
				
		showText(sb);
	
	}
	
	private int saveDataPack(SchematicData data,StringBuilder sb) {
		
		String fixedname = "";
		fixedname = data.filename.replaceAll(" ", "_").replaceAll("[^a-zA-Z0-9_-]", "").toLowerCase();
		
		data.cmds.add("say DONE");
		
		int pages = 1;
		String init = "say @p[distance=..16] Put a command block down at the build area. ";
		if(data.cmds.size() > 65000) {
			pages += data.cmds.size() / 65000;
			
			init += "In the command block, run commands \"function s2cb:"+fixedname+"/spawn\" through \"function s2cb:"+fixedname+"/spawn"+pages+"\" without moving the command block (running the first command should automatically run all the others). This will recreate the structure.\n";
		}else {
			init += "In the command block, run the command \"function s2cb:"+fixedname+"/spawn\" in the command block. This will recreate the structure.\n";
		}
		init += "say @p[distance=..16] The structure is "+data.w+" blocks east to west, "+data.l+" blocks north to south, and "+data.h+" blocks high, and will be built " + 
				"starting " + ((data.ox>=0)?(""+(data.ox)+((data.ox!=1)?" blocks ":" block ")+" south "):(""+(-data.ox)+((data.ox!=-1)?" blocks ":" block ")+" north ")) +
				" and " + ((data.oz>=0)?(""+(data.oz)+((data.oz!=1)?" blocks ":" block ")+" east "):(""+(-data.oz)+((data.oz!=-1)?" blocks ":" block ")+" west ")) + 
				" of the command block, and from " + 
				((data.oy==0)?"the level of the command block and up.":((data.oy>0)?(""+data.oy+((data.oy>1)?" blocks ":" block ")+" above  the command block and up."):(""+(-data.oy)+((data.oy<-1)?" blocks ":" block ")+" below  the command block and up.")));
		
		JFileChooser ch = new JFileChooser();
		ch.setCurrentDirectory(chooser.getCurrentDirectory());
		ch.addChoosableFileFilter(new FileFilter() {
			@Override
			public boolean accept(File f) {
				return f.getName().toLowerCase().endsWith(".zip");
			}

			@Override
			public String getDescription() {
				return "Data Pack Zip File";
			}
		});
		ch.setDialogTitle("Choose Data Pack Save File");
		ch.setSelectedFile(new File(data.filename + ".zip"));
		int result = ch.showSaveDialog(this);
		if(result != JFileChooser.APPROVE_OPTION) {
			return -1;
		}
		
		ZipOutputStream zos = null;
		try {
			File destFile = ch.getSelectedFile();
			if(destFile.exists()) {
				result = JOptionPane.showConfirmDialog(this, "File already exists. Overwrite?");
				if(result != JOptionPane.YES_OPTION) {
					return -2;
				}
			}
			
			data.outFile = destFile.getAbsolutePath();
			
			zos = new ZipOutputStream(new FileOutputStream(destFile),StandardCharsets.UTF_8);
			zos.setMethod(ZipOutputStream.DEFLATED);
			zos.setLevel(9); //Max Compression
			writeDataFile(zos,"pack.mcmeta", 
					PACK_MCMETA.replace("%DESC%", data.filename).replace("%w%", ""+data.w).replace("%h%", ""+data.h).replace("%l%", ""+data.l).replace("%ox%", ""+(data.ox)).replace("%oy%", ""+data.oy).replace("%oz%", ""+(data.oz))
					);
			
			//create directory structure
			data.filename = fixedname;
			String path = "data/s2cb/functions/" + data.filename.toLowerCase();
			String regex = "/";
			String[] paths = path.split(regex);
			String fullpath = "";
			for(String p:paths) {
				if(fullpath.length()>0) {
					fullpath = fullpath + "/";
				}
				fullpath = fullpath + p;
				zos.putNextEntry(new ZipEntry(fullpath+"/"));
				zos.closeEntry();
			}
			
			ArrayList<String> initList = new ArrayList<String>();
			initList.add(init);
			saveDataPackFile(zos,data.filename,"help.mcfunction",initList,0,1,null);
			
			if(pages == 1) { 
				
				saveDataPackFile(zos,data.filename,"spawn.mcfunction",data.cmds,0,data.cmds.size(),null);
			}else {
				
				initList.add("tp @e[type=player,distance=..5] ~"+(data.w/2)+" ~"+(data.h+3)+" ~"+(data.l/2));
				initList.add("fill ~0 ~0 ~0 ~-1 ~2 ~-1 air\nsetblock ~0 ~0 ~0 command_block{Command:\"function s2cb:"+fixedname+"/spawn\"}\nsummon "+fallingBlock+" ~0 ~3 ~0 {BlockState:{Name:redstone_block},Time:1}");
				saveDataPackFile(zos,data.filename,"spawnflying.mcfunction",initList,0,initList.size(),null);
				
				
				for(int i = 0; i < pages ; i++) {
					int start = 65000 * i;
					int end = 65000 * (i+1);
					saveDataPackFile(zos,data.filename,"spawn"+((i+1)>1?""+(i+1):"")+".mcfunction",data.cmds,start,end,
							(((i+1)<pages)?"s2cb:"+data.filename+"/spawn"+(i+2):""));
				}
			}
			
			zos.finish();
			
		} catch (IOException e) {
			sb.append("\n ERROR while writing output file: "+e.getLocalizedMessage());
			e.printStackTrace();
			pages = -10;
		} finally {
			if(zos!=null) {
				try {
					zos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		return pages;
	}
	
	


	private void saveDataPackFile(ZipOutputStream zos, String packname, String filename, ArrayList<String> cmds, int start, int end, String nextFile) {
		StringBuilder sb = new StringBuilder(65536);
		sb.append("#Function file to spawn structure ");
		sb.append(data.filename);
		for(int i = start ; (i < (end)) && (i < cmds.size()) ; i++ ) {
			sb.append("\n");
			sb.append(cmds.get(i));
		}
		
		if(nextFile!=null) {
			//multiple files - need at add command to run next, or to clean up.
			if(nextFile.length() > 0) {
				//run next
				sb.append("\nfill ~0 ~0 ~0 ~-1 ~2 ~-1 air\nsetblock ~0 ~0 ~0 command_block{Command:\"function "+nextFile+"\"}\nsummon "+fallingBlock+" ~0 ~3 ~0 {BlockState:{Name:redstone_block},Time:1}");
			}else {
				//clean up
				sb.append("\nfill ~0 ~0 ~0 ~-1 ~2 ~-1 air");
			}
		}
		
		
		String path = "data/s2cb/functions/" + packname.toLowerCase() + "/" +filename;
		writeDataFile(zos,path, sb.toString());
		
	}
	

	void writeDataFile(ZipOutputStream zos,String filename, String data) {

		try {
			zos.putNextEntry(new ZipEntry(filename));
			
			byte[] bytes = data.getBytes(java.nio.charset.StandardCharsets.UTF_8);
			zos.write(bytes, 0, bytes.length);
			zos.closeEntry();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}


	private AppendVars doInitialCommands(SchematicData data,ArrayList<String> cmds,StringBuilder psngrs,int cmdc,int volume, int OX, int OY, int OZ, int ox, int oy, int oz, int ow, int oh, int ol, int oy1) {
		//initial cmds setup
		appendTextNow("Creating initial commands...");
		if(quiet.isSelected()) {

			AppendVars v = appendPassenger(cmds, psngrs, cmdQuiet, cmdc, OX, OY, OZ, ox, oy, oz, ow, oh, ol, oy1);
			cmdc = v.cmdc;
			OX = v.OX;
			OY = v.OY;
			OZ = v.OZ;

		}
		if(base.getSelectedIndex()>0) {

			String c = replaceCoords(cmdBase,OX,OY,OZ,ox,oy-1,oz,ow-1,oh,ol-1,oy1).replace("%base%", BASE_BLOCK[base.getSelectedIndex()]);
			AppendVars v = appendPassenger(cmds, psngrs, c, cmdc, OX, OY, OZ, ox, oy, oz, ow, oh, ol, oy1);
			cmdc = v.cmdc;
			OX = v.OX;
			OY = v.OY;
			OZ = v.OZ;
		}
		if(clear.isSelected()) {
			
			if(volume < MAXFILLSIZE) {
			
				String c = replaceCoords(cmdClear,OX,OY,OZ,ox,oh,oz,ow-1,oy,ol-1,oy1);
				AppendVars v = appendPassenger(cmds, psngrs, c, cmdc, OX, OY, OZ, ox, oy, oz, ow, oh, ol, oy1);
				cmdc = v.cmdc;
				OX = v.OX;
				OY = v.OY;
				OZ = v.OZ;
			
			}else{
				
				
				if(data.w*data.l > (MAXFILLSIZE/2)) {
					//one y layer too much for a fill
					
					int times = (data.w*data.l*2) / MAXFILLSIZE;
					int dist = (int)Math.ceil((data.w)/(float)times);
					
					for(int yy = oh;yy>=oy;yy--) {
						//clear from top to bottom, so torches / redstone don't pop into items
						
						
						for(int i=0;i<=times;i++) {
							String psngr = replaceCoords(cmdClear,OX,OY,OZ,ox+(dist*i),yy,oz,ox+(dist*(i+1)),yy,ol-1,oy1);
						
							AppendVars v = appendPassenger(cmds, psngrs, psngr, cmdc, OX, OY, OZ, ox, oy, oz, ow, oh, ol, oy1);
							cmdc = v.cmdc;
							OX = v.OX;
							OY = v.OY;
							OZ = v.OZ;
						}
						
					}
					
				}else {
					//can do one or more y layers at a time
				
					int times = (volume *2) / MAXFILLSIZE;
					int dist = ((int)Math.floor((oh-oy)/(float)times));
					times = ((oh-oy)+1)/(dist); 
					
					//clear from top to bottom, so torches / redstone don't pop into items
					for(int i=0;i<=times;i++) {

						String psngr = replaceCoords(cmdClear,OX,OY,OZ,ox,oy+(dist*(times-i+1)),oz,ow-1,oy+(dist*(times-i) ),ol-1,oy1);
						
						AppendVars v = appendPassenger(cmds, psngrs, psngr, cmdc, OX, OY, OZ, ox, oy, oz, ow, oh, ol, oy1);
						cmdc = v.cmdc;
						OX = v.OX;
						OY = v.OY;
						OZ = v.OZ;
					}
				}
			}
		}
		return new AppendVars(cmdc,OX,OY,OZ);
	}
	
	
	private AppendVars doBuildPasses(SchematicData data,ArrayList<String> cmds,StringBuilder psngrs, byte[][][] done, int cmdc,int volume, int OX, int OY, int OZ, int ox, int oy, int oz, int ow, int oh, int ol, int oy1) {
		//add blocks
		for(int pass=0;pass<2;pass++) {
			//pass 0 skips materials with issue 2, as they need a solid object to attach to.
			//second pass then does them
			
			appendTextNow("Doing normal pass "+(pass+1)+"...");
			
			for(int y=0;y<data.h;y++) {
				for(int z=0;z<data.l;z++) {
					for(int x=0;x<data.w;x++) {
						try {
							
							if(done[x][y][z]!=DONE_DONE) {
								//DEBUG
								Block block = data.getBlockAt(x,y,z);
								//int issue = block.type.issues;
								//int block = 0;
								//int issue = 0;
								
								//if(issue==-1 && block==0 && done[x][y][z]==DONE_FORCEAIR) {
								//	System.out.println("air to replace");
								//}
								try {
									if(block.type.isAir() && done[x][y][z]!=DONE_FORCEAIR) {
											//this is not air, or is air that doesn't need to be encoded, or is already encoded (not sure how that would have happened, but...) 
											done[x][y][z]=DONE_DONE;
											continue;
									}
								}catch(Exception e) {
									throw e;
								}
								
	//							String mat = materials[block];
	//							if(mat.equals("redstone_wire")) {
	//								mat = mat;
	//							}
								
								if( block.type.needsSupport() && !( (y>0 && (done[x][y-1][z]==DONE_DONE)) || (y==0 && pass==1)) ) {
									//redstone component that needs to be placed on a block, but block below not placed yet.
									continue;
								}
								
								if(pass == 0 && block.type.isPassTwo()) {
									continue;
								}
								
								//this seems wrong - we do want to place the barriers initially, we later remove them.
								//if(block==166 && removeBarriers.isSelected()) {
								//	done[x][y][z]=DONE_DONE;
								//	continue;
								//}
								
								if(!complexRails.isSelected() || !block.type.isRail()) {
									if(pass==1 || !block.type.isSideAttached() ) {
										//any undone block in pass 1, but only ones without wall attachment (issue&2)==1  in pass 0
										
										if(minimizeWater.isSelected() && (WATER_BLOCKS.contains(block.type))&& block.properties.contains("level=")) {//level will be stripped if it is 0
											continue;
										}
										
										AppendVars v = encodeBlock(done, ox, oy, oz, x, y, z, block, false , cmds, psngrs, cmdc, OX, OY, OZ, ow, oh, ol ,oy1);
										cmdc = v.cmdc;
										OX = v.OX;
										OY = v.OY;
										OZ = v.OZ;
										
									}
							
								}
								
							}
						}catch(Exception e) {
							throw e;
						}
					}
				}
				appendTextNow(""+y);
			}
		}
		return new AppendVars(cmdc,OX,OY,OZ);
	}
	
	private AppendVars doEntities(SchematicData data,ArrayList<String> cmds,StringBuilder psngrs,int cmdc,int volume, int OX, int OY, int OZ, int ox, int oy, int oz, int ow, int oh, int ol, int oy1, StringBuilder sb) {
		appendTextNow("Adding entities...");
		for(TagCompound etag:data.entities) {
			
							
			StringBuilder summon = new StringBuilder();
			try {
				//Double x,y,z;
				Number x = new Integer(0),y = new Integer(0),z = new Integer(0);
				List<TagDouble> pos;
				List<TagInteger> ipos;
				
				TagCompound nbt = null;
				try {
					//this will exist for structures, but not for schematics
					 nbt = etag.getCompound("nbt");
				}catch(TagNotFoundException e) {
					//ignore
				}
				if(nbt == null) {
					nbt = etag;
				}
				
				
				String id = nbt.getString("id");
				//1.11 all lowercase now
				id = id.toLowerCase();
				if(entityNameMap.containsKey(id)){
					//old entity name - convert to new
					id = entityNameMap.get(id);
				}
				
				if(id.startsWith("minecraft:")) {
					id = id.substring(10);
				}
				
				if(entityProjectile.contains(id) && removeProjectiles.isSelected()) {
					continue;
				}
				
				if(removeMobs.isSelected()) {
					if(entityMob.contains(id) || entityPassive.contains(id)) {
						continue;
					}
				}else if(removeEMobs.isSelected() && entityMob.contains(id)) {
					continue;
				}
				
				
				if(etag.getTags().containsKey("blockPos")) {
					ipos = etag.getList("blockPos", TagInteger.class);
					
					x = new Integer( ipos.get(0).getValue() +ox);
					y = new Integer( ipos.get(1).getValue() +oy);
					z = new Integer( ipos.get(2).getValue() +oz);
				}else if(etag.getTags().containsKey("pos")) {
					pos = etag.getList("pos", TagDouble.class);
				
					x = new Double( pos.get(0).getValue() +ox);
					y = new Double( pos.get(1).getValue() +oy);
					z = new Double( pos.get(2).getValue() +oz);
				}else if(etag.getTags().containsKey("Pos")) {
					pos = etag.getList("Pos", TagDouble.class);
				
					x = new Double( pos.get(0).getValue() +ox);
					y = new Double( pos.get(1).getValue() +oy);
					z = new Double( pos.get(2).getValue() +oz);
				}else if(nbt.getTags().containsKey("TileX")) {
					x = new Integer( nbt.getInteger("TileX") +ox);
					y = new Integer( nbt.getInteger("TileY") +oy);
					z = new Integer( nbt.getInteger("TileZ") +oz);
				}else if(nbt.getTags().containsKey("pos")) {
					pos = nbt.getList("pos", TagDouble.class);
				
					x = new Double( pos.get(0).getValue() +ox);
					y = new Double( pos.get(1).getValue() +oy);
					z = new Double( pos.get(2).getValue() +oz);
				}
				
				/*
				if(!entityBlocks.contains(id)) {
					x = new Double(x.doubleValue() - 0.5);
					z = new Double(z.doubleValue() - 0.5);
					y = new Double(y.doubleValue() - 0.0625);
				}
				*/
				
				/*
				if( ( data.weOriginX != 0 || data.weOriginX != 0 || data.weOriginX != 0 ) &&
						(x.doubleValue() < 0 || x.doubleValue() > data.w || y.doubleValue() > data.h || z.doubleValue() < 0 || z.doubleValue() > data.l) ) {
					//may be an entity from a world edit schematic, and needs origin adjusted
					x = new Double(x.doubleValue() - data.weOriginX);
					y = new Double(y.doubleValue() - data.weOriginY);
					z = new Double(z.doubleValue() - data.weOriginZ);
				}
				*/
				
				//corrections (to get things to work correctly)
				if(id.equals("painting") || id.equals("item_frame") ) {
					
					if(etag.getTags().containsKey("blockPos")) {
						ipos = etag.getList("blockPos", TagInteger.class);
						
						x = new Integer( ipos.get(0).getValue() +ox);
						y = new Integer( ipos.get(1).getValue() +oy);
						z = new Integer( ipos.get(2).getValue() +oz);
						
						/*if(nbt.getTags().containsKey("facing")) {
							int facing = nbt.getByte("facing");
							
							if(facing != 1) {
								x = new Double(x.doubleValue() - 1);
								z = new Double(z.doubleValue() - 1);
							}
						}*/
					}else if(etag.getTags().containsKey("TileX") ) {
						x = new Integer(etag.getInteger("TileX") + ox);
						y = new Integer(etag.getInteger("TileY") + oy);
						z = new Integer(etag.getInteger("TileZ") + oz);
						
					} else {
					
						pos = etag.getList("Pos", TagDouble.class);
						//above code seems to work for recent schematics, but ones from older version don't always work.  Below code doesn't complete fix the older schematics.
						if(etag.getTag("Direction")!=null) {
							//only older schematics will have a direction (pre 1.8)
							x = Math.floor(new Double( pos.get(0).getValue() +ox));
							y = Math.floor(new Double( pos.get(1).getValue() +oy));
							z = Math.floor(new Double( pos.get(2).getValue() +oz));
						}
					}
				}
				
				
				
				//convert old schematic entities that are riding other entities, to be switchied around to be passengers. only need to check etag, as if it has an nbt tag its from a version far after the riding change.
				//doing this after getting the position as the riders likely won't have the correct schematic offset, only the base entity's position would be relative to the schematic's 0,0,0 coords.
				if(etag.getTags().containsKey("Riding")){
					etag = fixRiding(etag);
					id = etag.getString("id");
					nbt = etag;
				}
				
				
				
				summon.append("summon ");
				summon.append(id);
				summon.append(" ~");
				summon.append(x.floatValue());
				summon.append(" ~");
				summon.append(y.floatValue());
				summon.append(" ~");
				summon.append(z.floatValue());
				summon.append(" {");
				
				boolean needComma = false;
				for(ITag t:nbt.getTags().values()) {
					String name = t.getName();
					
					if(!"Pos".equals(name) && !"id".equals(name) && !name.startsWith("Tile")) 
					{
										
						StringBuilder result = writeTag(t);
						
						if(result!=null) {
							if(needComma)
								summon.append(",");
							else
								needComma=true;
							summon.append(result);
						}
					}
					
				}
				summon.append("}");
				
				
				//append entity to psngrs
				AppendVars v = appendPassenger(cmds, psngrs, summon.toString(), cmdc, OX, OY, OZ, ox, oy, oz, ow, oh, ol, oy1);
				cmdc = v.cmdc;
				OX = v.OX;
				OY = v.OY;
				OZ = v.OZ;
				
				
			} catch (UnexpectedTagTypeException | TagNotFoundException e) {
				sb.append("\n\nERROR: ");
				sb.append(e.getClass().getName());
				sb.append("  ");
				sb.append(e.getLocalizedMessage());
				e.printStackTrace();
			}
			
			
		}
		return new AppendVars(cmdc,OX,OY,OZ);
	}
	
	private TagCompound fixRiding(TagCompound tag) {
		
		try {
			TagString id = tag.getTag("id", TagString.class);
			String idstr = id.getValue();
			//1.11 all lowercase now
			idstr = idstr.toLowerCase();
			if(entityNameMap.containsKey(idstr)){
				//old entity name - convert to new
				idstr = entityNameMap.get(idstr);
			}
			id.setValue(idstr);
		} catch (UnexpectedTagTypeException | TagNotFoundException e) {
			e.printStackTrace();
		}
		
		if(tag.getTags().containsKey("Riding")){
			try {
				TagCompound riding = tag.getCompound("Riding");
				tag.removeTag(riding);
				TagCompound base = fixRiding(riding); //to handle ent 1 riding ent 2 which is riding ent 3
				List<ITag> pass = new ArrayList<ITag>();
				pass.add(tag);
				TagList passengers = new TagList("Passengers", pass);
				riding.setTag(passengers);
				riding.setName("");
				
				return base;
			} catch (UnexpectedTagTypeException | TagNotFoundException e) {
				e.printStackTrace();
			}
		}
		
		return tag;
	}


	private AppendVars doRemoveBarriers(SchematicData data,ArrayList<String> cmds,StringBuilder psngrs,int cmdc,int volume, int OX, int OY, int OZ, int ox, int oy, int oz, int ow, int oh, int ol, int oy1) {
		appendTextNow("Removing barriers...");
		
		if(volume < MAXFILLSIZE) {
		
			if(psngrs.length()>0) {
				psngrs.append(',');
			}
			String psngr = replaceCoords(cmdClearBarriers,OX,OY,OZ,ox,oh,oz,ow-1,oy,ol-1,oy1);
			
			AppendVars v = appendPassenger(cmds, psngrs, psngr, cmdc, OX, OY, OZ, ox, oy, oz, ow, oh, ol, oy1);
			cmdc = v.cmdc;
			OX = v.OX;
			OY = v.OY;
			OZ = v.OZ;
		
		}else{
			
			if(data.w*data.l > (MAXFILLSIZE/2)) {
				//one y layer too much for a fill
				
				int times = (data.w*data.l*2) / MAXFILLSIZE;
				int dist = (int)Math.ceil((data.w)/(float)times);
				
				for(int yy = oh;yy>=oy;yy--) {
					//clear from top to bottom, so torches / redstone don't pop into items
					
					
					for(int i=0;i<=times;i++) {
						String psngr = replaceCoords(cmdClearBarriers,OX,OY,OZ,ox+(dist*i),yy,oz,ox+(dist*(i+1)),yy,ol-1,oy1);
					
						AppendVars v = appendPassenger(cmds, psngrs, psngr, cmdc, OX, OY, OZ, ox, oy, oz, ow, oh, ol, oy1);
						cmdc = v.cmdc;
						OX = v.OX;
						OY = v.OY;
						OZ = v.OZ;
					}
					
				}
				
			}else {
			
				int times = (volume *2) / MAXFILLSIZE;
				int dist = (int)Math.ceil((oh-oy)/(float)times);
				
				for(int i=0;i<=times;i++) {

					String psngr = replaceCoords(cmdClearBarriers,OX,OY,OZ,ox,oy+(dist*(times-i+1)),oz,ow-1,oy+(dist*(times-i)),ol-1,oy1);
					
					AppendVars v = appendPassenger(cmds, psngrs, psngr, cmdc, OX, OY, OZ, ox, oy, oz, ow, oh, ol, oy1);
					cmdc = v.cmdc;
					OX = v.OX;
					OY = v.OY;
					OZ = v.OZ;
				}
			}
		}
		return new AppendVars(cmdc,OX,OY,OZ);
	}
	
	
	
	
	
	private AppendVars doBigBlocks(SchematicData data,ArrayList<String> cmds,StringBuilder psngrs, byte[][][] done, int cmdc, int OX, int OY, int OZ, int ox, int oy, int oz, int ow, int oh, int ol, int oy1) {
		//This attempts to find large areas of mostly (but not completely) one block to do as a single fill. (largest number of blocks filled will be done first) 
		//Blocks of the same type will count as 1 point, air will count as -1 (as then will need to be done when normally they are not).  Don't go further if out of bounds (obv.), > fill size, or if block marked done found.
		//We will fill the highest scoring area from the current block, as long as > 25 points (smaller areas will be handled by normal passes)
		//then mark blocks of same type in the area as done, and any air blocks in the area will be marked as needing to be done.
		//Much of this code should be similar to the normal fill routine
		
		appendTextNow("Finding Imperfect Fills...");
		appendTextNow(" 0%");
		long progressTime = System.currentTimeMillis();
		long count = 0;
		
		for(int y=0;y<data.h;y++) {
			for(int z=0;z<data.l;z++) {
				long time = System.currentTimeMillis();
				if(time - progressTime > 5000) {
					progressTime = time;
					appendProgressNow(count/(double)(data.h*data.l*data.w));
				}
				
				for(int x=0;x<data.w;x++) {
					count++;
					
					if(done[x][y][z]!=DONE_DONE) {
						Block block = data.getBlockAt(x,y,z);
												
						if(block.type.issues != 0) {
							//if air or block with an issue, don't fill with this block
							continue;
						}
						
						//System.out.println("\n\nStarting from "+x+","+y+","+z+"  - "+materials[block]+" data: "+bdata+" "+bdstr);
						
						int bestPoints=0, bestX=0, bestY=0, bestZ=0; 
						
						//look for biggest fill starting here, going to xx,yy,zz
						boolean valid = true;
						for(int yy=y;yy<data.h&&65>yy-y;yy++) {
							for(int zz=z;zz<data.l&&65>zz-z;zz++) {
								
								int okblocks=0,badblocks=0;
								
								for(int xx=x;xx<data.w&65>xx-x;xx++) {
									
									long volume=(xx-x+1)*(yy-y+1)*(zz-z+1);
									
									//ok weed out bad fills - too big, or covers already filled areas (actually this second test will be done as we count up points)
									if(volume >= MAXFILLSIZE) {
										break;
									}
									
									if(done[xx][yy][zz]==DONE_DONE) {
										break;
									}
									
									valid = true;
									//sum points - actually going to make this a bit more complex - points = goodfills * ((volume-airblocks)/volume)  basically, good blocks times ok ratio  (initially was just going to have points be okblocks - badblocks, but that could lead to fills where a large center portion had to be later removed that would be better as two (or more) fills. hopefully this should disencentiveize fills like that)
//*
									for(int y3=y;y3<=yy;y3++) {
										for(int z3=z;z3<=zz;z3++) {
											Block thisblock = data.getBlockAt(xx,y3,z3);
											
											
											if(thisblock.equals(block) ) {
												okblocks++;
											}else if(AIR_BLOCKS.contains(thisblock.type)) {
												badblocks++;
											}
											
											if(done[xx][y3][z3]==DONE_DONE || badblocks>512) {
												valid=false;
												break;
											}
										}
										if(valid==false)
											break;
									}
									
/*/
									
									int okblocks = 0, badblocks = 0;
									
									for(int x3=x;x3<=xx;x3++) {
										for(int y3=y;y3<=yy;y3++) {
											for(int z3=z;z3<=zz;z3++) {
												
												int thisblock = getBlockAt(x3,y3,z3);
												int thisbdata = getDataAt(x3, y3, z3);
												String thisbdstr = null; 
												if(data.format == Format.STRUCTURE) {
													thisbdstr = getDataStringAt(x3, y3, z3);
												}
												
												if(thisblock == block && thisbdata==bdata && (bdstr==null || bdstr.equals(thisbdstr)) ) {
													okblocks++;
												}else if(thisblock==0) {
													badblocks++;
												}
												
												if(done[x3][y3][z3]==DONE_DONE || badblocks>512) {
													valid=false;
													break;
												}
												
											}
											if(valid==false)
												break;
										}
										if(valid==false)
											break;
									}
//*/									

									//if too low a ratio of placed blocks for a large enough area, give up (or search will take too long)
									if(volume > 1000) {
										double fillRatio = okblocks / (double)volume;
										if(fillRatio < 0.1) {
											//give up
											valid = false;
										}
									}
									
									
									if(valid==false) {
										break;
									}else {
										double okRatio = (volume - badblocks) / (double)volume;
										int points = okblocks - (2*badblocks);//(int)(  okblocks * okRatio) - badblocks;
										
										if(points > bestPoints) {
											bestPoints = points;
											bestX = xx;
											bestY = yy;
											bestZ = zz;
											
											//System.out.println(" "+xx+","+yy+","+zz+" gives "+points+" points  - "+okblocks+" ok, "+badblocks+" bad - "+okRatio+" ratio");
										}
									}
								}
								
								if(!valid && zz-z > 32) {
									//if this was not valid - and we are bigger than 32, then going bigger probably won't be valid either
									break;
								}
							}
							
							if(!valid && yy-y > 32) {
								//if this was not valid - and we are bigger than 32, then going bigger probably won't be valid either
								break;
							}
						}
						
						if(bestPoints >= 5) {
							//best fill fills at least 10 more blocks than will need extra commands to remove - do fill
							// - all right, it is now 5 blocks, and the points are just ok blocks - 2 * bad blocks  as this required 17 fewer command block on my test schematic compared to the more complex formula.
							//System.out.println("\n\nStarting from "+x+","+y+","+z+"  - "+materials[block]+" data: "+bdata+" "+bdstr);
							//System.out.println("  "+bestX+","+bestY+","+bestZ+" gives "+bestPoints+" points");
						
							String cmd = "fill ~"+(x+ox)+" ~"+(y+oy)+" ~"+(z+oz)+" ~"+(bestX+ox)+" ~"+(bestY+oy)+" ~"+(bestZ+oz)+" "+block.toString();
														
							
							
							AppendVars v = appendPassenger(cmds, psngrs, cmd, cmdc, OX, OY, OZ, ox, oy, oz, ow, oh, ol, oy1);
							cmdc = v.cmdc;
							OX = v.OX;
							OY = v.OY;
							OZ = v.OZ;
							
							//mark only blocks of same type as initial block as done
							for(int x3=x;x3<=bestX;x3++) {
								for(int y3=y;y3<=bestY;y3++) {
									for(int z3=z;z3<=bestZ;z3++) {
										
										Block thisblock = data.getBlockAt(x3,y3,z3);
																			
										if(thisblock.equals(block) ) {
											done[x3][y3][z3] = DONE_DONE;
										}else if(AIR_BLOCKS.contains(thisblock.type)) {
											//need to mark air as needing to be converted
											done[x3][y3][z3] = DONE_FORCEAIR;
										}
									}
								}
							}
						}
						
					}
				}
			}
		}
		appendTextNow(" 100%");
		
		return new AppendVars(cmdc,OX,OY,OZ);
	}
	
	
	
	private AppendVars doBigBlocksThread(SchematicData data,ArrayList<String> cmds,StringBuilder psngrs, byte[][][] done, int cmdc, int OX, int OY, int OZ, int ox, int oy, int oz, int ow, int oh, int ol, int oy1) {
		//This attempts to find large areas of mostly (but not completely) one block to do as a single fill. (largest number of blocks filled will be done first) 
		//Blocks of the same type will count as 1 point, air will count as -1 (as then will need to be done when normally they are not).  Don't go further if out of bounds (obv.), > fill size, or if block marked done found.
		//We will fill the highest scoring area from the current block, as long as > 25 points (smaller areas will be handled by normal passes)
		//then mark blocks of same type in the area as done, and any air blocks in the area will be marked as needing to be done.
		//Much of this code should be similar to the normal fill routine
		
		//ERROR: This doesn't seem to have the same results as doBigBlocks for some unknown reason. So do not use for now.
		
		appendTextNow("Finding Imperfect Fills...");
		appendTextNow(" 0%");
		long progressTime = System.currentTimeMillis();
		long count = 0;
		
		for(int y=0;y<data.h;y++) {
			for(int z=0;z<data.l;z++) {
				long time = System.currentTimeMillis();
				if(time - progressTime > 5000) {
					progressTime = time;
					appendProgressNow(count/(double)(data.h*data.l*data.w));
				}
				
				for(int x=0;x<data.w;x++) {
					count++;
					
					if(done[x][y][z]!=DONE_DONE) {
						Block block = data.getBlockAt(x,y,z);
												
						if(block.type.issues != 0) {
							//if air or block with an issue, don't fill with this block
							continue;
						}
						
						//long start = System.currentTimeMillis();
						
						//System.out.println("\n\nStarting from "+x+","+y+","+z+"  - "+materials[block]+" data: "+bdata+" "+bdstr);
						
						int bestPoints=0, bestX=0, bestY=0, bestZ=0; 
						
						//look for biggest fill starting here, going to xx,yy,zz
						//start a thread for each Y level
						int threads = 65; //really 64, but one extra to be safe
						Thread[] thread = new Thread[threads];
						final SearchResults[] sr = new SearchResults[threads];
						for(int yy=y;yy<data.h&&65>yy-y;yy++) {
							
							int pos = yy-y;
							final int zf = z;
							final int xf = x;
							final int yf = y;
							final int yyf = yy;
							
							Runnable searchThread = new Runnable() {
								int id;
								public boolean equals(Object o) {
									if(o instanceof Number) {
										this.id = ((Number)o).intValue();
									}
									return false;
								}
								@Override
								public void run() {
									boolean valid = true;
									int bestPoints=0;
									sr[id] = new SearchResults();
									for(int zz=zf;zz<data.l&&65>zz-zf;zz++) {
										
										int okblocks=0,badblocks=0;
										
										for(int xx=xf;xx<data.w&65>xx-xf;xx++) {
											
											long volume=(xx-xf+1)*(yyf-yf+1)*(zz-zf+1);
											
											//ok weed out bad fills - too big, or covers already filled areas (actually this second test will be done as we count up points)
											if(volume >= MAXFILLSIZE) {
												break;
											}
											
											if(done[xx][yyf][zz]==DONE_DONE) {
												break;
											}
											
											valid = true;
											//sum points - actually going to make this a bit more complex - points = goodfills * ((volume-airblocks)/volume)  basically, good blocks times ok ratio  (initially was just going to have points be okblocks - badblocks, but that could lead to fills where a large center portion had to be later removed that would be better as two (or more) fills. hopefully this should disencentiveize fills like that)
											for(int y3=yf;y3<=yyf;y3++) {
												for(int z3=zf;z3<=zz;z3++) {
													Block thisblock = data.getBlockAt(xx,y3,z3);
																										
													if(thisblock.equals(block) ) {
														okblocks++;
													}else if(AIR_BLOCKS.contains(thisblock.type)) {
														badblocks++;
													}
													
													if(done[xx][y3][z3]==DONE_DONE || badblocks>512) {
														valid=false;
														break;
													}
												}
												if(valid==false)
													break;
											}
																			

											//if too low a ratio of placed blocks for a large enough area, give up (or search will take too long)
											if(volume > 1000) {
												double fillRatio = okblocks / (double)volume;
												if(fillRatio < 0.1) {
													//give up
													valid = false;
												}
											}
											
											
											if(valid==false) {
												break;
											}else {
												double okRatio = (volume - badblocks) / (double)volume;
												int points = (int)(  okblocks * okRatio) - badblocks;
												
												if(points > bestPoints) {
													sr[id].bestPoints = points;
													sr[id].x = xx;
													sr[id].y = yyf;
													sr[id].z = zz;
													sr[id].okBlocks = okblocks;
													sr[id].badBlocks = badblocks;
													//System.out.println(" "+xx+","+yy+","+zz+" gives "+points+" points  - "+okblocks+" ok, "+badblocks+" bad - "+okRatio+" ratio");
												}
											}
										}
										
										if(!valid && zz-zf > 32) {
											//if this was not valid - and we are bigger than 32, then going bigger probably won't be valid either
											break;
										}
									}
								}
							};
							
							searchThread.equals(pos);
							thread[pos]=new Thread(searchThread);
							thread[pos].start();
							
						}
						
						
						//wait
						for(int pos=0;pos<thread.length;pos++) {
							if(thread[pos]!=null && thread[pos].isAlive()) {
								try {
									thread[pos].join();
								} catch (InterruptedException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
							}
						}
						//get best fill from results
						for(int pos=0;pos<thread.length;pos++) {
							if(sr[pos]!=null && sr[pos].bestPoints>bestPoints) {
								bestPoints = sr[pos].bestPoints;
								bestX = sr[pos].x;
								bestY = sr[pos].y;
								bestZ = sr[pos].z;
							}
						}
						
						//long end = System.currentTimeMillis();
						//System.out.println("block "+x+","+y+","+z+" fill search took "+ (end-start));
						
						
						if(bestPoints >= 10) {
							//best fill fills at least 10 more blocks than will need extra commands to remove - do fill
							//System.out.println("Starting from "+x+","+y+","+z+"  - "+materials[block]+" data: "+bdata+" "+bdstr+"   to  "+bestX+","+bestY+","+bestZ+" gives "+bestPoints+" points");

							String cmd = "fill ~"+(x+ox)+" ~"+(y+oy)+" ~"+(z+oz)+" ~"+(bestX+ox)+" ~"+(bestY+oy)+" ~"+(bestZ+oz)+" "+block.toString();
														
							AppendVars v = appendPassenger(cmds, psngrs, cmd, cmdc, OX, OY, OZ, ox, oy, oz, ow, oh, ol, oy1);
							cmdc = v.cmdc;
							OX = v.OX;
							OY = v.OY;
							OZ = v.OZ;
							
							//mark only blocks of same type as initial block as done
							for(int x3=x;x3<=bestX;x3++) {
								for(int y3=y;y3<=bestY;y3++) {
									for(int z3=z;z3<=bestZ;z3++) {
										
										Block thisblock = data.getBlockAt(x3,y3,z3);
																				
										if(thisblock.equals(block) ) {
											done[x3][y3][z3] = DONE_DONE;
										}else if(AIR_BLOCKS.contains(thisblock.type)) {
											//need to mark air as needing to be converted
											done[x3][y3][z3] = DONE_FORCEAIR;
										}
									}
								}
							}
						}
						
					}
				}
			}
		}
		
		appendProgressNow(100.0);
		return new AppendVars(cmdc,OX,OY,OZ);
	}
	
	private static class CloneData implements Cloneable{
		int sx,sy,sz;
		int dx,dy,dz;
		int xSize,ySize,zSize;
		
		public CloneData clone() {
			try {
				return (CloneData)super.clone();
			} catch (CloneNotSupportedException e) {
			}
			return null;
		}
	}
	
	
	
	private void doClone(SchematicData data,ArrayList<String> cloneCmds, byte[][][] done, int ox, int oy, int oz) {
		//this will attempt to find duplicate areas of the schematic, and set up clone commands
	
		appendTextNow("Finding Clone Areas...");
		appendTextNow(" 0%");
		long progressTime = System.currentTimeMillis();
		long count = 0;
		
		final int MAXPASS = 5;
		int  finalcount = (data.h-MINCLONESIZE)*(data.l-MINCLONESIZE)*(data.w-MINCLONESIZE)*MAXPASS;
		
		for(int pass = MAXPASS;pass>0;pass--) {
			
			try {
				int cloneSize = pass*2;
				if(cloneSize < MINCLONESIZE) {
					cloneSize = MINCLONESIZE;
				}
				HashMap<String,Point3D> areas = new HashMap<String,Point3D>();
				System.gc();
				
				if(pass==5 && data.w*data.l>(128*128)) {
					finalcount -= (data.h-MINCLONESIZE)*(data.l-MINCLONESIZE)*(data.w-MINCLONESIZE);
					continue;
				}
				if(pass==4 && data.w*data.l>(192*192)) {
					finalcount -= (data.h-MINCLONESIZE)*(data.l-MINCLONESIZE)*(data.w-MINCLONESIZE);
					continue;
				}
				if(pass==3 && data.w*data.l>(256*256)) {
					finalcount -= (data.h-MINCLONESIZE)*(data.l-MINCLONESIZE)*(data.w-MINCLONESIZE);
					continue;
				}
				if(pass==2 && data.w*data.l>(384*384)) {
					finalcount -= (data.h-MINCLONESIZE)*(data.l-MINCLONESIZE)*(data.w-MINCLONESIZE);
					continue;
				}
			
				for(int y=0;y<data.h-cloneSize;y++) {
					for(int z=0;z<data.l-cloneSize;z++) {
						for(int x=0;x<data.w-cloneSize;x++) {
							long time = System.currentTimeMillis();
							if(time - progressTime > 5000) {
								progressTime = time;
								appendProgressNow(count/(double)(finalcount));
							}
							count++;
							
							if(done[x][y][z]!=DONE_DONE) {
								
								
								String area = getCloneAreaString(data,done,x,y,z,cloneSize);
								
								if(area!=null) {
									//valid area to clone / clone to - search for match
									
									Point3D src = areas.get(area);
									
									if(src == null) {
										//no match - add to search space
										areas.put(area, new Point3D(x,y,z));
										//System.out.println(area);
									} else {
										
										//we have a (potential) match - see if we actually match (not just block types) and if we can go bigger
								
										CloneData cd,bestClone=null;
										cd= new CloneData();
										cd.sx = src.x;
										cd.sy = src.y;
										cd.sz = src.z;
										cd.xSize = cd.ySize = cd.zSize = cloneSize;
										cd.dx=x;
										cd.dy=y;
										cd.dz=z;
										
										
										
										for( cd.ySize=cloneSize;cd.ySize<=16;cd.ySize++) {
											boolean match = true;
											for( cd.xSize=cloneSize;cd.xSize<16;cd.xSize++) {
												for( cd.zSize=cloneSize;cd.zSize<16&&match;cd.zSize++) {
													
													//make sure there is no overlap - need to check 4 bottom corners as we can't be lower
													if(cd.dy>=cd.sy && cd.dy<=cd.sy+cd.ySize && 
															(new Rectangle(cd.sx,cd.sz,cd.xSize,cd.zSize)).intersects(new Rectangle(cd.dx,cd.dz,cd.xSize,cd.zSize)) 	) {
														//dest area overlaps src, so skip (specifically, min coord of dest is inside src)
														match = false;
														continue;
													}
														
												
													match = true; 
													int blockcount=0; 
													for(int x3=cd.xSize;x3>=0&&match;x3--) {
														for(int y3=cd.ySize;y3>=0&&match;y3--) {
															for(int z3=cd.zSize;z3>=0&&match;z3--) {
																
																//check inbounds
																if(cd.sx+cd.xSize >= data.w || cd.dx+cd.xSize >=data.w ||
																		cd.sy+cd.ySize >= data.h || cd.dy+cd.ySize >= data.h ||
																		cd.sz+cd.zSize >= data.l || cd.dz+cd.zSize >= data.l ) {
																	match=false;
																}else {
																
																	Block srcblock = data.getBlockAt(cd.sx + x3,cd.sy + y3,cd.sz + z3);
																	Block dstblock = data.getBlockAt(cd.dx + x3,cd.dy + y3,cd.dz + z3);
																	
																	if(srcblock.type.equals(dstblock.type) && done[cd.dx+x3][cd.dy+y3][cd.dz+z3]!=DONE_DONE) {
																		
																		//keep track of block types as we dont want to clone if we can just fill
																		if(!AIR_BLOCKS.contains(srcblock.type)) {
																			blockcount++;
																		}
																																			
																		if(srcblock.equals(dstblock) ) {
																			//matches
																		}else {
																			match = false;
																		}
																	}else {
																		match = false;
																	}
																}
															}
														}
													}
													
													if(match && blockcount>=((cd.xSize*cd.ySize*cd.zSize)*CLONEMINBLOCKPERCENT)) {
														if(bestClone==null) {
															bestClone = cd.clone();
														}else {
															int bestSize = bestClone.xSize * bestClone.ySize * bestClone.zSize;
															int thisSize = cd.xSize * cd.ySize * cd.zSize;
															if(thisSize>bestSize) {
																bestClone = cd.clone();
															}
														}
													}
												}
												
												if(cd.zSize==MINCLONESIZE&&!match) {
													break;//didn't match at min size so no reason to go bigger
												}
											}
											if(cd.xSize==MINCLONESIZE&&cd.zSize==MINCLONESIZE&&!match) {
												break;//didn't match at min size so no reason to go bigger
											}
										}
		
										//if bestClone != null, we can clone an area
										if(bestClone!=null) {
											
											String cmd = "clone ~"+(bestClone.sx+ox)+" ~"+(bestClone.sy+oy)+" ~"+(bestClone.sz+oz)+
													" ~"+(bestClone.sx+bestClone.xSize-1+ox)+" ~"+(bestClone.sy+bestClone.ySize-1+oy)+" ~"+(bestClone.sz+bestClone.zSize-1+oz)+
													" ~"+(bestClone.dx+ox)+" ~"+(bestClone.dy+oy)+" ~"+(bestClone.dz+oz);
											cloneCmds.add(cmd);
											
											//now mark that area as done
											for(int x3=bestClone.dx;x3<bestClone.dx+bestClone.xSize;x3++) {
												for(int y3=bestClone.dy;y3<bestClone.dy+bestClone.ySize;y3++) {
													for(int z3=bestClone.dz;z3<bestClone.dz+bestClone.zSize;z3++) {
														done[x3][y3][z3] = DONE_DONE;
													}
												}
											}
											//System.out.println(cmd);
										}else {
											//System.out.println("Near match at "+cd.sx+","+cd.sy+","+cd.sz+" - "+cd.dx+","+cd.dy+","+cd.dz);
										}
		
									}
								}
							}
						}
					}
				}
			}catch(OutOfMemoryError ome) {
				//areas should now be out of scope
				System.gc();
				appendTextNow("*** OUT OF MEMORY LOOKING FOR DUPLICATE AREAS ***");
			}
		}
		appendProgressNow(1.0);
		appendTextNow(" found "+cloneCmds.size()+" clone locations");
		System.gc();
		return;
	}
	
	
	private void doCloneBig(SchematicData data,ArrayList<String> cloneCmds, byte[][][] done, int ox, int oy, int oz) {
		//this will attempt to find duplicate areas of the schematic, and set up clone commands
		//this is optimized for large schematics, to take less memory, but also take quite a bit longer.
		//this only remembers the blocks for 1 y - level at a time, comparing other y - levels to it.
	
		appendTextNow("Finding Clone Areas...");
		appendTextNow(" 0%");
		
		int cloneSize=3;
		int max = (data.h*(data.h+1))/2;
		int count = 0;
		
		for(int ystart=0;ystart<data.h-cloneSize;ystart++) {
			
			HashMap<String,Point3D> areas = new HashMap<String,Point3D>();
		
			for(int y=ystart;y<data.h-cloneSize;y++) {
				appendProgressNow(count/(double)(max));
				count++;
			
				for(int z=0;z<data.l-cloneSize;z++) {
					for(int x=0;x<data.w-cloneSize;x++) {
						
						if(done[x][y][z]!=DONE_DONE) {
							
							String area = getCloneAreaString(data,done,x,y,z,cloneSize,true);
							
							if(area!=null) {
	
								Point3D src = areas.get(area);
								
								if(src == null) {
									//no match - add to search space if we are on the start y level
									if(y == ystart) {
										areas.put(area, new Point3D(x,y,z));
										//System.out.println(area);
									}
									
								} else {
									
									//from here on, this is a duplicate of the original clone method
									
									//we have a (potential) match - see if we actually match (not just block types) and if we can go bigger
							
									CloneData cd,bestClone=null;
									cd= new CloneData();
									cd.sx = src.x;
									cd.sy = src.y;
									cd.sz = src.z;
									cd.xSize = cd.ySize = cd.zSize = cloneSize;
									cd.dx=x;
									cd.dy=y;
									cd.dz=z;
									
									
									
									for( cd.ySize=cloneSize;cd.ySize<=16;cd.ySize++) {
										boolean match = true;
										for( cd.xSize=cloneSize;cd.xSize<16;cd.xSize++) {
											for( cd.zSize=cloneSize;cd.zSize<16&&match;cd.zSize++) {
												
												//make sure there is no overlap - need to check 4 bottom corners as we can't be lower
												if(cd.dy>=cd.sy && cd.dy<=cd.sy+cd.ySize && 
														(new Rectangle(cd.sx,cd.sz,cd.xSize,cd.zSize)).intersects(new Rectangle(cd.dx,cd.dz,cd.xSize,cd.zSize)) 	) {
													//dest area overlaps src, so skip (specifically, min coord of dest is inside src)
													match = false;
													continue;
												}
													
											
												match = true; 
												int blockcount=0; 
												for(int x3=cd.xSize;x3>=0&&match;x3--) {
													for(int y3=cd.ySize;y3>=0&&match;y3--) {
														for(int z3=cd.zSize;z3>=0&&match;z3--) {
															
															//check inbounds
															if(cd.sx+cd.xSize >= data.w || cd.dx+cd.xSize >=data.w ||
																	cd.sy+cd.ySize >= data.h || cd.dy+cd.ySize >= data.h ||
																	cd.sz+cd.zSize >= data.l || cd.dz+cd.zSize >= data.l ) {
																match=false;
															}else {
															
																Block srcblock = data.getBlockAt(cd.sx + x3,cd.sy + y3,cd.sz + z3);
																Block dstblock = data.getBlockAt(cd.dx + x3,cd.dy + y3,cd.dz + z3);
																
																if(srcblock.type.equals(dstblock.type) && done[cd.dx+x3][cd.dy+y3][cd.dz+z3]!=DONE_DONE) {
																	
																	//keep track of block types as we dont want to clone if we can just fill
																	if(!AIR_BLOCKS.contains(srcblock.type)) {
																		blockcount++;
																	}
																	
																	if(srcblock.equals(dstblock)) {
																		//matches
																	}else {
																		match = false;
																	}
																}else {
																	match = false;
																}
															}
														}
													}
												}
												
												if(match && blockcount>=((cd.xSize*cd.ySize*cd.zSize)*CLONEMINBLOCKPERCENT)) {
													if(bestClone==null) {
														bestClone = cd.clone();
													}else {
														int bestSize = bestClone.xSize * bestClone.ySize * bestClone.zSize;
														int thisSize = cd.xSize * cd.ySize * cd.zSize;
														if(thisSize>bestSize) {
															bestClone = cd.clone();
														}
													}
												}
											}
											
											if(cd.zSize==MINCLONESIZE&&!match) {
												break;//didn't match at min size so no reason to go bigger
											}
										}
										if(cd.xSize==MINCLONESIZE&&cd.zSize==MINCLONESIZE&&!match) {
											break;//didn't match at min size so no reason to go bigger
										}
									}
	
									//if bestClone != null, we can clone an area
									if(bestClone!=null) {
										
										String cmd = "clone ~"+(bestClone.sx+ox)+" ~"+(bestClone.sy+oy)+" ~"+(bestClone.sz+oz)+
												" ~"+(bestClone.sx+bestClone.xSize-1+ox)+" ~"+(bestClone.sy+bestClone.ySize-1+oy)+" ~"+(bestClone.sz+bestClone.zSize-1+oz)+
												" ~"+(bestClone.dx+ox)+" ~"+(bestClone.dy+oy)+" ~"+(bestClone.dz+oz);
										cloneCmds.add(cmd);
										
										//now mark that area as done
										for(int x3=bestClone.dx;x3<bestClone.dx+bestClone.xSize;x3++) {
											for(int y3=bestClone.dy;y3<bestClone.dy+bestClone.ySize;y3++) {
												for(int z3=bestClone.dz;z3<bestClone.dz+bestClone.zSize;z3++) {
													done[x3][y3][z3] = DONE_DONE;
												}
											}
										}
										//System.out.println(cmd);
									}else {
										//System.out.println("Near match at "+cd.sx+","+cd.sy+","+cd.sz+" - "+cd.dx+","+cd.dy+","+cd.dz);
									}
	
								}
							}
						}
					}
				}
			}
		}
		appendProgressNow(1.0);
		appendTextNow(" found "+cloneCmds.size()+" clone locations");
		System.gc();
		return;
	}
	
	private String getCloneAreaString(SchematicData data, byte[][][] done, int x, int y, int z, int cloneSize ) {
		return getCloneAreaString( data, done, x, y, z, cloneSize, false );
	}
	
	private String getCloneAreaString(SchematicData data, byte[][][] done, int x, int y, int z, int cloneSize, boolean uncompressed ) {
		//returns a string representing the blocks on a 3x3x3 area (this just represents the block ids, not all the block data, but useful for eliminating different areas) 
		//returns null if not a valid clone area (has done blocks, only one block type besides air or >30% air)
		StringBuilder str = new StringBuilder(); 
		//byte[] src = new byte[cloneSize*cloneSize*cloneSize];
		//int pos=0;
		
		HashSet<Block> blockmap = new HashSet<Block>();
		int blockcount=0; 
		boolean valid=true;
		for( int yy=0;yy<cloneSize&&valid;yy++) {
			for( int xx=0;xx<cloneSize&&valid;xx++) {
				for( int zz=0;zz<cloneSize&&valid;zz++) {
					
					if(done[x+xx][y+yy][z+zz]!=DONE_DONE) {
						
						Block block = data.getBlockAt(x+xx,y+yy,z+zz);
						//keep track of block types as we dont want to clone if we can just fill
						if(!AIR_BLOCKS.contains(block)) {
							blockmap.add(block);
							blockcount++;
						}
					
						//str.append((char)(blockid+256));
						//src[pos] = (byte)blockid;
						//pos++;
						str.append(" "+block.type.myId);
					}else {
						valid=false;
					}
				}
			}
		}
		
		if(!valid || blockmap.size()<((cloneSize+1)/2) || blockcount < ((cloneSize*cloneSize*cloneSize)*(CLONEMINBLOCKPERCENT)) ) {
			return null;
		} else {
			
			if(uncompressed || (cloneSize < 5 && data.volume < (96*96*96)) || 
					(cloneSize < 4 && data.volume < (128*128*128)) || 
					data.volume < (80*80*80)){
				return str.toString(); //if small enough - don't worry about compression (quicker this way)
			}
			
			
			//ok, we have a string that represents this area of blocks, but we want to 'compress' this to as small of a string as possible.
			//we are going to try RLE and maybe also zip?
			
			/*
			char c ='\0'; 
			int cnt = 0;
			StringBuilder newstr = new StringBuilder();
			for(int i=0;i<str.length();i++) {
				char cc = str.charAt(i);
				if(c!=cc) {
					if(c!='\0') {
						newstr.append(c);
						if(cnt>1) {
							newstr.append((char)cnt);
						}
					}
					c=cc;
					cnt=1;
				}else {
					cnt++;
				}
			}
			if(c!='\0') {
				newstr.append(c);
				if(cnt>1) {
					newstr.append((char)cnt);
				}
			}
			*/
			
			java.util.zip.Deflater comp = new java.util.zip.Deflater(9, true);
			//byte[] src = new byte[str.length()];
			//for(int i=0;i<str.length();i++) {
			//	src[i] = (byte)(str.charAt(i)-256);
			//}
			comp.setInput(str.toString().getBytes());
			comp.finish();
			byte[] out = new byte[256];
			int size = comp.deflate(out);
			String zipped = new String(out,0,size);
			
			//return newstr.toString();
			return zipped;
		}
	}

	
/*  This was my first attempt, basically brute force attempt to find duplicate areas.  Took way too long due to too many comparisons	
	private void doClone(SchematicData data,ArrayList<String> cloneCmds, short[][][] done, int ox, int oy, int oz) {
		//this will attempt to find duplicate areas of the schematic, and set up clone commands
	
		appendTextNow("Finding Clone Areas...");
		appendTextNow(" 0%");
		long progressTime = System.currentTimeMillis();
		long count = 0;
		
		
		for(int y=0;y<data.h-MINCLONESIZE;y++) {
			for(int z=0;z<data.l-MINCLONESIZE;z++) {
				for(int x=0;x<data.w-MINCLONESIZE;x++) {
					long time = System.currentTimeMillis();
					if(time - progressTime > 5000) {
						progressTime = time;
						appendProgressNow(count/(double)(data.h*data.l*data.w));
					}
					count++;
					
					if(done[x][y][z]!=DONE_DONE) {
						int block = getBlockAt(x,y,z);
						int issue = materialIssue[block];

						if(issue == -1 || (issue & 31) != 0) {
							//if air or block with an issue, don't fill with this block
							continue;
						}
						
						CloneData cd,bestClone=null;
						cd= new CloneData();
						cd.sx = x;
						cd.sy = y;
						cd.sz = z;
						cd.xSize = cd.ySize = cd.zSize = MINCLONESIZE;
						
						//check if valid clone src (more than 1 block type, not counting air, and < 30% air
						HashSet<Integer> blockmap1 = new HashSet<Integer>();
						int blockcount1=0; 
						for(int x4=0;x4<cd.xSize;x4++) {
							for(int y4=0;y4<cd.ySize;y4++) {
								for(int z4=0;z4<cd.zSize;z4++) {
									int srcblock = getBlockAt(cd.sx + x4,cd.sy + y4,cd.sz + z4);
									//keep track of block types as we dont want to clone if we can just fill
									if(srcblock!=0) {
										blockmap1.add(srcblock);
										blockcount1++;
									}
								}
							}
						}
						
						if(blockmap1.size()<2 || blockcount1<19) {
							//only one type of block (better as a fill) or too much air (a different start location would be better for cloneing from (one with less air))
							continue;
						}
						
						
						//find a matching destination location - only check higher locations, as we can't match lower (as it would have already been found with src and dst swapped)
						for(int yy=y;yy<data.h-MINCLONESIZE;yy++) {
							
							time = System.currentTimeMillis();
							if(time - progressTime > 5000) {
								progressTime = time;
								appendProgressDoubleNow(count/(double)(data.h*data.l*data.w), yy/(double)(data.h-y));
							}
							
							
							for(int zz=0;zz<data.l-MINCLONESIZE;zz++) {
								for(int xx=0;xx<data.w-MINCLONESIZE;xx++) {
									
									cd.dx=xx;
									cd.dy=yy;
									cd.dz=zz;
									bestClone=null;
									
									int srcblock = getBlockAt(cd.sx,cd.sy,cd.sz);
									int dstblock = getBlockAt(cd.dx,cd.dy,cd.dz);
									
									if(srcblock == dstblock && done[cd.dx][cd.dy][cd.dz]!=DONE_DONE) {
										//initial block matches and isn't done - see if area matches
									
									
										for( cd.ySize=MINCLONESIZE;cd.ySize<=16;cd.ySize++) {
											boolean match = true;
											for( cd.xSize=MINCLONESIZE;cd.xSize<16;cd.xSize++) {
												for( cd.zSize=MINCLONESIZE;cd.zSize<16&&match;cd.zSize++) {
													
													//make sure there is no overlap - need to check 4 bottom corners as we can't be lower
													if(cd.dy>=cd.sy && cd.dy<=cd.sy+cd.ySize && 
															(new Rectangle(cd.sx,cd.sz,cd.xSize,cd.zSize)).intersects(new Rectangle(cd.dx,cd.dz,cd.xSize,cd.zSize)) 	) {
														//dest area overlaps src, so skip (specifically, min coord of dest is inside src)
														match = false;
														continue;
													}
														
													
																			
													HashSet<Integer> blockmap = new HashSet<Integer>();
												
													match = true; 
													int blockcount=0; 
													for(int x3=cd.xSize;x3>=0&&match;x3--) {
														for(int y3=cd.ySize;y3>=0&&match;y3--) {
															for(int z3=cd.zSize;z3>=0&&match;z3--) {
																
																//check inbounds
																if(cd.sx+cd.xSize >= data.w || cd.dx+cd.xSize >=data.w ||
																		cd.sy+cd.ySize >= data.h || cd.dy+cd.ySize >= data.h ||
																		cd.sz+cd.zSize >= data.l || cd.dz+cd.zSize >= data.l ) {
																	match=false;
																}else {
																
																	srcblock = getBlockAt(cd.sx + x3,cd.sy + y3,cd.sz + z3);
																	dstblock = getBlockAt(cd.dx + x3,cd.dy + y3,cd.dz + z3);
																	
																	if(srcblock == dstblock && done[cd.dx+x3][cd.dy+y3][cd.dz+z3]!=DONE_DONE) {
																		
																		//keep track of block types as we dont want to clone if we can just fill
																		if(srcblock!=0) {
																			blockmap.add(srcblock);
																			blockcount++;
																		}
																	
																		int srcbdata = getDataAt(cd.sx + x3,cd.sy + y3,cd.sz + z3);
																		int dstbdata = getDataAt(cd.dx + x3,cd.dy + y3,cd.dz + z3);
																		String srcbdstr = null; 
																		String dstbdstr = null;
																		if(data.format == Format.STRUCTURE) {
																			srcbdstr = getDataStringAt(cd.sx + x3,cd.sy + y3,cd.sz + z3);
																			dstbdstr = getDataStringAt(cd.dx + x3,cd.dy + y3,cd.dz + z3);
																		}
																		
																		if(srcblock == dstblock && srcbdata == dstbdata && (srcbdstr==null || srcbdstr.equals(dstbdstr)) ) {
																			//matches
																		}else {
																			match = false;
																		}
																	}else {
																		match = false;
																	}
																}
															}
														}
													}
													
													if(match && blockmap.size()>0 && blockcount>((cd.xSize*cd.ySize*cd.zSize)*0.6)) {
														if(bestClone==null) {
															bestClone = cd.clone();
														}else {
															int bestSize = bestClone.xSize * bestClone.ySize * bestClone.zSize;
															int thisSize = cd.xSize * cd.ySize * cd.zSize;
															if(thisSize>bestSize) {
																bestClone = cd.clone();
															}
														}
													}
												}
												
												if(cd.zSize==MINCLONESIZE&&!match) {
													break;//didn't match at min size so no reason to go bigger
												}
											}
											if(cd.xSize==MINCLONESIZE&&cd.zSize==MINCLONESIZE&&!match) {
												break;//didn't match at min size so no reason to go bigger
											}
										}

									}
									
									//if bestClone != null, we can clone an area
									if(bestClone!=null) {
										
										String cmd = "clone ~"+(bestClone.sx+ox)+" ~"+(bestClone.sy+oy)+" ~"+(bestClone.sz+oz)+
												" ~"+(bestClone.sx+bestClone.xSize-1+ox)+" ~"+(bestClone.sy+bestClone.ySize-1+oy)+" ~"+(bestClone.sz+bestClone.zSize-1+oz)+
												" ~"+(bestClone.dx+ox)+" ~"+(bestClone.dy+oy)+" ~"+(bestClone.dz+oz);
										cloneCmds.add(cmd);
										
										//now mark that area as done
										for(int x3=bestClone.dx;x3<bestClone.dx+bestClone.xSize;x3++) {
											for(int y3=bestClone.dy;y3<bestClone.dy+bestClone.ySize;y3++) {
												for(int z3=bestClone.dz;z3<bestClone.dz+bestClone.zSize;z3++) {
													done[x3][y3][z3] = DONE_DONE;
												}
											}
										}
										System.out.println(cmd);
									}

								}
							}
						}
					}
				}
			}
		}
		appendProgressNow(100.0);
		return;
	}
*/
	
	private void showText(StringBuilder sb) {
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				out.setText(sb.toString());
				setCursor(Cursor.getDefaultCursor());
				out.setCursor(Cursor.getDefaultCursor());
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						jsp.getHorizontalScrollBar().setValue(0);
						jsp.getVerticalScrollBar().setValue(0);
					}
				});
			}
			
		});
	}
	
	private void hollowOut(SchematicData data) {
		
		Block AIR = new Block();
		AIR.type = BLOCK_TYPES.get("air");
		AIR.properties = "";
		AIR.compound = new TagCompound("air");
		
		int h = data.h,hh = h-1;
		int l = data.l,ll = l-1;
		int w = data.w,ww = w-1;
		boolean[][][] clear = new boolean[w][h][l];
		
		//step 1, iterate through the schematic, and mark blocks to remove if all sides are not transparent blocks
		for(int y=1;y<hh;y++) {
			for(int z=1;z<ll;z++) {
				for(int x=1;x<ww;x++) {
		
					clear[x][y][z] = checkHollow(data,x,y,z);
					
				}
			}
		}
		
		//step 2, remove marked blocks to clear
		for(int y=1;y<hh;y++) {
			for(int z=1;z<ll;z++) {
				for(int x=1;x<ww;x++) {
		
					if (clear[x][y][z]) {
						
						data.setBlockAt(x,y,z,AIR);
					}
					
				}
			}
		}
		
	}
	
	private boolean checkHollow(SchematicData data, int x, int y, int z) {
		
		boolean transparent = (data.getBlockAt(x-1,y,z).type.isTransparent() || data.getBlockAt(x+1,y,z).type.isTransparent() ||
				data.getBlockAt(x,y-1,z).type.isTransparent() || data.getBlockAt(x,y+1,z).type.isTransparent() ||
				data.getBlockAt(x,y,z-1).type.isTransparent() || data.getBlockAt(x,y,z+1).type.isTransparent() );
		
		//System.out.println(""+!transparent+" \t"+materials[getBlockAt(x-1,y,z)]+" \t"+materials[getBlockAt(x+1,y,z)]+" \t"
		//		+materials[getBlockAt(x,y-1,z)]+" \t"+materials[getBlockAt(x,y+1,z)]+" \t"
		//		+materials[getBlockAt(x,y,z-1)]+" \t"+materials[getBlockAt(x,y,z+1)]+" \t");
		
		return !transparent;
				
		
	}
	
	
	private AppendVars doRails(ArrayList<String> cmds, StringBuilder psngrs, int cmdc, int OX, int OY, int OZ, int ox, int oy, int oz, int ow, int oh, int ol, int oy1, byte[][][] done) {
		
		//iterate through the structure, placing any rail that we deem safe to place (either have no rails touching them, only one rail next to them which they are connected to, or connected to the rail we just placed and another rail, which we will place next)
		//iterate until we place no more rails (at which point, we should have placed all rails, or any remaining rails are 'impossible' to place
		//this is because rails will update their orientation when a rail is put down next to them, unless both sides are already connected.
		//so they basically have to be placed in order along the direction of travel, if there are cases were there are multiple rails in parrellel next to each other.
		//cases of intersections (rails line joining at a corner, whether controled by redstone or not) need to be placed with the rails directly connected to the corner placed before the rail pointing at the corner but not connected, in order to prevent the corner from turning the wrong way.
		
		int h = data.h;
		int l = data.l;
		int w = data.w;
		
		//this is basically a list of rails we would have liked to place when following a rail line, but couldn't (because they ended at a location where it wan't 'safe' to place them.  Without this list, they would get added back, but perhaps not in the order we found them in, which could lead to a prior rail's shange getting changed due to a neighbor being placed before the rail that we found first due to following lines.
		ArrayList<Point3D> unplacedRails = new ArrayList<Point3D>();
		
		int placecount = 1;
		while(placecount > 0) {
			
			placecount = 0;
			
			for(int y=0;y<h;y++) {
				for(int z=0;z<l;z++) {
					for(int x=0;x<w;x++) {
						
						if(done[x][y][z]!=DONE_DONE) {
							Block block = data.getBlockAt(x,y,z);
							
							if(block.type.isRail()) {
								//we have a rail!
								int data = getRailData(x, y, z);
								if(canPlaceRail(x, y, z, block, done,null,true)) {
									
									//place rail
									AppendVars v =  encodeBlock(done, ox, oy, oz, x, y, z, block, false, cmds, psngrs, cmdc, OX, OY, OZ, ow, oh, ol, oy1);
									cmdc = v.cmdc;
									OX = v.OX;
									OY = v.OY;
									OZ = v.OZ;
									placecount++;
									
									
									//then follow and end (if end isn't placed yet) until not safe to place
									Stack<NextRail> nextRail = new Stack<NextRail>();
									
									//add this rail pos and both it's directions to the stack
									//then call 'follow rail' with top of stack until stack is empty
									NextRail nr1 = new NextRail(x,y,z,data,true);
									NextRail nr2 = new NextRail(x,y,z,data,false);
									nextRail.push( nr1 );
									nextRail.push( nr2 );
									
									
									//System.out.println("Starting rail line at "+x+", "+y+", "+z+"  going "+nr1.dir.name()+" and "+nr2.dir.name());
									
									
									while(!nextRail.isEmpty()) {
										v = followRail(nextRail,cmds, psngrs, cmdc, OX, OY, OZ, ox, oy, oz, ow, oh, ol, oy1, done, unplacedRails);
										cmdc = v.cmdc;
										OX = v.OX;
										OY = v.OY;
										OZ = v.OZ;
									}
								}
								
							}
						}
					}
				}
			}
			
		}
		
		//ok, we have followed all the lines that we can, now just place the rest of the rail (if any).  The may not come out right, but it is the best we can do without placing and deleting rails to get the rail shapes we need.
		//actually, place any rails we found by following line, but couldn't place, in the order we found them - but only if they have all neighbors placed already (and therefore 
		int count = 1;
		while(count > 0) {
			count = 0;

			for(Point3D p:unplacedRails) {
				int x = p.x;
				int y = p.y;
				int z = p.z;
				if(done[x][y][z]!=DONE_DONE) {
					Block block = data.getBlockAt(x,y,z);
	
					if(block.type.isRail()) {
						
						if(isNeighborRailPlaced(x,y,z,done)) {
							
							//we have a rail - need to place it
							//place rail
							AppendVars v =  encodeBlock(done, ox, oy, oz, x, y, z, block, false, cmds, psngrs, cmdc, OX, OY, OZ, ow, oh, ol, oy1);
							cmdc = v.cmdc;
							OX = v.OX;
							OY = v.OY;
							OZ = v.OZ;
							count++;
						}
					}
				}
			}
		}
		//now recheck all block to make sure we didn't miss any
		for(int y=0;y<h;y++) {
			for(int z=0;z<l;z++) {
				for(int x=0;x<w;x++) {
					
					if(done[x][y][z]!=DONE_DONE) {
						Block block = data.getBlockAt(x,y,z);
						
						if(x==2 && y==2&& z==4) {
							System.out.println("here");
						}

						if(block.type.isRail()) {
							//we have a rail - need to place it
							//place rail
							AppendVars v =  encodeBlock(done, ox, oy, oz, x, y, z, block, false, cmds, psngrs, cmdc, OX, OY, OZ, ow, oh, ol, oy1);
							cmdc = v.cmdc;
							OX = v.OX;
							OY = v.OY;
							OZ = v.OZ;
						}
					}
				}
			}
		}
							
							
		
		return new AppendVars(cmdc, OX, OY, OZ);
	}



	private AppendVars followRail(Stack<NextRail> nextRails, ArrayList<String> cmds, StringBuilder psngrs, int cmdc, int OX, int OY, int OZ, int ox, int oy, int oz, int ow, int oh, int ol, int oy1, byte[][][] done, ArrayList<Point3D> unplacedRails) {
		NextRail nr = nextRails.pop();
		
		Point3D point = nr.getNextRailPos();
		
		
		boolean alreadyleveledOut = false;
		if(point.y > data.h && (nr.dir.isSlope()) ) {
			point.y--;
			alreadyleveledOut = true;
		}else if(point.y < 0 && (nr.dir.isSlope())) {
			point.y++;
			alreadyleveledOut = true;
		}

		
		Block block = null;
		if(point.y>=0 && point.y<data.h && point.z>=0 && point.z<data.l && point.x>=0 && point.x<data.w ) {
			block = data.getBlockAt(point.x, point.y, point.z);
		}else {
			//System.out.println("Stopping following line at "+point.x+", "+point.y+", "+point.z+"  - out of bounds");
			return new AppendVars(cmdc, OX, OY, OZ);
		}
		
		//check that this block is a rail.  if not, check the block below, as we may be at the start of a slope down OR check the block the block able, as we may be at the end of a slope down. if neither are rails, we have reached the end of the line, return.
		if(!alreadyleveledOut && !block.type.isRail()) {
			if( !nr.dir.isSlope() ) {
				//not slope, but next isn't a rail, check block below to see as we may be at top of a slope down
				point.y--;
				if(point.y >=0) {
					block = data.getBlockAt(point.x, point.y, point.z);
					if(!block.type.isRail()) {
						//System.out.println("Stopping following line at "+point.x+", "+point.y+", "+point.z+"  - can't find next rail");
						return new AppendVars(cmdc, OX, OY, OZ);
					}
				}else {
					//System.out.println("Stopping following line at "+point.x+", "+point.y+", "+point.z+"  - out of bounds below");
					return new AppendVars(cmdc, OX, OY, OZ);
				}
			}else {
				//we are a slope, but next is not a rail, so we may have leveled off, check block above if it is a rail
				point.y++;
				if(point.y < data.h) {
					block = data.getBlockAt(point.x, point.y, point.z);
					if(!block.type.isRail()) {
						//System.out.println("Stopping following line at "+point.x+", "+point.y+", "+point.z+"  - can't find next rail");
						return new AppendVars(cmdc, OX, OY, OZ);
					}
				}else {
					//System.out.println("Stopping following line at "+point.x+", "+point.y+", "+point.z+"  - out of bounds above");
					return new AppendVars(cmdc, OX, OY, OZ);
				}
			}
		}
		
		//ok, at this point we have a rail at point.xyz, don't continue if already done
		if(done[point.x][point.y][point.z]==DONE_DONE) {
			//System.out.println("Stopping following line at "+point.x+", "+point.y+", "+point.z+"  - rail already done");
			return new AppendVars(cmdc, OX, OY, OZ);
		}
		
		
		int bdata = getRailData(point.x, point.y, point.z);
		
		
		RailDirections nextDir = railDataDir[bdata][0];
		if(nextDir.canBeOpposite( nr.dir ) ) {
			//this points back at previous so can't be direction to next
			nextDir = railDataDir[bdata][1];
		}else if(!nr.dir.canBeOpposite( railDataDir[bdata][1] ) ) {
			//problem, neither of this tracks directions point back!
			System.out.println("Neither direction points to previous track!");
		}
		
		
		if(canPlaceRail(point.x,point.y,point.z,block,done,nextDir) ) {
			
			//place rail
			Point3D p = point;//nr.getNextRailPos();
			AppendVars v =  encodeBlock(done, ox, oy, oz, p.x, p.y, p.z, block, false, cmds, psngrs, cmdc, OX, OY, OZ, ow, oh, ol, oy1);
			cmdc = v.cmdc;
			OX = v.OX;
			OY = v.OY;
			OZ = v.OZ;
			
			
			//add other side of new rail to nextRails
			int nextData = getRailData(p.x,p.y,p.z);
			NextRail nr1 = new NextRail(p.x,p.y,p.z,nextData,true);
			NextRail nr2 = new NextRail(p.x,p.y,p.z,nextData,false);
			
			Point3D nr1n = nr1.getNextRailPos();
			Point3D nr2n = nr2.getNextRailPos();
			if(nr1n.x == nr.point.x && (nr1n.y == nr.point.y || (nr1n.y +1)== nr.point.y || (nr1n.y -1)== nr.point.y ) && nr1n.z == nr.point.z) {
				//nr1 is the previous rail, so nr2 is the next rail
				nextRails.push( nr2 );
				
				//System.out.println("Continuing rail line at "+p.x+", "+p.y+", "+p.z+"  going "+nr2.dir.name());
				
			}else if(nr2n.x == nr.point.x && (nr2n.y == nr.point.y || (nr2n.y +1)== nr.point.y || (nr2n.y -1)== nr.point.y) && nr2n.z == nr.point.z) {
				//nr2 is the previous rail, so nr1 is the next rail
				nextRails.push( nr1 );
				
				//System.out.println("Continuing rail line at "+p.x+", "+p.y+", "+p.z+"  going "+nr1.dir.name());
			}else {
				//this should never be hit
				System.out.println("following rail but can't find previous rail!");
			}
		}else {
			//System.out.println("Stopping following line at "+point.x+", "+point.y+", "+point.z);
			unplacedRails.add(point);
		}
		
		return  new AppendVars(cmdc, OX, OY, OZ);
	}

	private boolean canPlaceRail(int x, int y, int z, Block block, byte[][][] done, RailDirections nextdir) {
		return canPlaceRail(x, y, z, block, done, nextdir, false);
	}
	
	private boolean canPlaceRail(int x, int y, int z, Block block, byte[][][] done, RailDirections nextdir,boolean start) {
		//check to make sure that the only rails near this rail are either connected to this one, or are already placed
		
		/*
			step 1: check in the directions this rail goes. if there are rails already placed that we can connect to, then SAFE TO PLACE
			
			step 2: check there are only rail neighbors that we point to (and that point back), then SAFE TO PLACE 
		
			step 3:  ???  need to figure out O shaped tracks with no gap
			/---\
			\---/
			Actually, these should be handled if we do rail line following, so ignore for now
		
			
			step 2 adjustment
			if nextdir is non-null (we are following a rail line, then we only care that if the block this rail is pointing to (in nextdir direction)
			 is a rail, that it is pointing back, we don't care about other neighbors.
		
		*/
		
		//step 1
		int bDataMod = getRailData(block );
		//int[] dirsX = railDataX[bDataMod];
		//int[] dirsY = railDataY[bDataMod];
		//int[] dirsZ = railDataZ[bDataMod];
		//boolean isSlope = railDataSlope[bDataMod];
		RailDirections[] dirs = railDataDir[bDataMod];
		
		boolean ok = true;
		for(int i=0;i<2;i++) {
			
			int xx = x + dirs[i].getOffset().x;
			int yy = y + dirs[i].getOffset().y;
			int zz = z + dirs[i].getOffset().z;
			
			if(yy>=0 && yy<data.h && zz>=0 && zz<data.l && xx>=0 && xx<data.w ) {
				//valid location to check
			
				Block destBlock = data.getBlockAt(xx,yy,zz);
				if(!destBlock.type.isRail() && dirs[i].isSlope() && i==1) {
					yy++;
					destBlock = data.getBlockAt(xx,yy,zz);
				}
				if(destBlock.type.isRail()) {
					//ok, we are pointing at a rail block, is it placed?
					
					//Actually, I'm not sure that we care if it is placed, only that if we point to it, it points back to us
					//if(done[xx][yy][zz]==DONE_DONE) {
						
						
						//it is placed, does it point back to us?
						
						int destData =  getRailData(destBlock);
						
						//int[] destX = railDataX[bDataMod];
						//int[] destZ = railDataZ[bDataMod];
						RailDirections[] destDirs = railDataDir[destData];//was bDataMod, but should be destData?  Put back if complex rails break
						
						if( (xx+destDirs[0].getOffset().x==x && zz+destDirs[0].getOffset().z==z) || 
								(xx+destDirs[1].getOffset().x==x && zz+destDirs[1].getOffset().z==z) ) {
							//rail we point to points back at us!
							ok = true;
						}else {
							ok=false;
							break;
						}
					
					//}else {
					//	ok=false;
					//	break;
					//}
				}else {
					ok=false;
					break;
				}
				
			}else {
				ok=false;
				break;
			}
		}
		
		
		boolean hasAnyBadNeighbors = false;
		if(!ok) {
			
		
			//step 2
			if(nextdir!=null) {
				//we are following this line, so we only care about the next block, that if it is a rail, that it is pointing back
				
				Point3D p = nextdir.getOffset().add(new Point3D(x,y,z));
				Block destblock = null;
				if(p.y>=0 && p.y<data.h && p.z>=0 && p.z<data.l && p.x>=0 && p.x<data.w ) {
					destblock = data.getBlockAt(p.x,p.y,p.z);
				}
				if(destblock != null && nextdir.getOffset().y == -1 && !destblock.type.isRail()) {
					//this rail is a slope, but slope stops - possibly - check horizontal block
					p.y++;
					if(p.y>=0 && p.y<data.h && p.z>=0 && p.z<data.l && p.x>=0 && p.x<data.w ) {
						destblock = data.getBlockAt(p.x,p.y,p.z);
					}
				}
				if(destblock != null && destblock.type.isRail()) {
					//check that it points back
					int destDataMod =  getRailData(destblock );
					Point3D itPoint1 = (new Point3D(p.x,p.y,p.z).add(railDataDir[destDataMod][0].getOffset()));
					Point3D itPoint2 = (new Point3D(p.x,p.y,p.z).add(railDataDir[destDataMod][1].getOffset()));
					boolean itPointsAtUs = (itPoint1.x == x && itPoint1.z == z) || (itPoint2.x == x && itPoint2.z == z);
					hasAnyBadNeighbors = !itPointsAtUs;
				}
				
				
			}else if(!start){
				//not following a rail, need to make sure there are no other rails pointing at this one.
				for(int yy=y-1;yy<y+2;yy++) {
					for(int i=0;i<4;i++) {
						
						int xx=x;
						int zz=z;
						switch(i) {
							case 0:{
								zz--;
								break;
							}
							case 1:{
								xx--;
								break;
							}
							case 2:{
								xx++;
								break;
							}
							case 3:{
								zz++;
								break;
							}
						}
						
						
						if(yy>=0 && yy<data.h && zz>=0 && zz<data.l && xx>=0 && xx<data.w ) {
							//valid location to check
							
							//does this location have a rail, and if so is this a rail that we connect to?
							Block blockat = data.getBlockAt(xx,yy,zz);
							if(blockat.type.isRail()) {
								//has a rail
								
								boolean itIsPlaced = done[xx][yy][zz]==DONE_DONE;
								
								//if it is placed, 
								if(!itIsPlaced) {
								
									boolean wePointAtIt;
									boolean itPointsAtUs;
								
									Point3D wePoint1 = (new Point3D(x,y,z).add(railDataDir[bDataMod][0].getOffset()));
									Point3D wePoint2 = (new Point3D(x,y,z).add(railDataDir[bDataMod][1].getOffset()));
									wePointAtIt = (wePoint1.x == xx && wePoint1.z == zz) || (wePoint2.x == xx && wePoint2.z == zz);  
								
									int destDataMod = getRailData(blockat );
									Point3D itPoint1 = (new Point3D(xx,yy,zz).add(railDataDir[destDataMod][0].getOffset()));
									Point3D itPoint2 = (new Point3D(xx,yy,zz).add(railDataDir[destDataMod][1].getOffset()));
									itPointsAtUs = (itPoint1.x == x && itPoint1.z == z) || (itPoint2.x == x && itPoint2.z == z);  
									
									if(!wePointAtIt || !itPointsAtUs) {
										hasAnyBadNeighbors = true;
									}
								}
							}
							
						}
					}
				}
			}else {
				hasAnyBadNeighbors = true;
			}
		}
		
		
		return ok || !hasAnyBadNeighbors;
	}
	
	/** returns true if all the neighbor block to rail x,y,z are already placed, or wouldn't be effecting by placing a rail at */
	private boolean isNeighborRailPlaced(int x, int y, int z, byte[][][] done) {
		int[] offsetx = {-1,0,0,1};
		int[] offsetz = {0,-1,1,0};
		
		//src is the block at x,y,z, while dest (lower down) is the block we are examining
		int srcRailData = getRailData(data.getBlockAt(x,y,z));
		RailDirections[] srcDirs = railDataDir[srcRailData];
		
		
		//check the four neighbor directions from x,y,z to see if they are a rail
		for(int yy=y-1;yy<y+2;yy++) {
			for(int i=0;i<offsetx.length;i++) {
				
				int xx = x + offsetx[i];
				int zz = z + offsetz[i];
				
				if(yy>=0 && yy<data.h && zz>=0 && zz<data.l && xx>=0 && xx<data.w ) {
					//valid location to check
		
					//is this location a rail
					Block destBlock = data.getBlockAt(xx,yy,zz);
					if(destBlock.type.isRail()) {
						
						//does it point at us and we point at it?
						int destData =  getRailData(destBlock);
						RailDirections[] destDirs = railDataDir[destData];
						
						if( 
								//dest point at src
								((xx+destDirs[0].getOffset().x==x && zz+destDirs[0].getOffset().z==z) || 
								(xx+destDirs[1].getOffset().x==x && zz+destDirs[1].getOffset().z==z))
								
								&&
								
								//src point at dest
								((x+srcDirs[0].getOffset().x==xx && z+srcDirs[0].getOffset().z==zz) || 
								(x+srcDirs[1].getOffset().x==xx && z+srcDirs[1].getOffset().z==zz))
								
								) {
							//rail we point to points back at us! - this is valid, so no need to check further in this direction
	
						}else {
							
							//either src doesn't point to dest, or dest doesn't point to src
							//we need to make sure that the rails that dest points to are already placed (and that dest itself is placed)
							//as if they are already placed, then placing src wont cause them to be modified (as all already placed tracks are 'safe')
							
							if(done[xx][yy][zz]!=DONE_DONE)
								return false;
							
							int xxx[] = {xx+destDirs[0].getOffset().x, xx+destDirs[1].getOffset().x};
							int zzz[] = {zz+destDirs[0].getOffset().z, zz+destDirs[1].getOffset().z};
							
							for(int h = 0;h<xxx.length;h++) {
								for(int yyy = yy-1;yyy<yy+2;yyy++) {
									if(yy>=0 && yy<data.h && zz>=0 && zz<data.l && xx>=0 && xx<data.w ) {
										//is valid location to check
									
										//is this location a rail
										Block dest2 = data.getBlockAt(xxx[h],yyy,zzz[h]);
										if(dest2.type.isRail()) {
											
											//does it point at us and we point at it?
											int dest2Data =  getRailData(dest2);
											RailDirections[] dest2Dirs = railDataDir[dest2Data];
											
											if( 
													//dest point at src
													((xxx[h]+dest2Dirs[0].getOffset().x==xx && zzz[h]+dest2Dirs[0].getOffset().z==zz) || 
													(xxx[h]+dest2Dirs[1].getOffset().x==xx && zzz[h]+dest2Dirs[1].getOffset().z==zz))

													) {
												//rail we point to points back at us! - this is valid if placed
												
												if(done[xxx[h]][yyy][zzz[h]]!=DONE_DONE)
													return false;
						
											}else {
												//this dest rail can be modified by placing src rail, so don't for now (this may be corrected after placing some other rails)
												return false;
											}
											
										}
									}
								}
							}	
							
						}
						
					}
				}
			}
		}
		//if we reach this point, all block in the area are already placed, or point into this block, so won't get modified when this block is placed
		return true;
		
		
		/* This turns out not to work at certain 4 way 'intersections' (corners with rails in all 4 directions), the above should work better.
		 
		//check in a diamond shape, up to two block from current block and a level above and below (but never this position) for any unplaced rails that can be effected
		//or specifically, if every rail in that are is placed, then the rails that could connect to this location shouldn't be modified by placing this rail
		int[] xoffset = {0,0,0,0,	1,1,1,	-1,-1,-1,	2,-2};
		int[] zoffset = {-2,-1,1,2,	-1,0,1,	-1,0,1,		0,0};
		
		for(int yy=y-1;yy<y+2;yy++) {
			for(int i=0;i<zoffset.length;i++) {
				
				int xx=x + xoffset[i];
				int zz=z + zoffset[i];
				
				if(yy>=0 && yy<data.h && zz>=0 && zz<data.l && xx>=0 && xx<data.w ) {
					//valid location to check
					
					//does this location have a rail
					Block blockat = getBlockAt(xx,yy,zz);
					if(blockat.type.isRail()) {
						//has a rail
						//is it already placed?
						if(done[xx][yy][zz]!=DONE_DONE) {
							return false;
						};
					}
				}
			}
		}
		return true;
		*/
	}
	

	static class AppendVars {
		int cmdc;
		int OX,OY,OZ;
		
		public AppendVars(int c,int x, int y, int z) {
			cmdc = c;
			OX = x;
			OY = y;
			OZ = z;
		}
	}
	
	
	/** 
	 * This adds a command to the current commands for a command block, setting up a new command block if needed
	 * 
	 * @param cmds		the current list of command block commands
	 * @param psngrs	the commands being collected for the next command block
	 * @param psngr		the new command to add
	 * @param cmdc		the current count of commands  (isn't this just cmds.length()?)
	 * @param OX		the X offset from this command block to the location where the command block minecart is spawned
	 * @param OY		the Y offset from this command block to the location where the command block minecart is spawned
	 * @param OZ		the Z offset from this command block to the location where the command block minecart is spawned
	 * @param ox		the X coordinate for the command (setblock or fill)
	 * @param oy		the Y coordinate for the command (setblock or fill)
	 * @param oz		the Z coordinate for the command (setblock or fill)
	 * @param ow		the width or secondary X coordinate for the command (usually fill)
	 * @param oh		the height or secondary Y coordinate for the command (usually fill)
	 * @param ol		the length or secondary Z coordinate for the command (usually fill)
	 * @param oy1		the secondary Y coordinate for the command (used when creating a base material floor for the build)
	 * @return			an AppendVars object containing the current list of complete commands (is this needed, won't the passed in cmds array be modified?) and the current command block offset coordinates (only modified if a new command was created)
	 */
	private AppendVars appendPassenger(ArrayList<String> cmds,StringBuilder psngrs,String psngr, int cmdc, int OX, int OY, int OZ, int ox, int oy, int oz, int ow, int oh, int ol, int oy1) {
		data.cmdCount++;
		if(psngr != null) {
			//could be null if it was a dangerous block we are not converting
			
			if(data.outputType == 1) {
				cmds.add(psngr);
			} else {
				psngr = psngrCMD.replace("%CMD%", escapeQuotesSlash(psngr));
			
				if(psngrs.length() + psngr.length() < maxMainCommandLength) {
					//append this passenger to the list of passengers
					if(psngrs.length()>0) {
						psngrs.append(',');
					}
					psngrs.append(psngr);
				}else {
					//no more room, end this command, create a new command, and add psngr to new passenger list
					
					//end psngrs list
					if(psngrs.length()>0) {
						psngrs.append(',');
					}
					psngrs.append(psngrMidCleanup);
					
					//create command
					String c;
					if(cmdc==0 && cmds.size()==0) {
						c = cmdStartFirst;
					}else {
						c = cmdStartOther;
					}
					
					c = replaceCoords(c,OX,OY,OZ,ox,oy,oz,ow,oh,ol,oy1).replace("%MINECARTS%", psngrs);
					//add command
					cmds.add(c);
					psngrs.setLength(0);
					
					//setup for next command
					cmdc++;
					//check if new line needed
					boolean multiline = data.maxCmdBlockLineLength > 0;
					int moreCmdsIndex = moreCmds.getSelectedIndex();
					if(multiline && cmdc>=data.maxCmdBlockLineLength) {
						//new command block line needed.  reset cmdc to 0
						cmdc = 0;
					}
					// 	offset in direction for next cmd		offset in direction based upon line of commands (assuming multiple lines are being used, or 0 if off)
					OX = moreCmdsX[moreCmdsIndex]*cmdc + ((multiline)?(-newLineCmdsX[moreCmdsIndex])*(cmds.size()/data.maxCmdBlockLineLength):0);
					OZ = moreCmdsZ[moreCmdsIndex]*cmdc + ((multiline)?(-newLineCmdsZ[moreCmdsIndex])*(cmds.size()/data.maxCmdBlockLineLength):0);
					OY = 5*((chain.isSelected()?cmdc+1:1)) + ((multiline)?newLineCmdsY*(cmds.size()/data.maxCmdBlockLineLength):0);
					if(moreCmds.getSelectedIndex()==9) {
						//minecart - always 1,1
						OX = 1;
						OZ = 1;		
					}
					
					psngrs.append(psngr);
				}
			}
		}
		
		return new AppendVars(cmdc, OX, OY, OZ);
	}
	

	/**
	 * Encodes a single block into a minecart command block
	 * @param done the array of completed blocks
	 * @param ox the x offset
	 * @param oy the y offset
	 * @param oz the z offset
	 * @param x the x position
	 * @param y the y position
	 * @param z the z position
	 * @param block
	 * @param noFill
	 * @return
	 */
	private AppendVars encodeBlock(byte[][][] done, int ox, int oy, int oz, int x, int y, int z, Block block, boolean noFill, 
			ArrayList<String> cmds,StringBuilder psngrs, int cmdc, int OX, int OY, int OZ, int ow, int oh, int ol, int oy1 ) {
		
		String cmd = null;
		StringBuilder dataTag = new StringBuilder();
		
		if(noDangerousBlocks.isSelected() && ArrayUtils.contains(dangerousBlocks, block.type.name)) {
			return new AppendVars(cmdc, OX, OY, OZ);
		}
		
		if(block.type.hasInventory() || noFill) {
			getTileEntityData(dataTag,block);
		}else {
			cmd = getFill(data, block ,x,y,z,done,ox,oy,oz);
		}
		if(cmd==null) {
			if(dataTag.length()>0) {
				cmd = "setblock ~"+(x+ox)+" ~"+(y+oy)+" ~"+(z+oz)+" "+block+"{"+dataTag.toString()+"}";
			}else {
				cmd = "setblock ~"+(x+ox)+" ~"+(y+oy)+" ~"+(z+oz)+" "+block;
			}
		}
		
		
		
//		if(cmd.contains("\"") && !(cmd.startsWith("\"") && cmd.endsWith("\"")) ) {
//			cmd = "\""+StringEscapeUtils.escapeJava(cmd)+"\"";
//		}
		//String psngr = psngrCMD.replace("%CMD%", cmd);
		
		AppendVars v = appendPassenger(cmds, psngrs, cmd, cmdc, OX, OY, OZ, ox, oy, oz, ow, oh, ol, oy1);
		cmdc = v.cmdc;
		OX = v.OX;
		OY = v.OY;
		OZ = v.OZ;
		
		done[x][y][z]=DONE_DONE;
		
		if( block.type.isMultiblock()) {// (materialIssue[block]&4)>0) {
						
			//if mat contains "_door" and it is a door bottom (data&8 == 0) - need to immediately add top of door, so they don't accidentally get split up in different groups.
			//if((y+1)<data.h && ( (mat.endsWith("_door") && (dataVal&8)==0) || (mat.equals("double_plant") && (dataVal&8)==0) ) ) {
			if((y+1)<data.h && ( (DOOR_BLOCKS.contains(block.type) && block.properties.contains("half=lower") )  || 
					(DOUBLEPLANT_BLOCKS.contains(block.type) && block.properties.contains("half=lower") ) ) ) {
				//encode the top of the door (or double plant) together
				Block bl = data.getBlockAt(x,y+1,z);
				if(bl.type.equals(block.type)) {
					v = encodeBlock(done, ox, oy, oz, x, y+1, z, bl, noFill, cmds, psngrs, cmdc, OX, OY, OZ, ow, oh, ol, oy1);
					cmdc = v.cmdc;
					OX = v.OX;
					OY = v.OY;
					OZ = v.OZ;
				}
			}
			//similar for beds, we want to place the other half of the bed just after the first half
			if(block.type.name.equals("bed")) {
				if(block.properties.contains("part=foot") ) {
					//foot of bed was just done, need to do head
					if(block.properties.contains("facing=south") ) {
						//facing is south, so head is south
						if((z+1)<data.l && done[x][y][z+1]!=DONE_DONE) {
							Block bl = data.getBlockAt(x,y,z+1);
							//if(materials[bl].equals(mat)) {
								v = encodeBlock(done, ox, oy, oz, x, y, z+1, bl, noFill, cmds, psngrs, cmdc, OX, OY, OZ, ow, oh, ol, oy1);
								cmdc = v.cmdc;
								OX = v.OX;
								OY = v.OY;
								OZ = v.OZ;
							//}
						}
					}else if(block.properties.contains("facing=east") ) {
						//facing is west, so head is east
						if(x>0 && done[x-1][y][z]!=DONE_DONE) {
							Block bl = data.getBlockAt(x-1,y,z);
							//if(materials[bl].equals(mat)) {
								v = encodeBlock(done, ox, oy, oz, x-1, y, z, bl, noFill, cmds, psngrs, cmdc, OX, OY, OZ, ow, oh, ol, oy1);
								cmdc = v.cmdc;
								OX = v.OX;
								OY = v.OY;
								OZ = v.OZ;
							//}
						}
					}else if(block.properties.contains("facing=north") ) {
						//facing is north, so head is north
						if(z>0 && done[x][y][z-1]!=DONE_DONE) {
							Block bl = data.getBlockAt(x,y,z-1);
							//if(materials[bl].equals(mat)) {
								v = encodeBlock(done, ox, oy, oz, x, y, z-1, bl, noFill, cmds, psngrs, cmdc, OX, OY, OZ, ow, oh, ol, oy1);
								cmdc = v.cmdc;
								OX = v.OX;
								OY = v.OY;
								OZ = v.OZ;
							//}
						}
					}else if(block.properties.contains("facing=west") ) {
						//facing is east, so head is west
						if((x+1)<data.w && done[x+1][y][z]!=DONE_DONE) {
							Block bl = data.getBlockAt(x+1,y,z);
							//if(materials[bl].equals(mat)) {
								v = encodeBlock(done, ox, oy, oz, x+1, y, z, bl, noFill, cmds, psngrs, cmdc, OX, OY, OZ, ow, oh, ol, oy1);
								cmdc = v.cmdc;
								OX = v.OX;
								OY = v.OY;
								OZ = v.OZ;
							//}
						}
					}
				}else {
					//head of bed was just done, need to do foot
					if(block.properties.contains("facing=south") ) {
						//head is south, so foot is north
						if(z>0 && done[x][y][z-1]!=DONE_DONE) {
							Block bl = data.getBlockAt(x,y,z-1);
							//if(materials[bl].equals(mat)) {
								v = encodeBlock(done, ox, oy, oz, x, y, z-1, bl, noFill, cmds, psngrs, cmdc, OX, OY, OZ, ow, oh, ol, oy1);
								cmdc = v.cmdc;
								OX = v.OX;
								OY = v.OY;
								OZ = v.OZ;
							//}
						}
					}else if(block.properties.contains("facing=east") ) {
						//head is west, so foot is east
						if((x+1)<data.w && done[x+1][y][z]!=DONE_DONE) {
							Block bl = data.getBlockAt(x+1,y,z);
							//if(materials[bl].equals(mat)) {
								v = encodeBlock(done, ox, oy, oz, x+1, y, z, bl, noFill, cmds, psngrs, cmdc, OX, OY, OZ, ow, oh, ol, oy1);
								cmdc = v.cmdc;
								OX = v.OX;
								OY = v.OY;
								OZ = v.OZ;
							//}
						}
					}else if(block.properties.contains("facing=north") ) {
						//head is north, so foot is south
						if((z+1)<data.l && done[x][y][z+1]!=DONE_DONE) {
							Block bl = data.getBlockAt(x,y,z+1);
							//if(materials[bl].equals(mat)) {
								v = encodeBlock(done, ox, oy, oz, x, y, z+1, bl, noFill, cmds, psngrs, cmdc, OX, OY, OZ, ow, oh, ol, oy1);
								cmdc = v.cmdc;
								OX = v.OX;
								OY = v.OY;
								OZ = v.OZ;
							//}
						}
					}else if(block.properties.contains("facing=west") ) {
						//head is east, so foot is west
						if(x>0 && done[x-1][y][z]!=DONE_DONE) {
							Block bl = data.getBlockAt(x-1,y,z);
							//if(materials[bl].equals(mat)) {
								v = encodeBlock(done, ox, oy, oz, x-1, y, z, bl, noFill, cmds, psngrs, cmdc, OX, OY, OZ, ow, oh, ol, oy1);
								cmdc = v.cmdc;
								OX = v.OX;
								OY = v.OY;
								OZ = v.OZ;
							//}
						}
					}
				}		
			}
			
		}
		
		return new AppendVars(cmdc, OX, OY, OZ);
	}

	
	private String getFill(SchematicData data, Block block , int xs, int ys, int zs, byte[][][] done, int ox, int oy, int oz) {
		
		//finds biggest group of connected blocks of the same type that can be created with a single fill command
		int bestcount=0,bestx=xs,besty=ys,bestz=zs;
		//Block block = getBlockAt(xs,ys,zs);

		
		if(block.type.isMultiblock() || block.type.isNoFill()) {
			//adding doors as fills doesn't work as putting the bottom without the top being the next change seems to cause the door to 'pop off' or fail to place
			//similar for beds and apparently tall grass
			return null;
		}
		
		
		for(int i=0;i<testdir.length;i++) {
			String test = testdir[i];
			int count = 0;
			int x=xs,y=ys,z=zs;
			for(int j=0;j<test.length();j++) {
				
				char dir = test.charAt(j);
				
				switch(dir) {
				
					case 'x':{
						while((x+1)<data.w && areAllSameBlock(block, xs,ys,zs,x+1,y,z,done)) {
							x=x+1;
							
							count = ((x+1)-xs) * ((y+1)-ys) * ((z+1)-zs);
							if(count > MAXFILLSIZE) {
								//we've gone beyond max fill size, so step back one
								x = x - 1;
								break;
							}
						}
						break;
					}
					case 'y':{
						while((y+1)<data.h && areAllSameBlock(block, xs,ys,zs,x,y+1,z,done)) {
							y=y+1;
							
							count = ((x+1)-xs) * ((y+1)-ys) * ((z+1)-zs);
							if(count > MAXFILLSIZE) {
								//we've gone beyond max fill size, so step back one
								y = y - 1;
								break;
							}
						}
						break;
					}
					case 'z':{
						while((z+1)<data.l && areAllSameBlock(block, xs,ys,zs,x,y,z+1,done)) {
							z=z+1;
							
							count = ((x+1)-xs) * ((y+1)-ys) * ((z+1)-zs);
							if(count > MAXFILLSIZE) {
								//we've gone beyond max fill size, so step back one
								z = z - 1;
								break;
							}
						}
						break;
					}
				
				}
				
				
			}
			
			//ok, expanded as much as possible in each direction, is this better than best?
			count = ((x+1)-xs) * ((y+1)-ys) * ((z+1)-zs);
			if(count > bestcount && count < (MAXFILLSIZE + 1) ) {
				bestcount = count;
				bestx = x;
				besty = y;
				bestz = z;
			}
		}
		
		
		if(bestcount>1) {
			//more than one block - fill instead of setblock
			
			//mark blocks as done
			for(int i=xs;i<=bestx;i++) {
				for(int j=ys;j<=besty;j++) {
					for(int k=zs;k<=bestz;k++) {
						done[i][j][k] = DONE_DONE;
					}
				}
			}
			
			//return fill command
			String cmd = "fill ~"+(xs+ox)+" ~"+(ys+oy)+" ~"+(zs+oz)+" ~"+(bestx+ox)+" ~"+(besty+oy)+" ~"+(bestz+oz)+" "+block.toString();

			return cmd;
		}
		
		return null;
	}
	
	
	private boolean areAllSameBlock(Block block, int x, int y, int z, int xx, int yy, int zz, byte[][][] done) {
		for(int i=x;i<=xx;i++) {
			for(int j=y;j<=yy;j++) {
				for(int k=z;k<=zz;k++) {
					
					Block b = data.getBlockAt(i,j,k);
					
					if(!block.equals(b)) {
						return false;
					}
					
					if(AIR_BLOCKS.contains(b.type) && done[i][j][k] != DONE_FORCEAIR) {
						//don't fill air unless it is an air block we have to replace
						return false;
					}
					
				}
			}
		}
		return true;
	}

	private String replaceCoords(String str, int OX,int OY, int OZ, int ox, int oy, int oz, int ow, int oh, int ol, int oy1) {
		str = str.replace("%OX%", ""+OX);
		str = str.replace("%OY%", ""+OY);
		str = str.replace("%OZ%", ""+OZ);
		str = str.replace("%ox%", ""+ox);
		str = str.replace("%oy%", ""+oy);
		str = str.replace("%oz%", ""+oz);
		str = str.replace("%ow%", ""+ow);
		str = str.replace("%oh%", ""+oh);
		str = str.replace("%ol%", ""+ol);
		str = str.replace("%oy1%", ""+oy1);
		return str;
	}
	private String replaceCoords(String str, int OX,int OY, int OZ, int ox, int oy, int oz, int ow, int oh, int ol, int oy1,String dir) {
		str = str.replace("%OX%", ""+OX);
		str = str.replace("%OY%", ""+OY);
		str = str.replace("%OZ%", ""+OZ);
		str = str.replace("%ox%", ""+ox);
		str = str.replace("%oy%", ""+oy);
		str = str.replace("%oz%", ""+oz);
		str = str.replace("%ow%", ""+ow);
		str = str.replace("%oh%", ""+oh);
		str = str.replace("%ol%", ""+ol);
		str = str.replace("%oy1%", ""+oy1);
		str = str.replace("%dir%", dir);
		return str;
	}

	public int getCoord(int x, int y, int z, int w, int h, int l) {
		return y*w*l + z*w + x;
	}
	
	public int getCoordStruct(int x, int y, int z, int w, int h, int l) {
		//return x*h + y + z*h*w;  //MCEdit order?
		return x + y*w*l + z*w;	//Minecraft generated structure order?
	}
	
	
	
	private void buildBlockCache() {
		//block don't appear to be in any particular order (mcedit seems to produce an order, but not actual minecraft exported structures)
		//so build a hashmap allowing quicker lookup of blocks
		for(TagCompound bl : data.blockList) {
			try {
				List<TagInteger> pos = bl.getList("pos", TagInteger.class);
				int x = pos.get(0).getValue();
				int y = pos.get(1).getValue();
				int z = pos.get(2).getValue();
				
				int stateIdx = bl.getInteger("state");
				if(stateIdx>=0) {
					TagCompound state = data.palette.get(stateIdx);
					String name = state.getString("Name");
					
					if(name.startsWith("minecraft:")) {
						name = name.substring(10);
					}
					
					Block b = new Block();
					b.type = BLOCK_TYPES.get(name);
					
					if(b.type == null) {
						System.out.println("Unknown block type found: "+state.getString("Name"));
						appendTextNow("Unknown block type found: "+state.getString("Name"));
						BlockType t = new BlockType(name,false,true,true,false,true,false,true);//assume the worst
						BLOCK_TYPES.put(name, t);
						b.type = t;
					}
					
					StringBuilder sb = new StringBuilder();
					Map<String, ITag> children = state.getTags();
					if(children.containsKey("Properties")){
						TagCompound properties = state.getCompound("Properties");
						boolean first = true;
						for(ITag prop : properties.getTags().values()) {
							if(prop instanceof TagString) {
								String val = ((TagString) prop).getValue();
								//optimization to throw away 'false' properties, assuming false is the default for all 'boolean' properties
								if(!val.equals("false")) {
									
									//some block optimization
									if(name.equals("redstone_wire") && ignoreWirePower.isSelected() ) {
										if(prop.getName().equals("power"))
											continue;
										if(val.equals("none")) //this will reduce the wire shape properties to only those actually needed
											continue;
									}
									
									if(!first) {
										sb.append(",");
									}else {
										first = false;
									}
									sb.append(prop.getName());
									sb.append("=");
									sb.append(val);
								}
							}
						}
					}
					b.properties = sb.toString();
					b.compound = bl;
					
					data.setBlockAt(x, y, z, b);
				}
			}catch(TagNotFoundException|UnexpectedTagTypeException e) {
				e.printStackTrace();
			}
		}
		
	}
	
	/*
	/ ** gets the block data (0-15 block data) for the block - typically alt version of the block * /
	public int getDataAt(int x, int y, int z) {
		if(data.format == Format.SCHEMATIC) {
			return data.data[getCoord(x,y,z,data.w,data.h,data.l)];
		}else {
			//TODO -Ugh, need to parse block properties, etc.  For now, just return -1 and call getDataStringAt
		}
		return -1;
	}
	*/
	
	//
	
	
	
	//returns the array position in RailDirections that represents this block
	public int getRailData(Block block) {
		//return (block == 66) ? data : data & 7;
		if(block.properties.contains("shape=north_south"))
			return 0;
		if(block.properties.contains("shape=east_west"))
			return 1;
		if(block.properties.contains("shape=ascending_east"))
			return 2;
		if(block.properties.contains("shape=ascending_west"))
			return 3;
		if(block.properties.contains("shape=ascending_north"))
			return 4;
		if(block.properties.contains("shape=ascending_south"))
			return 5;
		if(block.properties.contains("shape=south_east"))
			return 6;
		if(block.properties.contains("shape=south_west"))
			return 7;
		if(block.properties.contains("shape=north_west"))
			return 8;
		if(block.properties.contains("shape=north_east"))
			return 9;
		return 0;
	}
	
	public int getRailData(int x, int y, int z) {
		return getRailData(data.getBlockAt(x,y,z));
	}


	private void getTileEntityData(StringBuilder sb, Block block) {
		if(block.compound != null) {
			try {
				getTileEntityData(sb, block.compound.getCompound("nbt"));
			} catch (UnexpectedTagTypeException e) {
				//ignore - bad tile entity data
			} catch (TagNotFoundException e) {
				//ignore - bad tile entity data
			}
		}
	}
	private void getTileEntityData(StringBuilder sb, TagCompound te) {
		boolean needComma = false;
		for(ITag t:te.getTags().values()) {
			String name = t.getName();
			if("x".equals(name) || "y".equals(name) || "z".equals(name) || "id".equals(name) )
				continue;
			StringBuilder result = writeTag(t);
			if(result!=null) {
				if(needComma) 
					sb.append(",");
				else
					needComma = true;
				sb.append(result);
			}
		}
	}
	
	
	public StringBuilder writeTag( ITag t) {
		
		return writeTag(t,true,false,false,false,false,false);
	}
	
	public StringBuilder writeTag( ITag t,boolean ignorezero,boolean isItems,boolean isPotion,boolean isSpawnEgg, boolean isWrittenBook, boolean escape) {
		StringBuilder sb = new StringBuilder();
		String name = t.getName();
		
		//optimize entities
		if(minimizeEntities.isSelected()) {
			if(name != null && (name.equals("Air") || name.equals("Fire") || name.equals("FallDistance") || name.equals("Invulnerable") ||
					name.equals("PortalCooldown") || name.contains("UUID") || name.equals("CommandStats") || name.equals("ItemDropChance") ||
					name.equals("Attributes") || name.equals("HandDropChances") || name.equals("ArmorDropChances") || name.equals("Motion") || 
					name.equals("DropChances") || name.equals("PersistentId")) ){
				return null;
			}
		}
		
		if(name==null) {
			
		}
		
//		if(name!=null && name.equalsIgnoreCase("pages")) {
//			t=t;
//		}
		
		switch (t.getTagID()){
			case 1:{
				TagByte tag = (TagByte)t;
				if(!ignorezero || tag.getValue()!=0) {
					if(name!=null&&name.length()>0) {
						if(name.equalsIgnoreCase("dir")){
							//ignore, should be a direction that uses the same values as current 'facing'
							return null;
						}else if(name.equalsIgnoreCase("direction")) {//fix for paintings from 1.8 or earlier
							sb.append("Facing:");
						}else {
							sb.append(name);
							sb.append(":");
						}
					}
					sb.append(tag.getValue());
				}
				break;
			}
			case 2:{
				TagShort tag = (TagShort)t;
				if(!ignorezero || tag.getValue()!=0) {
					if(name!=null&&name.length()>0) {
						sb.append(name);
						sb.append(":");
					}
					
					
					if(isItems && name.equals("id")) {
						//sb.append(itemIdToStirng(tag.getValue()));
						sb.append(tag.getValue());
					}else /*if(isPotion && name.equals("Damage")) {
						sb.setLength(0);
						sb.append("tag:{Potion:");
						sb.append(getPotionName(tag.getValue()));
						sb.append("}");
					}else if(isSpawnEgg && name.equals("Damage")) {
						sb.setLength(0);
						sb.append("tag:{EntityTag:{id:");
						sb.append(mobNames.get((int)tag.getValue()));
						sb.append("}}");
					}else */{
						sb.append(tag.getValue());
						
					}
				}
				break;
			}
			case 3:{
				TagInteger tag = (TagInteger)t;
				if(!ignorezero || tag.getValue()!=0) {
					if(name!=null&&name.length()>0) {
						sb.append(name);
						sb.append(":");
					}
					sb.append(tag.getValue());
				}
				break;
			}
			case 4:{
				TagLong tag = (TagLong)t;
				if(!ignorezero || tag.getValue()!=0) {
					if(name!=null&&name.length()>0) {
						sb.append(name);
						sb.append(":");
					}

					
					sb.append(tag.getValue());
					sb.append("L");
				}
				break;
			}
			case 5:{
				TagFloat tag = (TagFloat)t;
				if(!ignorezero || tag.getValue()!=0) {
					
					if(name!=null&&name.length()>0) {
						sb.append(name);
						sb.append(":");
					}
					sb.append(tag.getValue());
					sb.append("f");
				}
				break;
			}
			case 6:{
				TagDouble tag = (TagDouble)t;
				if(!ignorezero || tag.getValue()!=0) {
					
					if(name!=null&&name.length()>0) {
						sb.append(name);
						sb.append(":");
					}
					sb.append(tag.getValue());
				}
				break;
			}
			case 7:{
				TagByteArray tag = (TagByteArray)t;
				byte[] bytes = tag.getValue();
				if(!ignorezero || bytes.length!=0) {
					if(name!=null&&name.length()>0) {
						sb.append(name);
						sb.append(":");
					}
					sb.append("[");
					boolean first = true;
					for(byte b:bytes) {
						if(first) {
							first=false;
						}else {
							sb.append(",");
						}
						sb.append(b);
					}
					sb.append("]");
				}
				break;
			}
			case 8:{
				TagString tag = (TagString)t;
				String str = tag.getValue();
				//if(name!=null && name.equalsIgnoreCase("customname")) {
				//	name = name+"";
				//}
				if(str!=null && str.contains("\\u")) {
					try {
						str = StringEscapeUtils.unescapeJava(str);
					}catch(IllegalArgumentException e) {
						System.out.println("Bad string tag found! Name:"+name+"  Value:"+str);
					}
				}
				if(name!=null && name.equals("LastOutput"))
					break;//not working, but not needed - skip
				if(!ignorezero || str.length()>0) {
					if(name!=null&&name.length()>0) {
						sb.append(name);
						sb.append(":");
					}
					
					if(name!=null && (name.equals("Text1")||name.equals("Text2")||name.equals("Text3")||name.equals("Text4")) ) {
						//apparently signs now always use the full '/tellraw' format in 1.9, where previously the were simply plain text for the most part - need to convert old signs.
						if(str==null || str.length()==0 || str.equals("\"\"") || str.equals("null")) {
							return null;
						}else if(!str.contains("\":\"")) {
							//doens't seem to be JSON, so assume plain text and convert to JSON
							if(!str.startsWith("\"") || !str.endsWith("\"")) {
								str = "\""+str+"\"";
							}
							str = "{\"text\":"+str+"}";
						}
						sb.append("\"");
						//sb.append(str.replace("\"", "\\\""));
						sb.append(escapeQuotesSlash(str));//was StringEscapeUtils.escapeJava(StringEscapeUtils.escapeJava())
						sb.append("\"");
					}else /*if(name!=null && name.equals("EntityId")) {
						//mob id for spawner - now inside a SpawnData compound tag. 
						sb.setLength(0);
						sb.append("SpawnData:{id:");
						int i=-1;
						try {
							i = Integer.parseInt(str);
						}catch(NumberFormatException nfe) {};
						if(i>-1) {
							sb.append(mobNames.get(i));
						}else {
							sb.append(str);
						}
						sb.append("}");
					}else */ if(name!=null && name.equals("id")) {
						if(str.startsWith("minecraft:")) {
							str = str.substring(10);
						}
						if(!str.startsWith("\"") || !str.endsWith("\"")) {
							str = "\""+str+"\"";
						}
						sb.append(str);//was StringEscapeUtils.escapeJava
					} else {
					
						sb.append("\"");
						sb.append(escapeQuotesSlash(str));//was StringEscapeUtils.escapeJava
						sb.append("\"");
					}
					
				}
				break;
			}
			case 9:{
				TagList tag = (TagList)t;
				List<ITag> l = tag.getTags();
				if(!ignorezero || l.size()>0) {
					if(name!=null&&name.length()>0) {
						sb.append(name);
						sb.append(":");
					}
					
					//optimize entities
					//if(minimizeEntities.isSelected()) { 
						
						if( name.equals("HandItems") || name.equals("ArmorItems") ) {
							//check to see if all are empty, and if so, ignore
							boolean any = false;
							
							for(ITag tt:l) {
								StringBuilder result = writeTag(tt,true,false,false,false,false,false);
								if(result!=null) {
									any = true;
								}
							}
							
							if(!any)
								return null;
						}
						if( name.equals("Equipment") ) {
							//pre 1.9 tag - convert to HandItems (first item, second hand item should be blank), and ArmorItems
							StringBuilder[] sbs =new StringBuilder[5];
							int count = 0;
							sb.setLength(0);
							for(ITag tt:l) {
								StringBuilder result = writeTag(tt,true,false,false,false,false,false);
								if(count<5) {
									sbs[count] = result;
								}
								count++;
							}
							if(sbs[0]!=null) {
								sb.append("HandItems:[");
								sb.append(sbs[0]);
								sb.append(",{}]");
							}
							if(sbs[1]!=null || sbs[2]!=null || sbs[3]!=null || sbs[4]!=null) {
								if(sb.length() > 0) {
									sb.append(",");
								}
								sb.append("ArmorItems:[");
								for(int i=1;i<5;i++) {
									if(i>1) {
										sb.append(",");
									}
									if(sbs[i]!=null) {
										sb.append(sbs[i]);
									}else {
										sb.append("{}");
									}
								}
								sb.append("]");
							}
							if(sb.length()>0) {
								return sb;
							}else {
								return null;
							}
						}
					//}
					
					
					boolean subItems = name.equals("Items");
					sb.append("[");
					boolean needComma = false;
					for(ITag tt:l) {
						StringBuilder result = writeTag(tt,subItems,subItems,false,false,isWrittenBook,false);
						if(result!=null) {
							if(needComma)
								sb.append(",");
							else
								needComma = true;
							
							if(name.equalsIgnoreCase("pages") && tt instanceof TagString && isWrittenBook ) {//pages works fine this way for 'written_book' but not for 'writable_book'
								//convert 'page string' to tellraw json format so newlines work in commands
								//StringBuilder raw = new StringBuilder();
								//raw.append("\"{text:");
								//raw.append(result.toString().replace("\n", "\\\\n").replace("\r", "\\\\r").replace("\t", "\\\\t"));
								//raw.append("}\"");
								//result = raw;
							}
							sb.append(result);
						}
					}
					sb.append("]");
					
				}
				break;
			}
			case 10:{
				TagCompound tag = (TagCompound)t;
				Collection<ITag> l = tag.getTags().values();
				if(!ignorezero || l.size()>0) {
					if(name!=null&&name.length()>0) {
						sb.append(name);
						sb.append(":");
					}
					sb.append("{");
					
					boolean isPot = false;
					boolean isEgg = false;
					boolean isWBook = isWrittenBook;
					if(isItems) {
						for(ITag tt:l) {
							//potion - need to convert id to name based on damage (splash used to be in data), and also convert damage to potion name
							if(tt.getName().equals("id") && ((tt.getTagID()==2 && ((TagShort)tt).getValue()==373) || 
									(tt.getTagID()==8 && (((TagString)tt).getValue().equals("minecraft:potion") || ((TagString)tt).getValue().equals("potion")) ) )) {
								isPot=true;
								for(ITag ttt:l) {
									if(ttt.getName().equals("Damage") && ttt.getTagID()==2 && ((((TagShort)ttt).getValue())&16384)>0) {
										if(tt.getTagID()==2) {
											((TagShort)tt).setValue((short)438);
										}else if( tt.getTagID()==8) {
											((TagString)tt).setValue("minecraft:splash_potion");
										}
									}
								}
							}else if(tt.getName().equals("id") && tt.getTagID()==2 && ((TagShort)tt).getValue()==383) {
								//spawn egg - need to convert damage into EntityTag
								isEgg = true;
							}else if(tt.getName().equals("id") && ((tt.getTagID()==2 && ((TagShort)tt).getValue()==387) || (tt.getTagID()==8 && 
									(((TagString)tt).getValue().equals("minecraft:written_book") || ((TagString)tt).getValue().equals("written_book"))))){
								isWBook = true;
							}
						}
					}	
					
					boolean needComma = false;
					for(ITag tt:l) {
						StringBuilder result = writeTag(tt,isItems,isItems,isPot,isEgg,isWBook,false);
						if(result!=null) {
							if(needComma)
								sb.append(",");
							else
								needComma = true;
							sb.append(result);
						}
					}
					sb.append("}");
					
				}
				break;
			}
			case 11:{
				TagIntegerArray tag = (TagIntegerArray)t;
				int[] ints = tag.getValues();
				if(!ignorezero || ints.length!=0) {
					if(name!=null&&name.length()>0) {
						sb.append(name);
						sb.append(":");
					}
					sb.append("[");
					boolean first = true;
					for(int i:ints) {
						if(first) {
							first=false;
						}else {
							sb.append(",");
						}
						sb.append(i);
					}
					sb.append("]");
					
				}
				break;
			}
		}
		
		if(sb!=null && sb.length() >0) {
			if(escape) {
				return new StringBuilder(escapeQuotesSlash(sb.toString()));
			}
			return sb;
		}
		return null;
	}
	
	public StringBuilder writeTagSimple( ITag t) {
		StringBuilder sb = new StringBuilder();
		String name = t.getName();
		
		sb.append(name);
		sb.append('=');
	
		switch (t.getTagID()){
			case 1:{
				TagByte tag = (TagByte)t;
				sb.append(tag.getValue());
				break;
			}
			case 2:{
				TagShort tag = (TagShort)t;
				sb.append(tag.getValue());
				break;
			}
			case 3:{
				TagInteger tag = (TagInteger)t;
				sb.append(tag.getValue());
				break;
			}
			case 4:{
				TagLong tag = (TagLong)t;
				sb.append(tag.getValue());
				break;
			}
			case 5:{
				TagFloat tag = (TagFloat)t;
				sb.append(tag.getValue());
				break;
			}
			case 6:{
				TagDouble tag = (TagDouble)t;
				sb.append(tag.getValue());
				break;
			}
			case 7:{
				TagByteArray tag = (TagByteArray)t;
				byte[] bytes = tag.getValue();
				sb.append("[");
				boolean first = true;
				for(byte b:bytes) {
					if(first) {
						first=false;
					}else {
						sb.append(",");
					}
					sb.append(b);
				}
				sb.append("]");
				break;
			}
			case 8:{
				TagString tag = (TagString)t;
				String str = tag.getValue();
				sb.append(escapeQuotesSlash(str));//was StringEscapeUtils.escapeJava
				break;
			}
			case 9:{
				TagList tag = (TagList)t;
				List<ITag> l = tag.getTags();
				
				sb.append("[");
				boolean needComma = false;
				for(ITag tt:l) {
					StringBuilder result = writeTag(tt,false,false,false,false,false,false);
					if(result!=null) {
						if(needComma) {
							sb.append(",");
						}else {
							needComma = true;
						}

						sb.append(result);
					}
				}
				sb.append("]");
				
				break;
			}
			case 10:{
				TagCompound tag = (TagCompound)t;
				Collection<ITag> l = tag.getTags().values();
				sb.append("{");
					
				boolean needComma = false;
				for(ITag tt:l) {
					StringBuilder result = writeTag(tt,false,false,false,false,false,false);
					if(result!=null) {
						if(needComma)
							sb.append(",");
						else
							needComma = true;
						sb.append(result);
					}
				}
				sb.append("}");
					
				
				break;
			}
			case 11:{
				TagIntegerArray tag = (TagIntegerArray)t;
				int[] ints = tag.getValues();
				sb.append("[");
				boolean first = true;
				for(int i:ints) {
					if(first) {
						first=false;
					}else {
						sb.append(",");
					}
					sb.append(i);
				}
				sb.append("]");
				break;
			}
		}

		return sb;
	}
	

	/*
	private String getPotionName(short value) {
		if(value==0) {
			return "water";
		}
		int type = value&127;
		return potions[type];
	}
	*/

	/*
	private void initItems() {
		
		for(int i=0;i<materials.length;i++) {
			itemNames.put(i,materials[i]);
		}
		
		for(int i=0;i<items.length;i++) {
			itemNames.put(i+256,items[i]);
		}
		
		itemNames.put(2256, "record_13");
		itemNames.put(2257, "record_cat");
		itemNames.put(2258, "record_blocks");
		itemNames.put(2259, "record_chirp");
		itemNames.put(2260, "record_far");
		itemNames.put(2261, "record_mall");
		itemNames.put(2262, "record_mellohi");
		itemNames.put(2263, "record_stal");
		itemNames.put(2264, "record_strad");
		itemNames.put(2265, "record_ward");
		itemNames.put(2266, "record_11");
		itemNames.put(2267, "record_wait");
		
		
		
		//init mobs
		for(Map.Entry<Integer, String>e : mobNames.entrySet()) {
			String name = e.getValue();
			if(e.getKey() < 90) {
				entityMob.add(name);
			}else {
				entityPassive.add(name);
			}
		}
	}
	*/
	
	void initEntities(){
		//entityNameMap.put("","");
		
		entityNameMap.put("AreaEffectCloud","area_effect_cloud");
		entityNameMap.put("ArmorStand","armor_stand");
		//entityNameMap.put("Cauldron","brewing_stand");
		entityNameMap.put("CaveSpider","cave_spider");
		entityNameMap.put("MinecartChest","chest_minecart");
		entityNameMap.put("Control","command_block");
		entityNameMap.put("MinecartCommandBlock","commandblock_minecart");
		entityNameMap.put("DLDetector","daylight_detector");
		entityNameMap.put("Trap","dispenser");
		entityNameMap.put("DragonFireball","dragon_fireball");
		entityNameMap.put("ThrownEgg","egg");
		entityNameMap.put("EnchantTable","enchanting_table");
		entityNameMap.put("EndGateway","end_gateway");
		entityNameMap.put("AirPortal","end_portal");
		entityNameMap.put("EnderChest","ender_chest");
		entityNameMap.put("EnderCrystal","ender_crystal");
		entityNameMap.put("EnderDragon","ender_dragon");
		entityNameMap.put("ThrownEnderpearl","ender_pearl");
		entityNameMap.put("EyeOfEnderSignal","eye_of_ender_signal");
		entityNameMap.put("FallingSand","falling_block");
		entityNameMap.put("FireworksRocketEntity","fireworks_rocket");
		entityNameMap.put("FlowerPot","flower_pot");
		entityNameMap.put("MinecartFurnace","furnace_minecart");
		entityNameMap.put("MinecartHopper","hopper_minecart");
		entityNameMap.put("EntityHorse","horse");
		entityNameMap.put("ItemFrame","item_frame");
		entityNameMap.put("RecordPlayer","jukebox");
		entityNameMap.put("LeashKnot","leash_knot");
		entityNameMap.put("LightningBolt","lightning_bolt");
		entityNameMap.put("LavaSlime","magma_cube");
		entityNameMap.put("MinecartRideable","minecart");
		entityNameMap.put("MobSpawner","mob_spawner");
		entityNameMap.put("MushroomCow","mooshroom");
		entityNameMap.put("Music","noteblock");
		entityNameMap.put("Ozelot","ocelot");
		entityNameMap.put("PolarBear","polar_bear");
		entityNameMap.put("ShulkerBullet","shulker_bullet");
		entityNameMap.put("SmallFireball","small_fireball");
		entityNameMap.put("SpectralArrow","spectral_arrow");
		entityNameMap.put("ThrownPotion","potion");
		entityNameMap.put("MinecartSpawner","spawner_minecart");
		entityNameMap.put("Structure","structure_block");
		entityNameMap.put("PrimedTnt","tnt");
		entityNameMap.put("MinecartTNT","tnt_minecart");
		entityNameMap.put("VillagerGolem","villager_golem");
		entityNameMap.put("WitherBoss","wither");
		entityNameMap.put("WitherSkull","wither_skull");
		entityNameMap.put("ThrownExpBottle","xp_bottle");
		entityNameMap.put("XPOrb","experience_orb");
		entityNameMap.put("XP_Orb","experience_orb");
		entityNameMap.put("PigZombie","zombie_pigman");
		entityNameMap.put("ender_crystal","end_crystal");
		entityNameMap.put("ItemFrame","item_frame");
		entityNameMap.put("Villager_Golem", "iron_golem");
		entityNameMap.put("Snowman", "snow_golem");
		entityNameMap.put("MinecartRidable", "minecart");
		entityNameMap.put("Evocation_Illager", "evoker");
		entityNameMap.put("Evocation_Fang", "evoker_fangs");
		entityNameMap.put("Vindication_Illager", "vindicator");
		entityNameMap.put("Illusion_Illager", "illusioner");
		entityNameMap.put("CaveSpider", "cave_spider");
		entityNameMap.put("Ozelot", "ocelot");
		entityNameMap.put("EntityHorse", "horse");
		
		
		
		List<String> keys = new ArrayList<String>(entityNameMap.keySet());
		for(String key:keys) {
			entityNameMap.put(key.toLowerCase(), entityNameMap.get(key));
		}
	};
	
	/*
	private String itemIdToStirng(int value) {
		
		String str = itemNames.get(value);
		if(str==null || str.length()==0) {
			str = ""+value;
		}
		return str;
	}
*/
	
	public void resetCopy() {
		if(data==null || data.cmds==null || data.cmds.size()<1) {
			copyNext.setEnabled(false);
		}else {
			copyNext.setEnabled(true);
			curCommand = 0;
			copyNext.setText("Copy Command "+(curCommand+1));
			resetCopy.setEnabled(true);
		}
	}
	
	public void copyNext() {
		Toolkit t = Toolkit.getDefaultToolkit();
		Clipboard c = t.getSystemClipboard();
		StringSelection stringSelection = new StringSelection(data.cmds.get(curCommand));
		c.setContents(stringSelection, null);
		
		curCommand++;
		if(curCommand<data.cmds.size()) {
			copyNext.setText("Copy Command "+(curCommand+1));
		}else {
			copyNext.setText("All Commands Copied");
			copyNext.setEnabled(false);
		}
	}
	
	static class Point3D{
		int x;
		int y;
		int z;
		
		public Point3D(int x, int y, int z) {
			this.x=x;
			this.y=y;
			this.z=z;
		}
		
		public Point3D add(Point3D p) {
			return new Point3D(x+p.x,y+p.y,z+p.z);
		}
		
		@Override
		public boolean equals(Object o) {
			if(o instanceof Point3D) {
				Point3D p = (Point3D)o;
				if(p.x==x && p.y==y && p.z==z) {
					return true;
				}
			}
			
			return false;
		}
	}
	
	public enum Format{ SCHEMATIC, STRUCTURE};  //.nbt is structure

	static class SchematicData{
		Format format = Format.SCHEMATIC;
		
		int w=0,h=0,l=0;
		int ox = 0, oy = 0, oz = 0;
		
		List<TagCompound> blockList = null;
		List<TagCompound> palette = null;
		List<TagCompound> entities = null;
		int weOriginX,weOriginY,weOriginZ;
		
		//new for 1.13 and structure support
		//HashMap<String, Block> BLOCK_CACHE = new HashMap<String, Block>();
		//HashMap<Long, Block> BLOCK_CACHE = new HashMap<Long, Block>();
		HashMap<Integer, Block> BLOCK_CACHE = new HashMap<Integer, Block>();
		
		ArrayList<String> cmds = null;
		StringBuilder out = null;
		int maxCmdBlockLineLength = 0;
		
		int outputType = 0;
		String filename = "";
		String outFile = "";
		
		//stats
		int cmdCount=0;
		int volume = 0;
		
		/*
		public void setBlockAt(int x, int y, int z, Block bl) {
			String location = ""+x+","+y+","+z;
			if(intern) {
				location = location.intern();
			}
			BLOCK_CACHE.put(location, bl);
		}
		
		/** returns the Block object for the block at the location specified 
		 * @throws TagNotFoundException 
		 * @throws UnexpectedTagTypeException * /
		public Block getBlockAt(int x, int y, int z) {
			String location = ""+x+","+y+","+z;
			if(intern) {
				location = location.intern();
			}
			return BLOCK_CACHE.get(location);
		}
		*/
		
		/*
		public void setBlockAt(int x, int y, int z, Block bl) {
			long location = (((long)y)<<40) + (((long)z)<<20) + ((long)x);
			BLOCK_CACHE.put(location, bl);
		}
		
		public Block getBlockAt(int x, int y, int z) {
			long location = (((long)y)<<40) + (((long)z)<<20) + ((long)x);
			return BLOCK_CACHE.get(location);
		}
		*/
		
		public void setBlockAt(int x, int y, int z, Block bl) {
			int location = ((y)<<24) + ((z)<<12) + (x);
			BLOCK_CACHE.put(location, bl);
		}
		
		public Block getBlockAt(int x, int y, int z) {
			int location = ((y)<<24) + ((z)<<12) + (x);
			return BLOCK_CACHE.get(location);
		}
	}

	
	/* --------------------------------------------
				expansion routines
	*/
	
	/* *
	 * Generates a command(s) that show off different terracotta patterns
	 * @param id - the block id of the terracotta to use (235 to 250, inclusive)
	 * /
	private void doTerracotta(int id, boolean complex) {
		if(id < 235 || id > 250)
			return;
		
		out.setText("Running...");
		out.setEditorKit(new StyledEditorKit());
		
		out.setDocument(new DefaultStyledDocument());
		
		out.setText("Running...");
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		out.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		
		StringBuilder sb = new StringBuilder();
		sb.append("Glazed Treeacotta: ");
		sb.append(materials[id]);
		sb.append("\n\n");
		
		if(complex) {
			moreCmds.setSelectedIndex(7);//south no space
			offsetZ.setValue((235-id) * 10 );
			//offsetZ.setValue(-20);
			
			//offsetZ.setValue(1);
			//offsetX.setValue(-1);
			//offsetY.setValue(-1);
		}
		
		//create data structure
		//1 high, 6 tall, (256+1) * 5 blocks wide (256 2x2 patterns (+1 for bonus space at end), each is tiles into a 4x4 pattern with a space between (for 5 wide each pattern))
		//complex is basically 4 times wider as we do each basic pattern with 4 rotations - not implemented yet.
		//
		//actually, 256 patterns is too long - so making eight rows of 32 patterns each
		//
		//ok, totally new approach. I'm now attempting to remove translation and rotation based duplicates (all duplicates???).
		//this results in 22 'base' 2x2 patterns.  These patterns are then combined into 4x4 blocks normally, or if complex is chosen, with a few different rotations:
		//  00	01	01	01	02	03	10	10	32
		//  00	23	32	10	20	30	23	32	01
		//
		//ok, on top of all that, I was assuming 1 = north, 2 = east, 3 = south, 4 = west, my rotations were just adding 1.  (really 0 - 4)
		//it turns out it is 1 = north, 2 = south, 3 = east, w = west.  so I need a conversion method;
		
		
		data = new SchematicData();
		
		data.w = ((22)*5)+1;
		data.h = 1;
		data.l = 5*1 + 1;
		if(complex) {
			data.l = 5*9 + 1;
		}
		data.blocks = new byte[data.w*data.h*data.l];
		data.data = new byte[data.w*data.h*data.l];
		data.tileEntities = new ArrayList<TagCompound>();
		data.entities = new ArrayList<TagCompound>();
		
		//init to same colored concrete (as it should be the nearest matching solid color)
		int x,y,z;
		y=0;
		for(x=0;x<data.w;x++) {
			for(z=0;z<data.l;z++) {
				byte color = (byte)(id - 235);
				setBlockAt(x,y,z,(byte)251);//251 = concrete
				setDataAt(x,y,z,color);
			}
		}
		
		
		//make patterns 
		int pCount = 0; //pattern count
		
		/ * Patterns will look like:
		 *  a b
		 *  c d
		 * where a,b,c,d are different directions of the same block (0-4).  The 2x2 patterns are then repeated in a 4x4 square. 
		 * This will cause duplicates (1234 is same as 3412 shifted up), but this is hard to avoid. 
		 * /
		
		boolean[][][][] done = new boolean[4][4][4][4];
		
		for(byte a=0;a<4;a++) {
			for(byte b=0;b<4;b++) {
				for(byte c=0;c<4;c++) {
					for(byte d=0;d<4;d++) {
						
						//this is and the markDone method implement the new approach to remove duplicates. This results in 22 unique patterns if translations and rotations are ignored.
						if(!done[a][b][c][d]) {
							markDone(done,a,b,c,d);
							System.out.println(""+a+""+b+""+c+""+d);
						
							makePattern(data, pCount, id, a,b,c,d, complex);
							pCount++;
						}
						
					}
				}
			}
		}
		
		//actually convert the data now.
		convert(data, sb);
		
		
		//debug
		//for(z=0;z<data.l;z++) {
		//	for(x=0;x<data.w;x++) {
		//		System.out.print(""+getDataAt(x,0,z));
		//	}
		//	System.out.println();
		//}

	}
	
	private void markDone(boolean[][][][] done, int a, int b, int c, int d) {
		// a b
		// c d
		
		//different rotations
		for(byte r=0;r<4;r++) {
			byte aa = (byte)((a+r)%4);
			byte bb = (byte)((b+r)%4);
			byte cc = (byte)((c+r)%4);
			byte dd = (byte)((d+r)%4);
		
			done[aa][bb][cc][dd] = true; //exact;
			//translate up
			done[cc][dd][aa][bb] = true;
			//translate right
			done[bb][aa][dd][cc] = true;
			//translate up-right
			done[dd][cc][bb][aa] = true;
		}
		
	}
	
	private void makePattern(SchematicData data, int patCount, int blockid, byte a, byte b, byte c, byte d, boolean complex) {
		//go over patCount * 5 + 1 and make the pattern from a,b,c,d using the block id
		//the '5' will need to be bigger for 'complex' as we will do rotate / mirror the other copies of the pattern
		
		//System.out.println("Make Pattern: "+a+""+b+""+c+""+d);
		
		int y = 0;
		int startx = (patCount) * 5 +1;
		
		//  00	01	01	01	02	03	10	10	32
		//  00	23	32	10	20	30	23	32	01
		int[][] patterns = { {0,0,0,0}, {0,1,2,3}, {0,1,3,2}, {0,1,1,0}, {0,2,2,0}, {0,3,3,0}, {1,0,2,3}, {1,0,3,2}, {3,2,0,1}};
		
		int maxpat = complex ? 9 : 1;
		
		for(int pat = 0; pat < maxpat; pat++) {
			int z = 1 + (pat)*5;
		
			//set block types
			for(int i=0;i<4;i++) {
				for(int j=0;j<4;j++) {
					setBlockAt(startx+i,y,z+j,(byte)blockid);
				}
			}
			//set block data
			for(int j=0;j<2;j++) {
				for(int i=0;i<2;i++) {
					
					int pos = (i*2) + j;
					byte r = (byte)patterns[pat][pos];
					
					byte aa = (byte)((a+(r))%4);
					byte bb = (byte)((b+(r))%4);
					byte cc = (byte)((c+(r))%4);
					byte dd = (byte)((d+(r))%4);
					
					for(int t=0;t<r;t++) {
						byte tmp = aa;
						aa = cc;
						cc = dd;
						dd = bb;
						bb = tmp;
					}
					
					setDataAt(startx+(i*2),y,z+(j*2),terracottaConvert(aa));
					setDataAt(startx+(i*2) +1,y,z+(j*2),terracottaConvert(bb));
					setDataAt(startx+(i*2),y,z+(j*2) +1,terracottaConvert(cc));
					setDataAt(startx+(i*2) +1,y,z+(j*2) +1,terracottaConvert(dd));
				}
			}
		}
		
		
	}
	
	private byte terracottaConvert(int dir) {
		/ *switch(dir) {
			default:
			case 0: return 1;
			case 1: return 3;
			case 2: return 2;
			case 3: return 4;
		}
		* /
		return (byte)(dir+1);
	}
	*/
	
	
	
	/**
	 * Generates a command to create a set of command blocks from running the schematic just converted, including flying machine to run commands. <br>
	 * Originally: only currently works for east no space, need to update for other directions.<br>
	 * Should now work for directions, but obviously not same command block or minecart command blocks 
	 * @return
	 */
	String generateEmptyCommandBlocks() {
		if(data==null || data.cmds==null || data.cmds.size()<1) {
			return "";
		}
		
		int moreCmdsIndex = moreCmds.getSelectedIndex();
		
		if(moreCmdsIndex != 1 && moreCmdsIndex != 3 && moreCmdsIndex != 5 && moreCmdsIndex != 7 ) {
			// not using one of the continuous lines of command blocks settings
			return "";
		}
		
		
		StringBuilder psngrs = new StringBuilder();
		
		if(quiet.isSelected()) {
			psngrs.append(psngrCMD.replace("%CMD%",cmdQuiet));
		}
		
		/* //orig code that made a single line of command blocks
		//add command blocks first as a fill, then every 5 facing up
		if(psngrs.length()>0) {
			psngrs.append(',');
		}
		psngrs.append(psngrCmdBlockList.replace("%len%", ""+(data.cmds.size()-1)));
		//now every 5
		for(int i=0;i<data.cmds.size();i++) {
			if(i%5==4) {
				psngrs.append(',');
				if(i%25==24) {
					psngrs.append(psngrCmdBlockSingle25.replace("%ox%",""+i));
				}else {
					psngrs.append(psngrCmdBlockSingle.replace("%ox%",""+i));
				}
			}
		}
		*/
		
		//note, the below does not check if all the commands will fit in one command block
		boolean multiline = data.maxCmdBlockLineLength > 0;
		int totalCmds = data.cmds.size();
		int groups = (multiline)?((totalCmds-1)/data.maxCmdBlockLineLength)+1:1;
		for(int grp = groups; grp > 0; grp--) {
			int length = (grp>1)?data.maxCmdBlockLineLength:(multiline)?totalCmds%(Math.max(data.maxCmdBlockLineLength,1)):totalCmds;
			int grpnumber = groups - grp;
			
			//OX, OY, OZ are the start coords of this 'line' of command blocks, ox, oz is the end coords (same Y), oyl is the facing. length is negative as the array is the offset per block to the 0,0, and we need the opposite.
			int OX = ((multiline)?newLineCmdsX[moreCmdsIndex]*(grpnumber):0);
			int OZ = ((multiline)?newLineCmdsZ[moreCmdsIndex]*(grpnumber):0);
			int OY = ((multiline)?newLineCmdsY*(grpnumber):0) -3;//3 is initial offset from where commands run to where we want the first line to start
			int ox = OX + (moreCmdsX[moreCmdsIndex]*(-length+1));
			int oz = OZ + (moreCmdsZ[moreCmdsIndex]*(-length+1));
			//int dir = cmdblkFacingNormal[moreCmdsIndex];
			String dir = cmdblkFacingNormalStr[moreCmdsIndex];
			
			//create line of commands (fill)
			if(psngrs.length()>0) {
				psngrs.append(',');
			}
			psngrs.append(replaceCoords(psngrCmdBlockListA,0,0,0,OX,OY,OZ,ox,OY,oz,0,dir));
			//now every 5, change the direction
			for(int i=0;i<length;i++) {
				if(i%5==4) {
					psngrs.append(',');
					ox = OX + (moreCmdsX[moreCmdsIndex]*-i);
					oz = OZ + (moreCmdsZ[moreCmdsIndex]*-i);
					if(i%25==24) {
						dir = cmdblkFacingTwentyFiveStr[moreCmdsIndex];
						psngrs.append(replaceCoords(psngrCmdBlockSingleA25,0,0,0,ox,OY,oz,0,0,0,0,dir));
					}else {
						dir = cmdblkFacingFiveStr[moreCmdsIndex];
						psngrs.append(replaceCoords(psngrCmdBlockSingleA,0,0,0,ox,OY,oz,0,0,0,0,dir));
					}
				}
			}
		}
	
		
		if(moreCmdsIndex == 5) {
			//add flying machine to run commands (todo: add support for other directions  todo: add support for lines besides the first (how?))
			
			//add flying machine blocker
			psngrs.append(',');
			psngrs.append(psngrCmdBlockFMBlocker.replace("%ox%",""+(data.cmds.size()+4)));
			
			//add flying machine
			psngrs.append(',');
			psngrs.append(psngrCmdBlockFlyingMachine);
		}
		
		//add end cleanup
		psngrs.append(',');
		psngrs.append(psngrEndCleanup);
		
		String c = cmdStartFirst;
		c =replaceCoords(c,0,0,0,0,0,0,0,0,0,0).replace("%MINECARTS%", psngrs);
		
		if(c.length() > MAXMAINCOMMANDLENGTH) {
			return "";
		}
		
		return c;
	}
	
	private String escapeQuotesSlash(String str) {
		return str.replace("\n", "\\n").replace("\\","\\\\").replace("\"", "\\\"");
	}
	
	private void appendTextNow(String text) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				out.setText(out.getText()+"\n"+text);
				out.repaint();
			}
		});
		
	}
	
	private void appendProgressNow(double ratio) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				String text = out.getText();
				text = text.substring(0, text.lastIndexOf('\n'));
				
				Runtime run = Runtime.getRuntime();
				long used = (run.totalMemory()-run.freeMemory())/(1024*1024);
				long total	= run.totalMemory()/(1024*1024);
				
				
				out.setText(text+"\n "+percentFormater.format(ratio*100)+"%    RAM USED: "+ used +"MB    TOTAL: " + total +"MB"  );
				out.repaint();
				//if(total*0.9 < used) {
				//	System.out.println(run.maxMemory());
				//}
			}
		});
	}
	
	private void appendProgressDoubleNow(double ratio, double ratio2) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				String text = out.getText();
				text = text.substring(0, text.lastIndexOf('\n'));
				out.setText(text+"\n "+percentFormater.format(ratio*100)+"%    "+percentFormater.format(ratio2*100)+"%");
				out.repaint();
			}
		});
	}
	
	static class BlockType {
		String name;
		int issues;
		
		int myId; //custom id - doesn't match with anything official, but is a unique id per type that I can used that is shorter than the name
		
		static final int ISSUE_AIR 				= 0x00001;
		static final int ISSUE_INVENTORY 		= 0x00002;
		static final int ISSUE_SIDE_ATTACHED 	= 0x00004;
		static final int ISSUE_MULTIBLOCK 		= 0x00008;
		static final int ISSUE_NEEDS_SUPPORT 	= 0x00010;
		static final int ISSUE_RAIL 			= 0x00020;
		static final int ISSUE_PASS_TWO			= 0x00040;
		static final int ISSUE_NO_FILL			= 0x00080;
		static final int ISSUE_TRANSPARENT 		= 0x10000;
		
		static int nextID = 0;
		
		
		public BlockType(String name) {
			this(name,0);
		}
		
		
		public BlockType(String name, boolean isAir, boolean hasInventory, boolean isSideAttached, boolean isMultiblock, boolean needsSupport, boolean isRail, boolean isTransparent) {
			this(name,
					(isAir ? ISSUE_AIR : 0) +
					(hasInventory ? ISSUE_INVENTORY : 0) +
					(isSideAttached ? ISSUE_SIDE_ATTACHED : 0) +
					(isMultiblock ? ISSUE_MULTIBLOCK : 0) +
					(needsSupport ? ISSUE_NEEDS_SUPPORT : 0) +
					(isRail ? ISSUE_RAIL : 0) +
					(isTransparent ? ISSUE_TRANSPARENT : 0)
			);
		}

		public BlockType(String name, int issues) {
			this.name = name;
			this.issues = issues;
			
			this.myId = nextID;
			nextID++;
			
			//debug
			if(isAir() && !name.contains("air")){
				System.out.println("AIR TYPE BLOCK NOT ANMED AIR!: "+name);
			}
		}
		
		public boolean isAir() {
			return ((issues & ISSUE_AIR) != 0);
		}
		
		public boolean hasInventory() {
			return ((issues & ISSUE_INVENTORY) != 0);
		}
		
		public boolean isSideAttached() {
			return ((issues & ISSUE_SIDE_ATTACHED) != 0);
		}
		
		public boolean isMultiblock() {
			return ((issues & ISSUE_MULTIBLOCK) != 0);
		}
		
		public boolean needsSupport() {
			return ((issues & ISSUE_NEEDS_SUPPORT) != 0);
		}
		
		public boolean isRail() {
			return ((issues & ISSUE_RAIL) != 0);
		}
		
		public boolean isTransparent() {
			return ((issues & ISSUE_TRANSPARENT) != 0);
		}
		
		public boolean isPassTwo() {
			return ((issues & ISSUE_PASS_TWO) != 0);
		}
		
		public void setPassTwo() {
			//mostly (only?) used so that sugar case isn't placed before the water that allows it to survive
			issues = issues | ISSUE_PASS_TWO;
		}
		
		public boolean isNoFill() {
			return ((issues & ISSUE_NO_FILL) != 0);
		}
		
		public void setNoFill() {
			//mostly (only?) used so that sugar case isn't placed before the water that allows it to survive
			issues = issues | ISSUE_NO_FILL;
		}
		
		@Override
		public boolean equals(Object o) {
			if(o instanceof BlockType) {
				if(this.name.equals(((BlockType)o).name)){
					return true;
				}
			}
			
			return false;
		}
		
		@Override
		public int hashCode() {
			return name.hashCode();
		}
	}
	
	static class Block {
		BlockType type;
		String properties;
		TagCompound compound;
		
		@Override
		public boolean equals(Object o) {
			
			if(o instanceof Block) {
				Block b = (Block)o;
				if(this.type.equals(b.type) && this.properties.equals(b.properties)) {
				
					if((this.compound == null && b.compound == null)) {
						return true;
					}else if( this.compound != null && b.compound != null) {
						//need to compare "nbt" sub-tags
						if( this.compound.getTags().containsKey("nbt") && b.compound.getTags().containsKey("nbt")) {

								try {
									return  this.compound.getCompound("nbt").equals(b.compound.getCompound("nbt"));
								} catch (UnexpectedTagTypeException | TagNotFoundException e) {
									//this should never happen as we just checked if they exist
									e.printStackTrace();
								}
								
						}else if(!this.compound.getTags().containsKey("nbt") && !b.compound.getTags().containsKey("nbt")) {
							return true;
						}
					}
				
						
				}
			}
			return false;
		}
		
		@Override
		public String toString() {
			if(properties.length()>0) {
				return type.name+"["+properties+"]";
			}
			return type.name;
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(type, properties, compound);
		}
	}
	
	
}
