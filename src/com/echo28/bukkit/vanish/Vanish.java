package com.echo28.bukkit.vanish;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

import net.minecraft.server.Packet20NamedEntitySpawn;
import net.minecraft.server.Packet29DestroyEntity;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import com.nijiko.permissions.PermissionHandler;
import com.nijikokun.bukkit.Permissions.Permissions;


/**
 * Vanish for Bukkit
 * 
 * @author Nodren
 */
public class Vanish extends JavaPlugin
{
	public static PermissionHandler Permissions = null;
	public int RANGE;
	public int TOTAL_REFRESHES;
	public int REFRESH_TIMER;

	private Timer timer = new Timer();

	public List<Player> invisible = new ArrayList<Player>();

	private final VanishPlayerListener playerListener = new VanishPlayerListener(this);
	private final VanishEntityListener entityListener = new VanishEntityListener(this);
	private final Logger log = Logger.getLogger("Minecraft");

	public void onDisable()
	{
		timer.cancel();
		log.info(getDescription().getName() + " " + getDescription().getVersion() + " unloaded.");
	}

	public void onEnable()
	{
		getDataFolder().mkdirs();

		File yml = new File(getDataFolder(), "config.yml");
		if (!yml.exists())
		{
			try
			{
				yml.createNewFile();
			}
			catch (IOException ex)
			{
			}
		}
		setupPermissions();
		RANGE = getConfiguration().getInt("range", 512);
		TOTAL_REFRESHES = getConfiguration().getInt("total_refreshes", 10);
		REFRESH_TIMER = getConfiguration().getInt("refresh_timer", 2);

		PluginManager pm = getServer().getPluginManager();
		pm.registerEvent(Event.Type.PLAYER_JOIN, playerListener, Priority.Monitor, this);
		pm.registerEvent(Event.Type.PLAYER_QUIT, playerListener, Priority.Monitor, this);
		pm.registerEvent(Event.Type.PLAYER_TELEPORT, playerListener, Priority.Monitor, this);
		pm.registerEvent(Event.Type.ENTITY_DAMAGE, entityListener, Priority.Highest, this);

		log.info(getDescription().getName() + " " + getDescription().getVersion() + " loaded.");

		timer.schedule(new UpdateInvisibleTimerTask(true), (1000 * 60) * REFRESH_TIMER);
	}

	@SuppressWarnings("static-access")
	public void setupPermissions()
	{
		Plugin test = this.getServer().getPluginManager().getPlugin("Permissions");

		if (this.Permissions == null)
		{
			if (test != null)
			{
				this.getServer().getPluginManager().enablePlugin(test);
				this.Permissions = ((Permissions) test).getHandler();
			}
			else
			{
				log.info(getDescription().getName() + " version " + getDescription().getVersion() + "not enabled. Permissions not detected");
			}
		}
	}

	public boolean check(CommandSender sender, String permNode)
	{
		if (sender instanceof Player)
		{
			if (Permissions == null)
			{
				if (sender.isOp()) { return true; }
				return false;
			}
			else
			{
				Player player = (Player) sender;
				return Permissions.has(player, permNode);
			}
		}
		else if (sender instanceof ConsoleCommandSender)
		{
			return true;
		}
		else
		{
			return false;
		}
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String commandLabel, String[] args)
	{
		if ((command.getName().equalsIgnoreCase("vanish")) || (command.getName().equalsIgnoreCase("poof")))
		{
			if ((args.length == 1) && (args[0].equalsIgnoreCase("list")))
			{
				list(sender);
				return true;
			}
			vanishCommand(sender);
			return true;
		}
		return false;
	}

	private void list(CommandSender sender)
	{
		if (!check(sender, "vanish.vanish.list")) { return; }
		if (invisible.size() == 0)
		{
			sender.sendMessage(ChatColor.RED + "No invisible players found");
			return;
		}
		String message = "List of Invisible Players: ";
		int i = 0;
		for (Player InvisiblePlayer : invisible)
		{
			message += InvisiblePlayer.getDisplayName();
			i++;
			if (i != invisible.size())
			{
				message += " ";
			}
		}
		sender.sendMessage(ChatColor.RED + message + ChatColor.WHITE + " ");
	}

	private void vanishCommand(CommandSender sender)
	{
		if (!check(sender, "vanish.vanish")) { return; }
		if (sender instanceof Player)
		{
			Player player = (Player) sender;
			vanish(player);
			return;
		}
		sender.sendMessage("That doesn't work from here");
	}

	private void invisible(Player p1, Player p2)
	{
		invisible(p1, p2, false);
	}

	private void invisible(Player p1, Player p2, boolean force)
	{
		if ((!force) && (check(p2, "vanish.dont.hide"))) { return; }
		CraftPlayer hide = (CraftPlayer) p1;
		CraftPlayer hideFrom = (CraftPlayer) p2;
		hideFrom.getHandle().netServerHandler.sendPacket(new Packet29DestroyEntity(hide.getEntityId()));
	}

	private void uninvisible(Player p1, Player p2)
	{
		CraftPlayer unHide = (CraftPlayer) p1;
		CraftPlayer unHideFrom = (CraftPlayer) p2;
		unHideFrom.getHandle().netServerHandler.sendPacket(new Packet20NamedEntitySpawn(unHide.getHandle()));
	}

	public void vanish(Player player)
	{
		if (invisible.contains(player))
		{
			reappear(player);
			return;
		}
		invisible.add(player);
		Player[] playerList = getServer().getOnlinePlayers();
		for (Player p : playerList)
		{
			if (getDistance(player, p) <= RANGE && !p.equals(player))
			{
				invisible(player, p);
			}
		}
		log.info(player.getName() + " disappeared.");
		player.sendMessage(ChatColor.RED + "Poof!");

	}

	public void reappear(Player player)
	{
		if (invisible.contains(player))
		{
			invisible.remove(player);
			// make someone really disappear if there's any doubt, should remove
			// cloning
			updateInvisibleForPlayer(player, true);
			Player[] playerList = getServer().getOnlinePlayers();
			for (Player p : playerList)
			{
				if (getDistance(player, p) < RANGE && !p.equals(player))
				{
					uninvisible(player, p);
				}
			}
			log.info(player.getName() + " reappeared.");
			player.sendMessage(ChatColor.RED + "You have reappeared!");

		}
	}

	public void reappearAll()
	{
		log.info("Everyone is going reappear.");
		for (Player InvisiblePlayer : invisible)
		{
			reappear(InvisiblePlayer);
		}
		invisible.clear();
	}

	public void updateInvisibleForPlayer(Player player)
	{
		updateInvisibleForPlayer(player, false);
	}

	public void updateInvisibleForPlayer(Player player, boolean force)
	{
		Player[] playerList = getServer().getOnlinePlayers();
		for (Player p : playerList)
		{
			if (getDistance(player, p) <= RANGE && !p.equals(player))
			{
				invisible(player, p, force);
			}
		}
	}

	public void updateInvisibleForAll()
	{
		Player[] playerList = getServer().getOnlinePlayers();
		for (Player invisiblePlayer : invisible)
		{
			for (Player p : playerList)
			{
				if (getDistance(invisiblePlayer, p) <= RANGE && !p.equals(invisiblePlayer))
				{
					invisible(invisiblePlayer, p);
				}
			}
		}
	}

	public void updateInvisibleForAll(boolean startTimer)
	{
		updateInvisibleForAll();
		if (startTimer)
		{
			timer.schedule(new UpdateInvisibleTimerTask(true), (1000 * 60) * REFRESH_TIMER);
		}
	}

	public void updateInvisible(Player player)
	{
		for (Player invisiblePlayer : invisible)
		{
			if (getDistance(invisiblePlayer, player) <= RANGE && !player.equals(invisiblePlayer))
			{
				invisible(invisiblePlayer, player);
			}
		}
	}

	public double getDistance(Player player1, Player player2)
	{
		Location loc1 = player1.getLocation();
		Location loc2 = player1.getLocation();
		return Math.sqrt(Math.pow(loc1.getX() - loc2.getX(), 2) + Math.pow(loc1.getY() - loc2.getY(), 2) + Math.pow(loc1.getZ() - loc2.getZ(), 2));
	}

	public void updateInvisibleOnTimer()
	{
		updateInvisibleForAll();
		Timer timer = new Timer();
		int i = 0;
		while (i < TOTAL_REFRESHES)
		{
			i++;
			timer.schedule(new UpdateInvisibleTimerTask(), i * 1000);
		}
	}

	public class UpdateInvisibleTimerTask extends TimerTask
	{
		private boolean startTimer = false;

		public UpdateInvisibleTimerTask()
		{

		}

		public UpdateInvisibleTimerTask(boolean startTimer)
		{
			this.startTimer = startTimer;
		}

		public void run()
		{
			updateInvisibleForAll(startTimer);
		}
	}

}
