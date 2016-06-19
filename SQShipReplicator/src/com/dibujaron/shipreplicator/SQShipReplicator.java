package com.dibujaron.shipreplicator;

import net.countercraft.movecraft.utils.BlockUtils;
import net.countercraft.movecraft.utils.Rotation;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class SQShipReplicator extends JavaPlugin implements Listener{
	
	private static final String KEY_LINE = ChatColor.AQUA + "Replicator";
	static List <String> ignorableDatas;
	static List <String> ignorableBlocks;
	
	public void onEnable(){
		getServer().getPluginManager().registerEvents(this, this);
		this.saveDefaultConfig();
		ignorableDatas = getConfig().getStringList("ignoreData");
		ignorableBlocks = getConfig().getStringList("ignoreBlock");
	}
	
	@EventHandler
	public void onSignInteract(PlayerInteractEvent event){
		if(!event.isCancelled() && event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock().getType() == Material.WALL_SIGN){
			Sign s = (Sign) event.getClickedBlock().getState();
			String keyLine = s.getLine(0);
			if(keyLine.equals("[replicator]") && event.getPlayer().hasPermission("replicator.create")){
				if(event.getClickedBlock().getRelative(BlockFace.DOWN).getType() == Material.CHEST){
					int[] dimensions = getFrame(event.getClickedBlock());
					if(dimensions != null){
						s.setLine(0, KEY_LINE);
						s.setLine(1, "<-- Scan");
						s.setLine(2, "Print -->");
						s.update();
						return;
					}
				}
				event.getPlayer().sendMessage("Improperly built replicator!");
				return;
			} else if (keyLine.equals(KEY_LINE) && event.getPlayer().hasPermission("replicator.use")){
				BlockBreakEvent fake = new BlockBreakEvent(event.getClickedBlock(), event.getPlayer());
				Bukkit.getPluginManager().callEvent(fake);
				if(!fake.isCancelled()){
					replicate(event.getClickedBlock(), event.getPlayer());
				} else {
					event.getPlayer().sendMessage("You do not have permission to build here!");
				}
			}
		}
	}
	
	private static void replicate(Block b, Player player) {
		int[] dimensions = getFrame(b);
		if(dimensions == null){
			player.sendMessage("Invalid frame!");
			return;
		}
		
		Block chestBlock = b.getRelative(BlockFace.DOWN);
		if(chestBlock.getType() != Material.CHEST){
			player.sendMessage("Chest not found!");
			return;
		}
		Chest chest = (Chest) chestBlock.getState();
		BlockFace forward = DirectionUtils.getSignDirection(b);
		BlockFace left = DirectionUtils.getBlockFaceLeft(forward);
		BlockFace right = DirectionUtils.getBlockFaceRight(forward);
		
		int forwardMax = dimensions[0];
		int lateralMax = dimensions[1];
		int heightMax = dimensions[2];
		

		Block scan = b.getRelative(forward).getRelative(left, lateralMax - 1);
		Block print = b.getRelative(forward).getRelative(right);

		int heightDist = 0;
		while(heightDist < heightMax){
			int forwardDist = 0;
			Block scanPreHeight = scan;
			Block printPreHeight = print;
			while(forwardDist < forwardMax){
				int lateralDist = 0;
				Block scanPreLat = scan;
				Block printPreLat = print;
				while(lateralDist < lateralMax){
					copy(scan, print, chest);
					scan = scan.getRelative(right);
					print = print.getRelative(right);
					lateralDist++;
				}
				scan = scanPreLat.getRelative(forward);
				print = printPreLat.getRelative(forward);
				forwardDist++;
			}
			scan = scanPreHeight.getRelative(BlockFace.UP);
			print = printPreHeight.getRelative(BlockFace.UP);
			heightDist++;
		}
		
		print.getWorld().playSound(print.getLocation(), Sound.BLOCK_PISTON_EXTEND, 2.0f, 2.0f);

	}

	private static String locToString(Location location) {
		return location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
	}

	private static void copy(Block scan, Block print, Chest chest) {
		Material type = scan.getType();
		byte data = scan.getData();
		if(print.getType() != Material.AIR) return;
		if(ignorableBlocks.contains(type.toString()) || type == print.getType() && data == print.getData()){
			return;
		}
		//System.out.println("Unequal types! : " + scan.getType());
		short durability = data;
		if(removeItemsFromInventory(type, durability, chest.getInventory(), 1) < 1){
			return;
		}
		print.setType(type);
		print.setData(data);
	}
	
	private static byte rotateData180(int type, byte data){
		return BlockUtils.rotate(BlockUtils.rotate(data, type, Rotation.CLOCKWISE), type, Rotation.CLOCKWISE);
	}
	
	public static int removeItemsFromInventory(Material type, short durability, Inventory inv, int amount){
		int amountRemoved = 0;
		for(int i = 0; i < inv.getSize(); i++){
			ItemStack item = inv.getItem(i);
			if(item != null && item.getType() == type){
				if(item.getDurability() == durability || isIgnorableDataValue(type)){
					int amtLeft = amount-amountRemoved;
					if(item.getAmount() < amtLeft){
						//add to the amount removed and delete the item
						amountRemoved += item.getAmount();
						inv.setItem(i, new ItemStack(Material.AIR, 1));
					} else {
						//everything is removed, subtract the last few from this stack and go
						item.setAmount(item.getAmount() - amtLeft);
						inv.setItem(i, item);
						amountRemoved = amount;
						break;
					}
				}
			}
		}
		return amountRemoved;
	}
	
	private static boolean isIgnorableDataValue(Material type){
		if(ignorableDatas.contains(type.toString())){
			return true;
		}
		return false;
	}

	private static int[] getFrame(Block b){
		BlockFace forward = DirectionUtils.getSignDirection(b);
		int fDist = 0;
		int lDist = 0;
		int hDist = 0;
		
		Material frame = Material.BRICK;
		Block base = b.getRelative(forward);
		fDist = getDistanceInDirection(base, forward, frame);
		if(fDist == 0) return null;
		hDist = getDistanceInDirection(base, BlockFace.UP, frame);
		if(hDist == 0) return null;
		
		//lateral distance is trickier
		int leftDist = getDistanceInDirection(base, DirectionUtils.getBlockFaceLeft(forward), frame);
		int rightDist = getDistanceInDirection(base, DirectionUtils.getBlockFaceRight(forward), frame);
		if(leftDist != rightDist) return null;
		lDist = leftDist;
		
		return new int[] {fDist, lDist, hDist};
	}
	
	private static int getDistanceInDirection(Block base, BlockFace direction, Material frame){
		Block testBlock = base;
		int dist = 0;
		int tries = 0;
		//first get forward distance
		while(testBlock.getType() == frame && tries < 50){
			dist++;
			testBlock = testBlock.getRelative(direction);
		}
		return dist;
	}
}