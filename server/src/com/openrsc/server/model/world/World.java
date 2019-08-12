package com.openrsc.server.model.world;

import com.openrsc.server.Constants;
import com.openrsc.server.Server;
import com.openrsc.server.content.DropTable;
import com.openrsc.server.content.clan.ClanManager;
import com.openrsc.server.content.minigame.fishingtrawler.FishingTrawler;
import com.openrsc.server.content.minigame.fishingtrawler.FishingTrawler.TrawlerBoat;
import com.openrsc.server.content.party.PartyManager;
import com.openrsc.server.event.SingleEvent;
import com.openrsc.server.external.GameObjectLoc;
import com.openrsc.server.external.ItemId;
import com.openrsc.server.external.NPCLoc;
import com.openrsc.server.external.NpcId;
import com.openrsc.server.io.WorldLoader;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.Shop;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.snapshot.Snapshot;
import com.openrsc.server.model.world.region.RegionManager;
import com.openrsc.server.model.world.region.TileValue;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.MiniGameInterface;
import com.openrsc.server.plugins.QuestInterface;
import com.openrsc.server.sql.GameLogging;
import com.openrsc.server.sql.WorldPopulation;
import com.openrsc.server.sql.query.PlayerOnlineFlagQuery;
import com.openrsc.server.sql.query.logs.LoginLog;
import com.openrsc.server.sql.web.AvatarGenerator;
import com.openrsc.server.util.EntityList;
import com.openrsc.server.util.IPTracker;
import com.openrsc.server.util.SimpleSubscriber;
import com.openrsc.server.util.ThreadSafeIPTracker;
import com.openrsc.server.util.rsc.CollisionFlag;
import com.openrsc.server.util.rsc.MessageType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

public final class World implements SimpleSubscriber<FishingTrawler> {

	public static final int MAX_HEIGHT = 4032; // 3776
	public static final int MAX_WIDTH = 1008; // 944
	/**
	 * The asynchronous logger.
	 */
	private static final Logger LOGGER = LogManager.getLogger();
	/**
	 * IP filtering for wilderness entry
	 */

	private final static IPTracker<String> wildernessIPTracker = new ThreadSafeIPTracker<String>();
	/**
	 * Avatar generator upon logout save to PNG.
	 */
	private final static AvatarGenerator avatarGenerator = new AvatarGenerator();
	private static final String[] objectsProjectileClipAllowed = {"gravestone", "sign", "broken pillar", "bone",
		"animalskull", "skull", "egg", "eggs", "ladder", "torch", "rock", "treestump", "railing",
		"railings", "gate", "fence", "table", "smashed chair", "smashed table", "longtable", "fence", "chair"};
	public static int membersWildStart = 48;
	public static int membersWildMax = 56;
	public static int godSpellsStart = 1;
	public static int godSpellsMax = 5;
	public static final int GLORY_TELEPORT_LIMIT = 30;
	public static boolean EVENT = false;
	public static int EVENT_X = -1, EVENT_Y = -1;
	public static int EVENT_COMBAT_MIN, EVENT_COMBAT_MAX;
	public static boolean WORLD_TELEGRAB_TOGGLE = false;
	private static World worldInstance;
	private final WorldLoader db = new WorldLoader();
	private final EntityList<Npc> npcs = new EntityList<Npc>(4000);
	private final EntityList<Player> players = new EntityList<Player>(2000);
	private final List<QuestInterface> quests = new LinkedList<QuestInterface>();
	private final List<MiniGameInterface> minigames = new LinkedList<MiniGameInterface>();
	private final List<Shop> shopData = new ArrayList<Shop>();
	private final List<Shop> shops = new ArrayList<Shop>();
	private final TileValue[][] tiles = new TileValue[MAX_WIDTH][MAX_HEIGHT];
	public WorldLoader wl;

	private Map<Player, Boolean> playerUnderAttackMap = new HashMap<Player, Boolean>();
	private Map<Npc, Boolean> npcUnderAttackMap = new HashMap<Npc, Boolean>();

	public DropTable gemTable;
	public DropTable standardTable;
	public DropTable ultraTable;

	/**
	 * Double ended queue to store snapshots into
	 */
	private Deque<Snapshot> snapshots = new LinkedList<Snapshot>();
	private Map<TrawlerBoat, FishingTrawler> fishingTrawler = new HashMap<TrawlerBoat, FishingTrawler>();

	public static IPTracker<String> getWildernessIPTracker() {
		return wildernessIPTracker;
	}

	public static synchronized World getWorld() {
		if (worldInstance == null) {
			worldInstance = new World();

		}
		return worldInstance;
	}

	private static void shutdownCheck() {
		Server.getServer().getGameEventHandler().add(new SingleEvent(null, 1000, "Shutdown Check") {
			public void action() {
				int currSecond = (int) (System.currentTimeMillis() / 1000.0 - (4 * 3600));
				if (Constants.GameServer.AUTO_SERVER_RESTART) {
					if ((int) ((currSecond / 3600.0) % 24) == Constants.GameServer.RESTART_HOUR
						&& (int) ((currSecond / 60.0) % 60) >= Constants.GameServer.RESTART_MINUTE) {
						int seconds = Constants.GameServer.RESTART_DELAY;
						int minutes = seconds / 60;
						int remainder = seconds % 60;
						if (Server.getServer().restart(seconds)) {
							for (Player p : World.getWorld().getPlayers()) {
								ActionSender.startShutdown(p, seconds);
							}
						}
					}
				}
				if (Constants.GameServer.AUTO_SERVER_RESTART_2) {
					if ((int) ((currSecond / 3600.0) % 24) == Constants.GameServer.RESTART_HOUR_2
						&& (int) ((currSecond / 60.0) % 60) >= Constants.GameServer.RESTART_MINUTE_2) {
						int seconds = Constants.GameServer.RESTART_DELAY_2;
						int minutes = seconds / 60;
						int remainder = seconds % 60;
						if (Server.getServer().restart(seconds)) {
							for (Player p : World.getWorld().getPlayers()) {
								ActionSender.startShutdown(p, seconds);
							}
						}
					}
				}
				shutdownCheck();
			}
		});
	}

	public static void restartCommand() {
		Server.getServer().getGameEventHandler().add(new SingleEvent(null, 1000, "Restart Command") {
			public void action() {
				int currSecond = (int) (System.currentTimeMillis() / 1000.0 - (4 * 3600));
				int seconds = 10;
				int minutes = seconds / 60;
				int remainder = seconds % 60;
				if (Server.getServer().restart(seconds)) {
					for (Player p : World.getWorld().getPlayers()) {
						ActionSender.startShutdown(p, seconds);
					}
				}
				restartCommand();
			}
		});
	}

	/**
	 * Returns double-ended queue for snapshots.
	 */
	public synchronized Deque<Snapshot> getSnapshots() {
		return snapshots;
	}

	/**
	 * Add entry to snapshots
	 */
	public synchronized void addEntryToSnapshots(Snapshot snapshot) {
		snapshots.offerFirst(snapshot);
	}

	public void addShopData(Shop... shop) {
		shopData.addAll(Arrays.asList(shop));
	}

	public void clearShopData() {
		shopData.clear();
	}

	public int countNpcs() {
		return npcs.size();
	}

	public int countPlayers() {
		return players.size();
	}

	public void delayedRemoveObject(final GameObject object, final int delay) {
		Server.getServer().getGameEventHandler().add(new SingleEvent(null, delay, "Delayed Remove Object") {
			public void action() {
				unregisterGameObject(object);
			}
		});
	}

	/**
	 * Adds a DelayedEvent that will spawn a GameObject
	 */
	public void delayedSpawnObject(final GameObjectLoc loc, final int respawnTime, final boolean forceFullBlock) {
		Server.getServer().getGameEventHandler().add(new SingleEvent(null, respawnTime, "Delayed Spawn Object") {
			public void action() {
				registerGameObject(new GameObject(loc));
				if (forceFullBlock) {
					getTile(loc.getX(), loc.getY()).traversalMask |= 64;
				}
			}
		});
	}

	public void delayedSpawnObject(final GameObjectLoc loc, final int respawnTime) {
		this.delayedSpawnObject(loc, respawnTime, false);
	}

	public WorldLoader getDB() {
		return db;
	}

	public Npc getNpc(int idx) {
		try {
			return npcs.get(idx);
		} catch (Exception e) {
			return null;
		}
	}

	public Npc getNpc(int id, int minX, int maxX, int minY, int maxY) {
		for (Npc npc : npcs) {
			if (npc.getID() == id && npc.getX() >= minX && npc.getX() <= maxX && npc.getY() >= minY
				&& npc.getY() <= maxY) {
				return npc;
			}
		}
		return null;
	}

	public Npc getNpc(int id, int minX, int maxX, int minY, int maxY, boolean notNull) {
		for (Npc npc : npcs) {
			if (npc.getID() == id && npc.getX() >= minX && npc.getX() <= maxX && npc.getY() >= minY
				&& npc.getY() <= maxY) {
				if (!npc.inCombat()) {
					return npc;
				}
			}
		}
		return null;
	}

	public Npc getNpcById(int id) {
		for (Npc npc : npcs) {
			if (npc.getID() == id) {
				return npc;
			}
		}
		return null;
	}

	/**
	 * Gets the list of npcs on the server
	 */
	public synchronized EntityList<Npc> getNpcs() {
		return npcs;
	}

	/**
	 * Gets a Player by their server index
	 */
	public Player getPlayer(int idx) {
		try {
			Player p = players.get(idx);
			return p;
		} catch (Exception e) {
			return null;
		}

	}

	/**
	 * Gets a player by their username hash
	 */
	public Player getPlayer(long usernameHash) {
		for (Player p : players) {
			if (p.getUsernameHash() == usernameHash) {
				return p;
			}
		}
		return null;
	}

	/**
	 * Gets a player by their username hash
	 */
	public Player getPlayerID(int databaseID) {
		for (Player p : players) {
			if (p.getDatabaseID() == databaseID) {
				return p;
			}
		}
		return null;
	}

	public synchronized EntityList<Player> getPlayers() {
		return players;
	}

	/**
	 * Finds a specific quest by ID
	 *
	 * @param q
	 * @return
	 * @throws IllegalArgumentException when a quest by that ID isn't found
	 */
	public QuestInterface getQuest(int q) throws IllegalArgumentException {
		for (QuestInterface quest : this.getQuests()) {
			if (quest.getQuestId() == q) {
				return quest;
			}
		}
		throw new IllegalArgumentException("No quest found");
	}

	/**
	 * Finds a specific miniquest/minigame by ID
	 *
	 * @param m
	 * @return
	 * @throws IllegalArgumentException when a quest by that ID isn't found
	 */
	public MiniGameInterface getMiniGame(int m) throws IllegalArgumentException {
		for (MiniGameInterface minigame : this.getMiniGames()) {
			if (minigame.getMiniGameId() == m) {
				return minigame;
			}
		}
		throw new IllegalArgumentException("No mini-game found");
	}

	public List<QuestInterface> getQuests() {
		return quests;
	}

	public List<MiniGameInterface> getMiniGames() {
		return minigames;
	}

	public List<Shop> getShops() {
		return shops;
	}

	public TileValue getTile(int x, int y) {
		if (!withinWorld(x, y)) {
			return null;
		}
		TileValue t = tiles[x][y];
		if (t == null) {
			t = new TileValue();
			tiles[x][y] = t;
		}
		return t;
	}

	public TileValue getTile(Point p) {
		return getTile(p.getX(), p.getY());
	}

	public boolean hasNpc(Npc n) {
		return npcs.contains(n);
	}
	/*
	 * Note to self - Remove CollidingWallObject, Remove getWallGameObject, And others if this doesn't work in long run.
	 * Classes - viewArea, world, region, gameObjectAction, GameObjectWallAction, ItemUseOnObject
	 */

	public boolean hasPlayer(Player p) {
		return players.contains(p);
	}

	public boolean isLoggedIn(long usernameHash) {
		Player friend = getPlayer(usernameHash);
		if (friend != null) {
			return friend.loggedIn();
		}
		return false;
	}

	public void load() {
		try {
			ClanManager.init();
			PartyManager.init();
			worldInstance.wl = new WorldLoader();
			worldInstance.wl.loadWorld(worldInstance);
			WorldPopulation.populateWorld(worldInstance);
			shutdownCheck();
			if (Constants.GameServer.WANT_NEW_RARE_DROP_TABLES)
				initializeRareDropTables();
			//AchievementSystem.loadAchievements();
			// Server.getServer().getEventHandler().add(new WildernessCycleEvent());
			//setFishingTrawler(new FishingTrawler());
			//Server.getServer().getEventHandler().add(getFishingTrawler());
		} catch (Exception e) {
			LOGGER.catching(e);
		}
	}

	public void registerGameObject(GameObject o) {
		Point objectCoordinates = Point.location(o.getLoc().getX(), o.getLoc().getY());
		GameObject collidingGameObject = RegionManager.getRegion(objectCoordinates).getGameObject(objectCoordinates);
		GameObject collidingWallObject = RegionManager.getRegion(objectCoordinates).getWallGameObject(objectCoordinates, o.getLoc().getDirection());
		if (collidingGameObject != null && o.getType() == 0) {
			unregisterGameObject(collidingGameObject);
		}
		if (collidingWallObject != null && o.getType() == 1) {
			unregisterGameObject(collidingWallObject);
		}
		o.setLocation(Point.location(o.getLoc().getX(), o.getLoc().getY()));

		int dir = o.getDirection();
		if (o.getID() == 1147) {
			return;
		}
		switch (o.getType()) {
			case 0:
				if (o.getGameObjectDef().getType() != 1 && o.getGameObjectDef().getType() != 2) {
					return;
				}
				int width, height;
				if (dir == 0 || dir == 4) {
					width = o.getGameObjectDef().getWidth();
					height = o.getGameObjectDef().getHeight();
				} else {
					height = o.getGameObjectDef().getWidth();
					width = o.getGameObjectDef().getHeight();
				}
				for (int x = o.getX(); x < o.getX() + width; ++x) {
					for (int y = o.getY(); y < o.getY() + height; ++y) {
						if (isProjectileClipAllowed(o)) {
							getTile(x, y).projectileAllowed = true;
						}
						if (o.getGameObjectDef().getType() == 1) {
							getTile(x, y).traversalMask |= CollisionFlag.FULL_BLOCK_C;
						} else if (dir == 0) {
							getTile(x, y).traversalMask |= CollisionFlag.WALL_EAST;
							if (getTile(x - 1, y) != null)
								getTile(x - 1, y).traversalMask |= CollisionFlag.WALL_WEST;
						} else if (dir == 2) {
							getTile(x, y).traversalMask |= CollisionFlag.WALL_SOUTH;
							if (getTile(x, y + 1) != null)
								getTile(x, y + 1).traversalMask |= CollisionFlag.WALL_NORTH;
						} else if (dir == 4) {
							getTile(x, y).traversalMask |= CollisionFlag.WALL_WEST;
							if (getTile(x + 1, y) != null)
								getTile(x + 1, y).traversalMask |= CollisionFlag.WALL_EAST;
						} else if (dir == 6) {
							getTile(x, y).traversalMask |= CollisionFlag.WALL_NORTH;
							if (getTile(x, y - 1) != null)
								getTile(x, y - 1).traversalMask |= CollisionFlag.WALL_SOUTH;
						}
					}
				}
				break;

			case 1:
				if (o.getDoorDef().getDoorType() != 1) {
					return;
				}
				int x = o.getX(), y = o.getY();
				if (isProjectileClipAllowed(o)) {
					getTile(x, y).projectileAllowed = true;
				}
				if (dir == 0) {

					getTile(x, y).traversalMask |= CollisionFlag.WALL_NORTH;
					if (getTile(x, y - 1) != null)
						getTile(x, y - 1).traversalMask |= CollisionFlag.WALL_SOUTH;
				} else if (dir == 1) {
					getTile(x, y).traversalMask |= CollisionFlag.WALL_EAST;
					if (getTile(x - 1, y) != null)
						getTile(x - 1, y).traversalMask |= CollisionFlag.WALL_WEST;
				} else if (dir == 2) {
					getTile(x, y).traversalMask |= CollisionFlag.FULL_BLOCK_A;
				} else if (dir == 3) {
					getTile(x, y).traversalMask |= CollisionFlag.FULL_BLOCK_B;
				}
				break;
		}
	}

	private boolean isProjectileClipAllowed(GameObject o) {
		for (String s : objectsProjectileClipAllowed) {
			if (o.getType() == 0) {
				// there are many of the objects that need to
				// have clip enabled.
				if (!o.getGameObjectDef().getName().equalsIgnoreCase("tree")) {
					if (o.getGameObjectDef().getHeight() == 1 && o.getGameObjectDef().getWidth() == 1 && !o.getGameObjectDef().getName().toLowerCase().equalsIgnoreCase("chest"))
						return true;
				}
				if (o.getGameObjectDef().getName().toLowerCase().equalsIgnoreCase(s)) {
					return true;
				}
			} else if (o.getType() == 1) {
				if (o.getDoorDef().getName().toLowerCase().equalsIgnoreCase(s)) {
					return true;
				}
			}
		}
		return false;
	}

	public void registerItem(final GroundItem i) {
		registerItem(i, 128000);
	}

	public void registerItem(final GroundItem i, int delayTime) {
		try {
			if (i.getLoc() == null) {
				Server.getServer().getGameEventHandler().add(new SingleEvent(null, delayTime, "Register Item") {
					public void action() {
						unregisterItem(i);
					}
				});
			}
		} catch (Exception e) {
			i.remove();
			LOGGER.catching(e);
		}
	}

	public Npc registerNpc(Npc n) {
		NPCLoc npc = n.getLoc();
		if (npc.startX < npc.minX || npc.startX > npc.maxX || npc.startY < npc.minY || npc.startY > npc.maxY
			|| (getTile(npc.startX, npc.startY).overlay & 64) != 0) {
			LOGGER.error("Broken Npc: <id>" + npc.id + "</id><startX>" + npc.startX + "</startX><startY>"
				+ npc.startY + "</startY>");
		}

		npcs.add(n);
		return n;
	}

	public void registerObjects(GameObject... obs) {
		for (GameObject o : obs) {
			o.setLocation(Point.location(o.getLoc().getX(), o.getLoc().getY()));
		}
	}

	public boolean registerPlayer(Player player) {

		if (!players.contains(player)) {
			player.setUUID(UUID.randomUUID().toString());

			player.setLoggedIn(true);
			player.setBusy(false);

			players.add(player);
			player.updateRegion();
			if (Server.getServer().getPlayerDataProcessor() != null) {
				GameLogging.addQuery(new PlayerOnlineFlagQuery(player.getDatabaseID(), player.getCurrentIP(), true));
				GameLogging.addQuery(new LoginLog(player.getDatabaseID(), player.getCurrentIP()));
			}
			for (Player other : getPlayers()) {
				other.getSocial().alertOfLogin(player);
			}
			ClanManager.checkAndAttachToClan(player);
			PartyManager.checkAndAttachToParty(player);
			LOGGER.info("Registered " + player.getUsername() + " to server");
			return true;
		}
		return false;
	}

	public void registerQuest(QuestInterface quest) {
		if (quest.getQuestName() == null) {
			throw new IllegalArgumentException("Quest name cannot be null");
		} else if (quest.getQuestName().length() > 40) {
			throw new IllegalArgumentException("Quest name cannot be longer then 40 characters");
		}
		for (QuestInterface q : quests) {
			if (q.getQuestId() == quest.getQuestId()) {
				throw new IllegalArgumentException("Quest ID must be unique");
			}
		}
		quests.add(quest);
	}

	public void registerMiniGame(MiniGameInterface minigame) {
		if (minigame.getMiniGameName() == null) {
			throw new IllegalArgumentException("Minigame name cannot be null");
		} else if (minigame.getMiniGameName().length() > 40) {
			throw new IllegalArgumentException("Minigame name cannot be longer then 40 characters");
		}
		for (MiniGameInterface m : minigames) {
			if (m.getMiniGameId() == minigame.getMiniGameId()) {
				throw new IllegalArgumentException("MiniGame ID must be unique");
			}
		}
		minigames.add(minigame);
	}

	public void registerShop(Shop shop) {
		shops.add(shop);
	}

	public void registerShops(Shop... shop) {
		shops.addAll(Arrays.asList(shop));
	}

	public void replaceGameObject(GameObject old, GameObject _new) {
		unregisterGameObject(old);
		registerGameObject(_new);
	}

	public void sendKilledUpdate(long killedHash, long killerHash, int type) {
		for (final Player player : players)
			ActionSender.sendKillUpdate(player, killedHash, killerHash, type);
	}

	public void sendModAnnouncement(String string) {
		for (Player p : players) {
			if (p.isMod()) {
				p.message("[@cya@SERVER@whi@]: " + string);
			}
		}
	}

	public void sendWorldAnnouncement(String msg) {
		if (Constants.GameServer.WANT_GLOBAL_CHAT) {
			for (Player p : getPlayers()) {
				p.playerServerMessage(MessageType.QUEST, "@gre@[Global] @whi@" + msg);
			}
		}
	}

	public void sendWorldMessage(String msg) {
		synchronized (players) {
			for (Player p : players) {
				p.playerServerMessage(MessageType.QUEST, msg);
			}
		}
	}

	/**
	 * Removes an object from the server
	 */
	public void unregisterGameObject(GameObject o) {
		o.remove();
		int dir = o.getDirection();
		switch (o.getType()) {
			case 0:
				if (o.getGameObjectDef().getType() != 1 && o.getGameObjectDef().getType() != 2) {
					return;
				}
				int width, height;
				if (dir == 0 || dir == 4) {
					width = o.getGameObjectDef().getWidth();
					height = o.getGameObjectDef().getHeight();
				} else {
					height = o.getGameObjectDef().getWidth();
					width = o.getGameObjectDef().getHeight();
				}
				for (int x = o.getX(); x < o.getX() + width; ++x) {
					for (int y = o.getY(); y < o.getY() + height; ++y) {
						if (o.getGameObjectDef().getType() == 1) {
							getTile(x, y).traversalMask &= 0xffbf;
						} else if (dir == 0) {
							getTile(x, y).traversalMask &= 0xfffd;
							getTile(x - 1, y).traversalMask &= 65535 - 8;
						} else if (dir == 2) {
							getTile(x, y).traversalMask &= 0xfffb;
							getTile(x, y + 1).traversalMask &= 65535 - 1;
						} else if (dir == 4) {
							getTile(x, y).traversalMask &= 0xfff7;
							getTile(x + 1, y).traversalMask &= 65535 - 2;
						} else if (dir == 6) {
							getTile(x, y).traversalMask &= 0xfffe;
							getTile(x, y - 1).traversalMask &= 65535 - 4;
						}
					}
				}
				break;
			case 1:
				if (o.getDoorDef().getDoorType() != 1) {
					return;
				}
				int x = o.getX(), y = o.getY();
				if (dir == 0) {
					getTile(x, y).traversalMask &= 0xfffe;
					getTile(x, y - 1).traversalMask &= 65535 - 4;
				} else if (dir == 1) {
					getTile(x, y).traversalMask &= 0xfffd;
					getTile(x - 1, y).traversalMask &= 65535 - 8;
				} else if (dir == 2) {
					getTile(x, y).traversalMask &= 0xffef;
				} else if (dir == 3) {
					getTile(x, y).traversalMask &= 0xffdf;
				}
				break;
		}
	}

	/**
	 * Removes an item from the server
	 */
	public void unregisterItem(GroundItem i) {
		i.remove();
	}

	/**
	 * Removes an npc from the server
	 */
	public void unregisterNpc(Npc n) {
		if (hasNpc(n)) {
			npcs.remove(n);
		}
		n.superRemove();
	}

	/**
	 * Removes a player from the server and saves their account
	 */
	public void unregisterPlayer(final Player player) {
		try {
			if (Server.getServer().getPlayerDataProcessor() != null) {
				GameLogging.addQuery(new PlayerOnlineFlagQuery(player.getDatabaseID(), false));
				if (Constants.GameServer.AVATAR_GENERATOR)
					avatarGenerator.generateAvatar(player.getDatabaseID(), player.getSettings().getAppearance(), player.getWornItems());
			}
			player.logout();
			LOGGER.info("Unregistered " + player.getUsername() + " from player list.");
		} catch (Exception e) {
			LOGGER.catching(e);
		}

	}

	public void unregisterQuest(QuestInterface quest) {
		if (quests.contains(quest)) {
			quests.remove(quest);
		}
	}

	public void unregisterMiniGame(MiniGameInterface minigame) {
		if (minigames.contains(minigame)) {
			minigames.remove(minigame);
		}
	}

	/**
	 * Are the given coords within the world boundaries
	 */
	public boolean withinWorld(int x, int y) {
		return x >= 0 && x < MAX_WIDTH && y >= 0 && y < MAX_HEIGHT;
	}

	public FishingTrawler getFishingTrawler(TrawlerBoat boat) {
		FishingTrawler trawlerInstance = fishingTrawler.get(boat);
		if (trawlerInstance != null && !trawlerInstance.shouldRemove()) {
			return trawlerInstance;
		} else {
			trawlerInstance = new FishingTrawler(boat);
			trawlerInstance.register(this);
			fishingTrawler.put(boat, trawlerInstance);
			Server.getServer().getGameEventHandler().add(trawlerInstance);
			return trawlerInstance;
		}
	}

	public FishingTrawler getFishingTrawler(Player p) {
		if (fishingTrawler.get(TrawlerBoat.EAST) != null && fishingTrawler.get(TrawlerBoat.EAST).getPlayers().contains(p)) {
			return fishingTrawler.get(TrawlerBoat.EAST);
		}
		if (fishingTrawler.get(TrawlerBoat.WEST) != null && fishingTrawler.get(TrawlerBoat.WEST).getPlayers().contains(p)) {
			return fishingTrawler.get(TrawlerBoat.WEST);
		}
		return null;
	}

	// notified when event is stopped to deallocate reference
	@Override
	public void update(FishingTrawler ctx) {
		if (ctx != null && ctx.getPlayers().size() == 0) {
			fishingTrawler.put(ctx.getBoat(), null);
		}
	}

	public void produceUnderAttack(Player p) {
		playerUnderAttackMap.put(p, true);
	}

	public void produceUnderAttack(Npc n) {
		npcUnderAttackMap.put(n, true);
	}

	public boolean checkUnderAttack(Player p) {
		return playerUnderAttackMap.getOrDefault(p, false);
	}

	public boolean checkUnderAttack(Npc n) {
		return npcUnderAttackMap.getOrDefault(n, false);
	}

	public void releaseUnderAttack(Player p) {
		if (playerUnderAttackMap.containsKey(p)) {
			playerUnderAttackMap.remove(p);
		}
	}

	public void releaseUnderAttack(Npc n) {
		if (npcUnderAttackMap.containsKey(n)) {
			npcUnderAttackMap.remove(n);
		}
	}

	public synchronized Map<Player, Boolean> getPlayersUnderAttack() {
		return playerUnderAttackMap;
	}

	public synchronized Map<Npc, Boolean> getNpcsUnderAttack() {
		return npcUnderAttackMap;
	}

	private void initializeRareDropTables() {
		gemTable = new DropTable();
		standardTable = new DropTable();
		ultraTable = new DropTable();

		//ITEMS
		gemTable.addEmptyDrop(45);
		gemTable.addItemDrop(ItemId.FEATHER.id(), 1, 18);
		gemTable.addItemDrop(ItemId.UNCUT_SAPPHIRE.id(), 1, 32);
		gemTable.addItemDrop(ItemId.UNCUT_EMERALD.id(), 1, 16);
		gemTable.addItemDrop(ItemId.UNCUT_RUBY.id(), 1, 8);
		gemTable.addItemDrop(ItemId.UNCUT_DIAMOND.id(), 1, 4);
		gemTable.addItemDrop(ItemId.TOOTH_KEY_HALF.id(), 1, 2);
		gemTable.addItemDrop(ItemId.LOOP_KEY_HALF.id(), 1, 2);
		gemTable.addTableDrop(ultraTable, 1);

		standardTable.addItemDrop(ItemId.NATURE_RUNE.id(),50,3);
		standardTable.addItemDrop(ItemId.LAW_RUNE.id(),40,2);
		standardTable.addItemDrop(ItemId.DEATH_RUNE.id(),50,2);
		standardTable.addItemDrop(ItemId.STEEL_ARROWS.id(),150,2);
		standardTable.addItemDrop(ItemId.RUNE_ARROWS.id(),40,2);
		standardTable.addItemDrop(ItemId.FIRE_RUNE.id(),150,2);
		standardTable.addItemDrop(ItemId.IRON_ARROWS.id(),200,1);
		standardTable.addItemDrop(ItemId.RUNE_2_HANDED_SWORD.id(),1,3);
		standardTable.addItemDrop(ItemId.RUNE_BATTLE_AXE.id(),1,2);
		standardTable.addItemDrop(ItemId.RUNE_SQUARE_SHIELD.id(),1,2);
		standardTable.addItemDrop(ItemId.RUNE_KITE_SHIELD.id(),1,1);
		standardTable.addItemDrop(ItemId.DRAGON_MEDIUM_HELMET.id(),1,1);
		standardTable.addItemDrop(ItemId.COINS.id(),3000,22);
		standardTable.addItemDrop(ItemId.RUNITE_BAR.id(),1,5);
		standardTable.addItemDrop(ItemId.DRAGONSTONE.id(),1,2);
		standardTable.addItemDrop(1624,100,2); //noted silver ore
		standardTable.addItemDrop(ItemId.LOOP_KEY_HALF.id(), 1, 20);
		standardTable.addItemDrop(ItemId.TOOTH_KEY_HALF.id(), 1, 19);
		standardTable.addTableDrop(gemTable, 20);
		standardTable.addTableDrop(ultraTable, 15);

		ultraTable.addEmptyDrop(105);
		ultraTable.addItemDrop(ItemId.COAL.id(), 1, 19);
		ultraTable.addItemDrop(ItemId.LEFT_HALF_DRAGON_SQUARE_SHIELD.id(), 1, 4);

		//MOBS
		gemTable.addAccessor(NpcId.LESSER_DEMON.id(),3,256);
		gemTable.addAccessor(NpcId.LESSER_DEMON_WMAZEKEY.id(),4,256);
		gemTable.addAccessor(NpcId.DWARF.id(),5,256);
		gemTable.addAccessor(NpcId.THUG.id(),5,256);
		gemTable.addAccessor(NpcId.GREATER_DEMON.id(),5,256);
		gemTable.addAccessor(NpcId.ZOMBIE_LVL19.id(),6,256);
		gemTable.addAccessor(NpcId.TARGET_PRACTICE_ZOMBIE.id(),6,256);
		gemTable.addAccessor(NpcId.SKELETON_LVL21.id(),6,256);
		gemTable.addAccessor(NpcId.CHAOS_DRUID.id(),6,256);
		gemTable.addAccessor(137,7,256);//lower level pirates
		gemTable.addAccessor(NpcId.TRIBESMAN.id(),7,256);
		gemTable.addAccessor(NpcId.MOUNTAIN_DWARF.id(),7,256);
		gemTable.addAccessor(NpcId.BANDIT_AGGRESSIVE.id(),7,256);
		gemTable.addAccessor(NpcId.BANDIT_AGGRESSIVE.id(),7,256);
		gemTable.addAccessor(NpcId.PIRATE.id(),8,256);
		gemTable.addAccessor(NpcId.HOBGOBLIN_LVL32.id(),8,256);
		gemTable.addAccessor(NpcId.SKELETON_LVL25.id(),8,256);
		gemTable.addAccessor(NpcId.SKELETON_LVL31.id(),8,256);
		gemTable.addAccessor(NpcId.ZOMBIE_LVL24_GEN.id(),8,256);
		gemTable.addAccessor(NpcId.ZOMBIE_LVL32.id(),8,256);
		gemTable.addAccessor(NpcId.ZOMBIE_WMAZEKEY.id(),8,256);
		gemTable.addAccessor(NpcId.CHAOS_DRUID_WARRIOR.id(),9,256);
		gemTable.addAccessor(NpcId.GIANT.id(),9,256);
		gemTable.addAccessor(NpcId.GUNTHOR_THE_BRAVE.id(),9,256);
		gemTable.addAccessor(NpcId.BLACK_KNIGHT.id(),9,256);
		gemTable.addAccessor(NpcId.BLACK_KNIGHT_AGGRESSIVE.id(),10,256);
		gemTable.addAccessor(NpcId.BLACK_KNIGHT_FORTRESS.id(),12,256);
		gemTable.addAccessor(NpcId.HOBGOBLIN_LVL48.id(),11,256);
		gemTable.addAccessor(NpcId.JAILER.id(),13,256);
		gemTable.addAccessor(NpcId.RENEGADE_KNIGHT.id(),13,256);
		gemTable.addAccessor(195,13,256);//lvl 54 skeletons
		gemTable.addAccessor(NpcId.EARTH_WARRIOR.id(),13,256);
		gemTable.addAccessor(NpcId.ICE_WARRIOR.id(),14,256);
		gemTable.addAccessor(NpcId.CHAOS_DWARF.id(),14,256);
		gemTable.addAccessor(NpcId.WHITE_KNIGHT.id(),14,256);
		gemTable.addAccessor(NpcId.JOGRE.id(),15,256);
		gemTable.addAccessor(NpcId.MOSS_GIANT.id(),15,256);
		gemTable.addAccessor(594,15,256);//moss giant
		gemTable.addAccessor(NpcId.SHADOW_WARRIOR.id(),16,256);
		gemTable.addAccessor(NpcId.OTHERWORLDLY_BEING.id(),17,256);
		gemTable.addAccessor(NpcId.ICE_GIANT.id(),18,256);
		gemTable.addAccessor(NpcId.PALADIN.id(),18,256);
		gemTable.addAccessor(NpcId.LORD_DARQUARIUS.id(),19,256);
		gemTable.addAccessor(NpcId.ICE_QUEEN.id(),22,256);

		standardTable.addAccessor(NpcId.BLUE_DRAGON.id(),3,256);
		standardTable.addAccessor(NpcId.FIRE_GIANT.id(),3,256);
		standardTable.addAccessor(NpcId.HELLHOUND.id(),3,256);
		standardTable.addAccessor(NpcId.RED_DRAGON.id(),4,256);
		standardTable.addAccessor(NpcId.BLACK_DEMON.id(),4,256);
		standardTable.addAccessor(NpcId.BLACK_DEMON_GRANDTREE.id(),4,256);
		standardTable.addAccessor(NpcId.BLACK_DRAGON.id(),5,256);
		standardTable.addAccessor(NpcId.KING_BLACK_DRAGON.id(),12,256);

	}


}
