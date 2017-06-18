package Phimosis;

import java.util.Collection;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.SkullType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;


public class Main extends JavaPlugin implements CommandExecutor, Listener {

	void kup(Material kup, String[] args, CommandSender sender, int price){
		if (!(sender instanceof Player)) {
			sender.sendMessage("This command can only be run by a player.");
		} else {
			Player player = (Player) sender;

			PlayerInventory inventory = player.getInventory(); // Ekwipunek
																// gracza
			
			int mul = 1;
			if(args.length == 1) mul = Integer.parseInt(args[0]);
			
			ItemStack sztabka = new ItemStack(Material.EMERALD, price*mul);
			ItemStack kosci = new ItemStack(kup, mul);
			
			if (inventory.containsAtLeast(sztabka, price*mul)) {
				inventory.removeItem(sztabka);
				inventory.addItem(kosci);
				player.sendMessage("§cJP2: OK");
			} else
				player.sendMessage("§cJP2: Chuj Ci w dupe. Nie masz "+ mul*price +" szmaragdow.");

		}
	}
	
	void sprzedaj(Material sprzedaj, String[] args, CommandSender sender, int price){
		if (!(sender instanceof Player)) {
			sender.sendMessage("This command can only be run by a player.");
		} else {
			Player player = (Player) sender;

			PlayerInventory inventory = player.getInventory(); // Ekwipunek
																// gracza
			
			int mul = 1;
			if(args.length == 1) mul = Integer.parseInt(args[0]);
			
			ItemStack oferta = new ItemStack(sprzedaj, mul);
			ItemStack waluta = new ItemStack(Material.EMERALD, price*mul);
			
			if (inventory.containsAtLeast(oferta, mul)) {
				inventory.removeItem(oferta);
				inventory.addItem(waluta);
				player.sendMessage("§cJP2: OK");
			} else
				player.sendMessage("§cJP2: Chuj Ci w dupe. Nie masz "+ mul +" " + sprzedaj);

		}
	}
	
	
	public void onEnable() {

		Bukkit.getServer().getPluginManager().registerEvents(this, this);
		System.out.println("Ku chwale Phimosis");

	}

	public void onDisable() {
	}

	@EventHandler
	public void onDeath(PlayerDeathEvent e) {
		ItemStack skull = new ItemStack(Material.SKULL_ITEM, 1, (short) SkullType.PLAYER.ordinal());
		SkullMeta sm = (SkullMeta) skull.getItemMeta();
		sm.setOwner(e.getEntity().getName());
		skull.setItemMeta(sm);
		e.getEntity().getLocation().getWorld().dropItemNaturally(e.getEntity().getLocation(), skull);
	}

	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		
		/********************************************************/
		/*						KUP								*/
		/********************************************************/
		
		if (cmd.getName().equalsIgnoreCase("kupkwarc")) {
			
			//material, args, sender, price
			kup(Material.QUARTZ_BLOCK, args, sender, 40);
			
		} else if (cmd.getName().equalsIgnoreCase("kupkosci")) {

			//material, args, sender, price
			kup(Material.BONE, args, sender, 6);
			
		} else if (cmd.getName().equalsIgnoreCase("kupjasnopyl")) {
			
			//material, args, sender, price
			kup(Material.GLOWSTONE, args, sender, 15);
			
		} else if (cmd.getName().equalsIgnoreCase("kupzloto")) {
			
			//material, args, sender, price
			kup(Material.GOLD_INGOT, args, sender, 40);
			
		} else if (cmd.getName().equalsIgnoreCase("kupdiamenty")) {
			
			//material, args, sender, price
			kup(Material.DIAMOND, args, sender, 160);
			
		} else if (cmd.getName().equalsIgnoreCase("kupczerwonyproszek")) {
			
			//material, args, sender, price
			kup(Material.REDSTONE, args, sender, 4);
			
		} else if (cmd.getName().equalsIgnoreCase("kupsnieg")) {
			
			//material, args, sender, price
			kup(Material.SNOW_BLOCK, args, sender, 4);
			
		} else if (cmd.getName().equalsIgnoreCase("kuppiaskowiec")) {
			
			//material, args, sender, price
			kup(Material.SANDSTONE, args, sender, 4);
		
		/********************************************************/
		/*						SPRZEDAJ						*/
		/********************************************************/
			
		} else if (cmd.getName().equalsIgnoreCase("sprzedajziemie")) {
			
			//material, args, sender, price
			sprzedaj(Material.DIRT, args, sender, 1);
			
		} else if (cmd.getName().equalsIgnoreCase("sprzedajzloto")) {
			
			//material, args, sender, price
			sprzedaj(Material.GOLD_INGOT, args, sender, 40);
			
		
		} else if (cmd.getName().equalsIgnoreCase("sprzedajbruk")) {
			
			//material, args, sender, price
			sprzedaj(Material.COBBLESTONE, args, sender, 1);
			
		} else if (cmd.getName().equalsIgnoreCase("sprzedajdiamenty")) {
			
			//material, args, sender, price
			sprzedaj(Material.DIAMOND, args, sender, 160);
			
		} else if (cmd.getName().equalsIgnoreCase("sprzedajpiasek")) {
			
			//material, args, sender, price
			sprzedaj(Material.SAND, args, sender, 1);
			
		} else if (cmd.getName().equalsIgnoreCase("sprzedajzelazo")) {
			
			//material, args, sender, price
			sprzedaj(Material.IRON_INGOT, args, sender, 15);
			
		} else if (cmd.getName().equalsIgnoreCase("sprzedajwegiel")) {
			
			//material, args, sender, price
			sprzedaj(Material.COAL, args, sender, 8);
		
			
		/********************************************************/
		/*						INNE						*/
		/********************************************************/

		} else if (cmd.getName().equalsIgnoreCase("wolagrzmota")) {
			if (!(sender instanceof Player)) {
				sender.sendMessage("This command can only be run by a player.");
			} else {
				Player player = (Player) sender;
				
				player.sendMessage("§cJP2: JEB!");
				
				org.bukkit.Location location = player.getLocation();
				
				Collection<org.bukkit.entity.Entity> zwierzeta = player.getWorld().getNearbyEntities(location, 10, 10, 10);
				
				for(org.bukkit.entity.Entity zwierze:zwierzeta){
					zwierze.remove();
				}
				
			}

		} else if (cmd.getName().equalsIgnoreCase("chwalaJP2")) {
			if (!(sender instanceof Player)) {
				sender.sendMessage("This command can only be run by a player.");
			} else {
				Player player = (Player) sender;

				PlayerInventory inventory = player.getInventory();
				ItemStack axe = new ItemStack(Material.DIAMOND_AXE);
				{
					axe.addUnsafeEnchantment(Enchantment.LOOT_BONUS_BLOCKS, 10);
					axe.addUnsafeEnchantment(Enchantment.DIG_SPEED, 10);
					axe.addUnsafeEnchantment(Enchantment.DURABILITY, 10);
				}
				ItemStack pickaxe = new ItemStack(Material.DIAMOND_PICKAXE);
				{
					pickaxe.addUnsafeEnchantment(Enchantment.LOOT_BONUS_BLOCKS, 10);
					pickaxe.addUnsafeEnchantment(Enchantment.DIG_SPEED, 10);
					pickaxe.addUnsafeEnchantment(Enchantment.DURABILITY, 10);
				}
				ItemStack spade = new ItemStack(Material.DIAMOND_SPADE);
				{
					spade.addUnsafeEnchantment(Enchantment.LOOT_BONUS_BLOCKS, 10);
					spade.addUnsafeEnchantment(Enchantment.DIG_SPEED, 10);
					spade.addUnsafeEnchantment(Enchantment.DURABILITY, 10);
				}
				
				inventory.addItem(pickaxe);
				inventory.addItem(spade);
				inventory.addItem(axe);
				player.sendMessage("§cJP2: Blogoslawie cie :)");
			}
			
			
		} else if (cmd.getName().equalsIgnoreCase("morphingtime")) {
			if (!(sender instanceof Player)) {
				sender.sendMessage("This command can only be run by a player.");
			} else {
				Player player = (Player) sender;

				ItemStack buty = new ItemStack(Material.LEATHER_BOOTS, 1);
				ItemStack armor = new ItemStack(Material.LEATHER_CHESTPLATE, 1);
				ItemStack helm = new ItemStack(Material.LEATHER_HELMET, 1);
				ItemStack spodnie = new ItemStack(Material.LEATHER_LEGGINGS, 1);

				LeatherArmorMeta meta1 = (LeatherArmorMeta) buty.getItemMeta();
				LeatherArmorMeta meta2 = (LeatherArmorMeta) armor.getItemMeta();
				LeatherArmorMeta meta3 = (LeatherArmorMeta) helm.getItemMeta();
				LeatherArmorMeta meta4 = (LeatherArmorMeta) spodnie.getItemMeta();

				switch (player.getName()) {

				case "Grzmot":

					meta1.setColor(Color.RED);
					meta2.setColor(Color.RED);
					meta3.setColor(Color.RED);
					meta4.setColor(Color.RED);
					sender.sendMessage("§cJP2: Czerwony Ranger! Aktywacja!");
					break;

				case "Ehwar":

					meta1.setColor(Color.WHITE);
					meta2.setColor(Color.WHITE);
					meta3.setColor(Color.WHITE);
					meta4.setColor(Color.WHITE);
					sender.sendMessage("§cJP2: Bialy Ranger! Aktywacja!");
					break;

				case "chkstate":

					meta1.setColor(Color.BLUE);
					meta2.setColor(Color.BLUE);
					meta3.setColor(Color.BLUE);
					meta4.setColor(Color.BLUE);
					sender.sendMessage("§cJP2: Niebieski Ranger! Aktywacja!");
					break;

				case "Broden94":

					meta1.setColor(Color.PURPLE);
					meta2.setColor(Color.PURPLE);
					meta3.setColor(Color.PURPLE);
					meta4.setColor(Color.PURPLE);
					sender.sendMessage("§cJP2: Fioletowy Ranger! Aktywacja!");
					break;

				case "walikmalik":

					meta1.setColor(Color.BLACK);
					meta2.setColor(Color.BLACK);
					meta3.setColor(Color.BLACK);
					meta4.setColor(Color.BLACK);
					sender.sendMessage("§cJP2: Czarny Ranger! Aktywacja!");
					break;

				default:
					sender.sendMessage("§cJP2: Nie jestes Rengersem");
					break;

				}

				buty.setItemMeta(meta1);
				armor.setItemMeta(meta2);
				helm.setItemMeta(meta3);
				spodnie.setItemMeta(meta4);

				PlayerInventory inventory = player.getInventory();

				inventory.setBoots(buty);
				inventory.setChestplate(armor);
				inventory.setLeggings(spodnie);
				inventory.setHelmet(helm);

			}
		}

		return false;
	}
}
