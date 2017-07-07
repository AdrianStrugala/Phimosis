package Phimosis;

import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SkullType;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Ocelot;
import org.bukkit.entity.Ocelot.Type;
import org.bukkit.entity.Parrot;
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

	void kup(Material kup, String[] args, CommandSender sender, int price) {
		if (!(sender instanceof Player)) {
			sender.sendMessage("This command can only be run by a player.");
		} else {
			Player player = (Player) sender;

			PlayerInventory inventory = player.getInventory(); // Ekwipunek
																// gracza

			int mul = 1;
			if (args.length == 1)
				mul = Integer.parseInt(args[0]);

			ItemStack sztabka = new ItemStack(Material.EMERALD, price * mul);
			ItemStack kosci = new ItemStack(kup, mul);

			if (inventory.containsAtLeast(sztabka, price * mul)) {
				inventory.removeItem(sztabka);
				inventory.addItem(kosci);
				player.sendMessage("§cJP2: OK");
			} else
				player.sendMessage("§cJP2: Chuj Ci w dupe. Nie masz " + mul * price + " szmaragdow.");

		}
	}

	void sprzedaj(Material sprzedaj, String[] args, CommandSender sender, int price) {
		if (!(sender instanceof Player)) {
			sender.sendMessage("This command can only be run by a player.");
		} else {
			Player player = (Player) sender;

			PlayerInventory inventory = player.getInventory(); // Ekwipunek
																// gracza

			int mul = 1;
			if (args.length == 1)
				mul = Integer.parseInt(args[0]);

			ItemStack oferta = new ItemStack(sprzedaj, mul);
			ItemStack waluta = new ItemStack(Material.EMERALD, price * mul);

			if (inventory.containsAtLeast(oferta, mul)) {
				inventory.removeItem(oferta);
				inventory.addItem(waluta);
				player.sendMessage("§cJP2: OK");
			} else
				player.sendMessage("§cJP2: Chuj Ci w dupe. Nie masz " + mul + " " + sprzedaj);

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
		/* KUP */
		/********************************************************/

		if (cmd.getName().equalsIgnoreCase("kupkwarc")) {

			// material, args, sender, price
			kup(Material.QUARTZ_BLOCK, args, sender, 40);

		} else if (cmd.getName().equalsIgnoreCase("kupkosci")) {

			// material, args, sender, price
			kup(Material.BONE, args, sender, 12);

		} else if (cmd.getName().equalsIgnoreCase("kupjasnopyl")) {

			// material, args, sender, price
			kup(Material.GLOWSTONE, args, sender, 40);

		} else if (cmd.getName().equalsIgnoreCase("kupzloto")) {

			// material, args, sender, price
			kup(Material.GOLD_INGOT, args, sender, 80);

		} else if (cmd.getName().equalsIgnoreCase("kupdiamenty")) {

			// material, args, sender, price
			kup(Material.DIAMOND, args, sender, 320);

		} else if (cmd.getName().equalsIgnoreCase("kupczerwonyproszek")) {

			// material, args, sender, price
			kup(Material.REDSTONE, args, sender, 8);

		} else if (cmd.getName().equalsIgnoreCase("kupsnieg")) {

			// material, args, sender, price
			kup(Material.SNOW_BLOCK, args, sender, 8);

		} else if (cmd.getName().equalsIgnoreCase("kuppiaskowiec")) {

			// material, args, sender, price
			kup(Material.SANDSTONE, args, sender, 8);

		} else if (cmd.getName().equalsIgnoreCase("kupkamien")) {

			// material, args, sender, price
			kup(Material.STONE, args, sender, 4);

		} else if (cmd.getName().equalsIgnoreCase("kupszlam")) {

			// material, args, sender, price
			kup(Material.SLIME_BALL, args, sender, 50);

		} else if (cmd.getName().equalsIgnoreCase("kupplomiennyproszek")) {

			// material, args, sender, price
			kup(Material.BLAZE_ROD, args, sender, 100);

		} else if (cmd.getName().equalsIgnoreCase("kuplze")) {

			// material, args, sender, price
			kup(Material.GHAST_TEAR, args, sender, 400);

		} else if (cmd.getName().equalsIgnoreCase("kupelytre")) {

			// material, args, sender, price
			kup(Material.ELYTRA, args, sender, 640);

		} else if (cmd.getName().equalsIgnoreCase("kupnetherowacegle")) {

			// material, args, sender, price
			kup(Material.NETHER_BRICK, args, sender, 16);

		} else if (cmd.getName().equalsIgnoreCase("kupczerwonanetherowacegle")) {

			// material, args, sender, price
			kup(Material.RED_NETHER_BRICK, args, sender, 16);

		} else if (cmd.getName().equalsIgnoreCase("kuplatarnie")) {

			// material, args, sender, price
			kup(Material.SEA_LANTERN, args, sender, 40);

		} else if (cmd.getName().equalsIgnoreCase("kuppryzmaryn")) {

			int price = 20;

			if (!(sender instanceof Player)) {

				sender.sendMessage("This command can only be run by a player.");
			} else {
				Player player = (Player) sender;

				PlayerInventory inventory = player.getInventory(); // Ekwipunek
																	// gracza

				int mul = 1;
				if (args.length == 1)
					mul = Integer.parseInt(args[0]);

				ItemStack sztabka = new ItemStack(Material.EMERALD, price * mul);
				ItemStack kosci = new ItemStack(Material.PRISMARINE, mul, (short) 0);

				if (inventory.containsAtLeast(sztabka, price * mul)) {
					inventory.removeItem(sztabka);
					inventory.addItem(kosci);
					player.sendMessage("§cJP2: OK");
				} else
					player.sendMessage("§cJP2: Chuj Ci w dupe. Nie masz " + mul * price + " szmaragdow.");

			}

		} else if (cmd.getName().equalsIgnoreCase("kuppryzmarynowacegle")) {

			int price = 40;

			if (!(sender instanceof Player)) {

				sender.sendMessage("This command can only be run by a player.");
			} else {
				Player player = (Player) sender;

				PlayerInventory inventory = player.getInventory(); // Ekwipunek
																	// gracza

				int mul = 1;
				if (args.length == 1)
					mul = Integer.parseInt(args[0]);

				ItemStack sztabka = new ItemStack(Material.EMERALD, price * mul);
				ItemStack kosci = new ItemStack(Material.PRISMARINE, mul, (short) 1);

				if (inventory.containsAtLeast(sztabka, price * mul)) {
					inventory.removeItem(sztabka);
					inventory.addItem(kosci);
					player.sendMessage("§cJP2: OK");
				} else
					player.sendMessage("§cJP2: Chuj Ci w dupe. Nie masz " + mul * price + " szmaragdow.");

			}

		} else if (cmd.getName().equalsIgnoreCase("kupciemnypryzmaryn")) {

			int price = 40;

			if (!(sender instanceof Player)) {

				sender.sendMessage("This command can only be run by a player.");
			} else {
				Player player = (Player) sender;

				PlayerInventory inventory = player.getInventory(); // Ekwipunek
																	// gracza

				int mul = 1;
				if (args.length == 1)
					mul = Integer.parseInt(args[0]);

				ItemStack sztabka = new ItemStack(Material.EMERALD, price * mul);
				ItemStack kosci = new ItemStack(Material.PRISMARINE, mul, (short) 2);

				if (inventory.containsAtLeast(sztabka, price * mul)) {
					inventory.removeItem(sztabka);
					inventory.addItem(kosci);
					player.sendMessage("§cJP2: OK");
				} else
					player.sendMessage("§cJP2: Chuj Ci w dupe. Nie masz " + mul * price + " szmaragdow.");

			}

		} else if (cmd.getName().equalsIgnoreCase("kupdab")) {

			int price = 16;

			if (!(sender instanceof Player)) {

				sender.sendMessage("This command can only be run by a player.");
			} else {
				Player player = (Player) sender;

				PlayerInventory inventory = player.getInventory(); // Ekwipunek
																	// gracza

				int mul = 1;
				if (args.length == 1)
					mul = Integer.parseInt(args[0]);

				ItemStack sztabka = new ItemStack(Material.EMERALD, price * mul);
				ItemStack kosci = new ItemStack(Material.LOG, mul, (short) 0);

				if (inventory.containsAtLeast(sztabka, price * mul)) {
					inventory.removeItem(sztabka);
					inventory.addItem(kosci);
					player.sendMessage("§cJP2: OK");
				} else
					player.sendMessage("§cJP2: Chuj Ci w dupe. Nie masz " + mul * price + " szmaragdow.");

			}

		} else if (cmd.getName().equalsIgnoreCase("kupswierk")) {

			int price = 16;

			if (!(sender instanceof Player)) {

				sender.sendMessage("This command can only be run by a player.");
			} else {
				Player player = (Player) sender;

				PlayerInventory inventory = player.getInventory(); // Ekwipunek
																	// gracza

				int mul = 1;
				if (args.length == 1)
					mul = Integer.parseInt(args[0]);

				ItemStack sztabka = new ItemStack(Material.EMERALD, price * mul);
				ItemStack kosci = new ItemStack(Material.LOG, mul, (short) 1);

				if (inventory.containsAtLeast(sztabka, price * mul)) {
					inventory.removeItem(sztabka);
					inventory.addItem(kosci);
					player.sendMessage("§cJP2: OK");
				} else
					player.sendMessage("§cJP2: Chuj Ci w dupe. Nie masz " + mul * price + " szmaragdow.");

			}

		} else if (cmd.getName().equalsIgnoreCase("kupbrzoze")) {

			int price = 16;

			if (!(sender instanceof Player)) {

				sender.sendMessage("This command can only be run by a player.");
			} else {
				Player player = (Player) sender;

				PlayerInventory inventory = player.getInventory(); // Ekwipunek
																	// gracza

				int mul = 1;
				if (args.length == 1)
					mul = Integer.parseInt(args[0]);

				ItemStack sztabka = new ItemStack(Material.EMERALD, price * mul);
				ItemStack kosci = new ItemStack(Material.LOG, mul, (short) 2);

				if (inventory.containsAtLeast(sztabka, price * mul)) {
					inventory.removeItem(sztabka);
					inventory.addItem(kosci);
					player.sendMessage("§cJP2: OK");
				} else
					player.sendMessage("§cJP2: Chuj Ci w dupe. Nie masz " + mul * price + " szmaragdow.");

			}

		} else if (cmd.getName().equalsIgnoreCase("kupjungle")) {

			int price = 16;

			if (!(sender instanceof Player)) {

				sender.sendMessage("This command can only be run by a player.");
			} else {
				Player player = (Player) sender;

				PlayerInventory inventory = player.getInventory(); // Ekwipunek
																	// gracza

				int mul = 1;
				if (args.length == 1)
					mul = Integer.parseInt(args[0]);

				ItemStack sztabka = new ItemStack(Material.EMERALD, price * mul);
				ItemStack kosci = new ItemStack(Material.LOG, mul, (short) 3);

				if (inventory.containsAtLeast(sztabka, price * mul)) {
					inventory.removeItem(sztabka);
					inventory.addItem(kosci);
					player.sendMessage("§cJP2: OK");
				} else
					player.sendMessage("§cJP2: Chuj Ci w dupe. Nie masz " + mul * price + " szmaragdow.");

			}

		} else if (cmd.getName().equalsIgnoreCase("kupciemnydab")) {

			int price = 16;

			if (!(sender instanceof Player)) {

				sender.sendMessage("This command can only be run by a player.");
			} else {
				Player player = (Player) sender;

				PlayerInventory inventory = player.getInventory(); // Ekwipunek
																	// gracza

				int mul = 1;
				if (args.length == 1)
					mul = Integer.parseInt(args[0]);

				ItemStack sztabka = new ItemStack(Material.EMERALD, price * mul);
				ItemStack kosci = new ItemStack(Material.LOG_2, mul, (short) 1);

				if (inventory.containsAtLeast(sztabka, price * mul)) {
					inventory.removeItem(sztabka);
					inventory.addItem(kosci);
					player.sendMessage("§cJP2: OK");
				} else
					player.sendMessage("§cJP2: Chuj Ci w dupe. Nie masz " + mul * price + " szmaragdow.");

			}

		} else if (cmd.getName().equalsIgnoreCase("kupakacje")) {

			int price = 16;

			if (!(sender instanceof Player)) {

				sender.sendMessage("This command can only be run by a player.");
			} else {
				Player player = (Player) sender;

				PlayerInventory inventory = player.getInventory(); // Ekwipunek
																	// gracza

				int mul = 1;
				if (args.length == 1)
					mul = Integer.parseInt(args[0]);

				ItemStack sztabka = new ItemStack(Material.EMERALD, price * mul);
				ItemStack kosci = new ItemStack(Material.LOG_2, mul, (short) 0);

				if (inventory.containsAtLeast(sztabka, price * mul)) {
					inventory.removeItem(sztabka);
					inventory.addItem(kosci);
					player.sendMessage("§cJP2: OK");
				} else
					player.sendMessage("§cJP2: Chuj Ci w dupe. Nie masz " + mul * price + " szmaragdow.");

			}

			/********************************************************/
			/* SPRZEDAJ */
			/********************************************************/

		} else if (cmd.getName().equalsIgnoreCase("sprzedajziemie")) {

			// material, args, sender, price
			sprzedaj(Material.DIRT, args, sender, 1);

		} else if (cmd.getName().equalsIgnoreCase("sprzedajzloto")) {

			// material, args, sender, price
			sprzedaj(Material.GOLD_INGOT, args, sender, 80);

		} else if (cmd.getName().equalsIgnoreCase("sprzedajbruk")) {

			// material, args, sender, price
			sprzedaj(Material.COBBLESTONE, args, sender, 4);

		} else if (cmd.getName().equalsIgnoreCase("sprzedajdiamenty")) {

			// material, args, sender, price
			sprzedaj(Material.DIAMOND, args, sender, 320);

		} else if (cmd.getName().equalsIgnoreCase("sprzedajpiasek")) {

			// material, args, sender, price
			sprzedaj(Material.SAND, args, sender, 2);

		} else if (cmd.getName().equalsIgnoreCase("sprzedajzelazo")) {

			// material, args, sender, price
			sprzedaj(Material.IRON_INGOT, args, sender, 30);

		} else if (cmd.getName().equalsIgnoreCase("sprzedajwegiel")) {

			// material, args, sender, price
			sprzedaj(Material.COAL, args, sender, 16);

		} else if (cmd.getName().equalsIgnoreCase("sprzedajkrzesiwo")) {

			// material, args, sender, price
			sprzedaj(Material.FLINT, args, sender, 8);

		} else if (cmd.getName().equalsIgnoreCase("sprzedajdab")) {
			if (!(sender instanceof Player)) {
				sender.sendMessage("This command can only be run by a player.");
			} else {
				Player player = (Player) sender;

				PlayerInventory inventory = player.getInventory(); // Ekwipunek
																	// gracza
				int price = 16;

				int mul = 1;
				if (args.length == 1)
					mul = Integer.parseInt(args[0]);

				ItemStack oferta = new ItemStack(Material.LOG, mul, (short) 0);
				ItemStack waluta = new ItemStack(Material.EMERALD, price * mul);

				if (inventory.containsAtLeast(oferta, mul)) {
					inventory.removeItem(oferta);
					inventory.addItem(waluta);
					player.sendMessage("§cJP2: OK");
				} else
					player.sendMessage("§cJP2: Chuj Ci w dupe. Nie masz " + mul + " " + Material.LOG);

			}

		} else if (cmd.getName().equalsIgnoreCase("sprzedajjungle")) {
			if (!(sender instanceof Player)) {
				sender.sendMessage("This command can only be run by a player.");
			} else {
				Player player = (Player) sender;

				PlayerInventory inventory = player.getInventory(); // Ekwipunek
																	// gracza
				int price = 16;

				int mul = 1;
				if (args.length == 1)
					mul = Integer.parseInt(args[0]);

				ItemStack oferta = new ItemStack(Material.LOG, mul, (short) 3);
				ItemStack waluta = new ItemStack(Material.EMERALD, price * mul);

				if (inventory.containsAtLeast(oferta, mul)) {
					inventory.removeItem(oferta);
					inventory.addItem(waluta);
					player.sendMessage("§cJP2: OK");
				} else
					player.sendMessage("§cJP2: Chuj Ci w dupe. Nie masz " + mul + " " + oferta);

			}

			/********************************************************/
			/* INNE */
			/********************************************************/

		} else if (cmd.getName().equalsIgnoreCase("wolagrzmota")) {
			if (!(sender instanceof Player)) {
				sender.sendMessage("This command can only be run by a player.");
			} else {
				Player player = (Player) sender;
				player.sendMessage("§cJP2: JEB!");

				World w = player.getWorld();
				for (Entity e : w.getNearbyEntities(player.getLocation(), 10, 10, 10)) {
					if (e instanceof Ocelot) {
						if (!((Ocelot) e).isTamed())
							((Ocelot) e).damage(1000);
					}
				}
			}

		} else if (cmd.getName().equalsIgnoreCase("alkhajit")) {
			if (!(sender instanceof Player)) {
				sender.sendMessage("This command can only be run by a player.");
			} else {
				Player player = (Player) sender;
				player.sendMessage("§cJP2: El Khajito byl bogiem!");

				World w = player.getWorld();

				Location location = player.getEyeLocation();

				Ocelot kot = (Ocelot) w.spawnEntity(location, EntityType.OCELOT);
				kot.setOwner(player);

				Random generator = new Random();
				int i = generator.nextInt(2);

				switch (i) {

				case 0:
					kot.setCatType(Type.BLACK_CAT);
					break;

				case 1:
					kot.setCatType(Type.SIAMESE_CAT);
					break;

				default:
					kot.setCatType(Type.RED_CAT);
					break;

				}
			}

		} else if (cmd.getName().equalsIgnoreCase("papuga")) {
			if (!(sender instanceof Player)) {
				sender.sendMessage("This command can only be run by a player.");
			} else {
				Player player = (Player) sender;
				player.sendMessage("§cJP2: Cwir cwir kurwa!");

				World w = player.getWorld();

				Location location = player.getEyeLocation();

				Parrot kot = (Parrot) w.spawnEntity(location, EntityType.PARROT);
				kot.setOwner(player);
			}

		} else if (cmd.getName().equalsIgnoreCase("chwalaJP2")) {
			if (!(sender instanceof Player)) {
				sender.sendMessage("This command can only be run by a player.");
			} else {
				Player player = (Player) sender;

				PlayerInventory inventory = player.getInventory();
				ItemStack axe = new ItemStack(Material.DIAMOND_AXE);
				{
					axe.addUnsafeEnchantment(Enchantment.LOOT_BONUS_BLOCKS, 3);
					axe.addUnsafeEnchantment(Enchantment.DIG_SPEED, 5);
					axe.addUnsafeEnchantment(Enchantment.DURABILITY, 10);
				}
				ItemStack pickaxe = new ItemStack(Material.DIAMOND_PICKAXE);
				{
					pickaxe.addUnsafeEnchantment(Enchantment.LOOT_BONUS_BLOCKS, 3);
					pickaxe.addUnsafeEnchantment(Enchantment.DIG_SPEED, 5);
					pickaxe.addUnsafeEnchantment(Enchantment.DURABILITY, 10);
				}
				ItemStack spade = new ItemStack(Material.DIAMOND_SPADE);
				{
					spade.addUnsafeEnchantment(Enchantment.LOOT_BONUS_BLOCKS, 3);
					spade.addUnsafeEnchantment(Enchantment.DIG_SPEED, 5);
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

		} else if (cmd.getName().equalsIgnoreCase("pogoda")) {
			if (!(sender instanceof Player)) {
				sender.sendMessage("This command can only be run by a player.");
			} else {
				Player player = (Player) sender;
				World world = player.getWorld();

				switch (args[0]) {

				case "czysto":

					world.setStorm(false);
					world.setThundering(false);
					Bukkit.broadcastMessage("§cJP2: Ustawiam czyste niebo.");
					break;

				case "deszcz":

					world.setStorm(true);
					world.setThundering(false);
					Bukkit.broadcastMessage("§cJP2: Ustawiam deszcz.");
					break;

				case "burza":

					world.setStorm(true);
					world.setThundering(true);
					Bukkit.broadcastMessage("§cJP2: Ustawiam burze.");
					break;

				default:
					sender.sendMessage("§cJP2: Chuj Ci w dupe. Nie ma takiej pogody.");
					break;

				}

			}

		} else if (cmd.getName().equalsIgnoreCase("czas")) {
			if (!(sender instanceof Player)) {
				sender.sendMessage("This command can only be run by a player.");
			} else {
				Player player = (Player) sender;
				World world = player.getWorld();

				switch (args[0]) {

				case "dzien":

					world.setTime(0);
					Bukkit.broadcastMessage("§cJP2: Robie dzien.");
					break;

				case "noc":

					world.setTime(13000);
					Bukkit.broadcastMessage("§cJP2: Robie noc.");
					break;

				default:

					world.setTime(Integer.parseInt(args[0]));
					Bukkit.broadcastMessage("§cJP2: Ustawiam czas: " + args[0]);
					break;

				}
			}
		}

		return false;
	}
}
