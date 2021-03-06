package com.openrsc.server.plugins.quests.members.undergroundpass.obstacles;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Quests;
import com.openrsc.server.constants.Skills;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.Area;
import com.openrsc.server.model.world.Areas;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.triggers.OpLocTrigger;
import com.openrsc.server.util.rsc.DataConversions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

import static com.openrsc.server.plugins.Functions.*;

public class UndergroundPassObstaclesMap3 implements OpLocTrigger {
	private static final Logger LOGGER = LogManager.getLogger(UndergroundPassObstaclesMap3.class);
	/**
	 * OBJECT IDs
	 **/
	public static int[] CAGES = {888, 887};
	public static int ZAMORAKIAN_TEMPLE_DOOR = 869;
	public static final int DEMONS_CHEST_OPEN = 911;
	public static final int DEMONS_CHEST_CLOSED = 912;

	public static final int [] PIT_COORDS = {802, 3469};
	public static final Area boundArea = new Area(PIT_COORDS[0] - 24, PIT_COORDS[0] + 24, PIT_COORDS[1] - 24, PIT_COORDS[1] + 24);

	@Override
	public boolean blockOpLoc(Player player, GameObject obj, String command) {
		return inArray(obj.getID(), CAGES) || obj.getID() == DEMONS_CHEST_CLOSED || obj.getID() == ZAMORAKIAN_TEMPLE_DOOR;
	}

	@Override
	public void onOpLoc(Player player, GameObject obj, String command) {
		if (inArray(obj.getID(), CAGES)) {
			if (obj.getID() == CAGES[1]) {
				player.message("the man seems to be entranced");
				mes("the cage is locked");
				delay(1600);
				mes("you search through the bottom of the cage");
				if (!player.getCache().hasKey("cons_on_doll")) {
					player.message("but the souless bieng bites into your arm");
					if (player.getCarriedItems().getEquipment().hasEquipped(ItemId.KLANKS_GAUNTLETS.id())) {
						player.message("klanks gaunlett protects you");
					} else {
						player.damage(((int) getCurrentLevel(player, Skills.HITS) / 10) + 5);
						say(player, null, "aaarrgghh");
					}
				}
				if (!player.getCarriedItems().hasCatalogID(ItemId.IBANS_CONSCIENCE.id(), Optional.of(false)) && !player.getCache().hasKey("cons_on_doll")) {
					player.message("you find the remains of a dove");
					give(player, ItemId.IBANS_CONSCIENCE.id(), 1);
				} else {
					//kosher was separated lol
					if (player.getCarriedItems().getEquipment().hasEquipped(ItemId.KLANKS_GAUNTLETS.id())) {
						player.message("but you find find nothing");
					} else {
						player.message("you find nothing");
					}
				}
			}
			else if (obj.getID() == CAGES[0]) {
				player.message("the man seems to be entranced");
				mes("the cage is locked");
				delay(1600);
				mes("you search through the bottom of the cage");
				player.message("but the souless bieng bites into your arm");
				if (player.getCarriedItems().getEquipment().hasEquipped(ItemId.KLANKS_GAUNTLETS.id())) {
					player.message("klanks gaunlett protects you");
					player.message("but you find find nothing");
				} else {
					player.damage(((int) getCurrentLevel(player, Skills.HITS) / 10) + 5);
					say(player, null, "aaarrgghh");
					player.message("you find nothing");
				}
			}
		}
		else if (obj.getID() == DEMONS_CHEST_CLOSED) {
			mes("you attempt to open the chest");
			if (player.getCarriedItems().hasCatalogID(ItemId.AMULET_OF_OTHAINIAN.id(), Optional.of(false))
				&& player.getCarriedItems().hasCatalogID(ItemId.AMULET_OF_DOOMION.id(), Optional.of(false))
				&& player.getCarriedItems().hasCatalogID(ItemId.AMULET_OF_HOLTHION.id(), Optional.of(false)) && !player.getCache().hasKey("shadow_on_doll")) {
				mes("the three amulets glow red in your satchel");
				player.getCarriedItems().remove(new Item(ItemId.AMULET_OF_OTHAINIAN.id()));
				player.getCarriedItems().remove(new Item(ItemId.AMULET_OF_DOOMION.id()));
				player.getCarriedItems().remove(new Item(ItemId.AMULET_OF_HOLTHION.id()));
				player.message("you place them on the chest and the chest opens");
				changeloc(obj, new GameObject(obj.getWorld(), obj.getLocation(), DEMONS_CHEST_OPEN, obj.getDirection(), obj.getType()));
				addloc(obj.getWorld(), obj.getLoc(), 2000);
				delay(config().GAME_TICK * 2);
				player.message("inside you find a strange dark liquid");
				give(player, ItemId.IBANS_SHADOW.id(), 1);
			} else {
				player.message("but it's magically sealed");
			}
		}
		else if (obj.getID() == ZAMORAKIAN_TEMPLE_DOOR) {
			if (player.getX() <= 792) {
				if (player.getQuestStage(Quests.UNDERGROUND_PASS) == -1 &&
					!config().LOCKED_POST_QUEST_REGIONS_ACCESSIBLE) {
					mes("the temple is in ruins...");
					player.message("...you cannot enter");
					return;
				}
				if (player.getCarriedItems().getEquipment().hasEquipped(ItemId.ROBE_OF_ZAMORAK_TOP.id())
					&& player.getCarriedItems().getEquipment().hasEquipped(ItemId.ROBE_OF_ZAMORAK_BOTTOM.id())) {
					changeloc(obj, new GameObject(obj.getWorld(), obj.getLocation(), 914, obj.getDirection(), obj.getType()));
					addloc(obj.getWorld(), obj.getLoc(), 3000);
					player.teleport(792, 3469);
					delay(config().GAME_TICK);
					player.teleport(795, 3469);
					mes("you pull open the large doors");
					player.message("and walk into the temple");
					if (player.getQuestStage(Quests.UNDERGROUND_PASS) == 7 || (player.getCache().hasKey("poison_on_doll") && player.getCache().hasKey("cons_on_doll")
						&& player.getCache().hasKey("ash_on_doll") && player.getCache().hasKey("shadow_on_doll"))) {
						if (player.getQuestStage(Quests.UNDERGROUND_PASS) == 6) {
							player.updateQuestStage(Quests.UNDERGROUND_PASS, 7);
						}
						player.message("Iban seems to sense danger");
						mes("@yel@Iban: who dares bring the witches magic into my temple");
						mes("his eyes fixate on you as he raises his arm");
						mes("@yel@Iban: an imposter dares desecrate this sacred place..",
							"@yel@Iban: ..home to the only true child of zamorak",
							"@yel@Iban: join the damned, mortal");
						player.message("iban raises his staff to the air");
						mes("a blast of energy comes from ibans staff");
						player.message("you are hit by ibans magic bolt");
						displayTeleportBubble(player, player.getX() + 1, player.getY(), true);
						player.damage(((int) getCurrentLevel(player, Skills.HITS) / 7) + 1);
						say(player, null, "aarrgh");
						mes("@yel@Iban:die foolish mortal");
						long start = System.currentTimeMillis();
						Area area = Areas.getArea("ibans_room");
						try {
							while (true) {
								/* Time-out fail, handle appropriately */
								if (System.currentTimeMillis() - start > 1000 * 60 * 2 && player.getLocation().inBounds(794, 3467, 799, 3471)) {
									player.message("you're blasted out of the temple");
									player.message("@yel@Iban: and stay out");
									player.teleport(790, 3469);
									break;
								}
								/* If player has logged out or not region area */
								if (player.isRemoved() || !player.getLocation().inBounds(boundArea.getMinX(), boundArea.getMinY(),
										boundArea.getMaxX(), boundArea.getMaxY())) {
									break;
								}
								/* ends it */
								if (player.getAttribute("iban_bubble_show", false)) {
									break;
								}
								/* Get random point on the area */
								Point blastPosition = new Point(
									DataConversions.random(area.getMinX(), area.getMaxX()),
									DataConversions.random(area.getMinY(), area.getMaxY()));
								ActionSender.sendTeleBubble(player, blastPosition.getX(), blastPosition.getY(), true);
								if (player.getLocation().withinRange(blastPosition, 1)) {
									/* Blast hit */
									player.damage(((int) getCurrentLevel(player, Skills.HITS) / 6) + 2);
									player.teleport(795, 3469); // insert the coords
									say(player, null, "aarrgh");
									player.message("you're blasted back to the door");
								}
								delay(config().GAME_TICK);
							}
						} catch (Exception e) {
							LOGGER.catching(e);
						}
					} else {
						player.message("inside iban stands preaching at the alter");
					}
				} else {
					mes("The door refuses to open");
					player.message("only followers of zamorak may enter");
				}
			} else {
				changeloc(obj, new GameObject(obj.getWorld(), obj.getLocation(), 914, obj.getDirection(), obj.getType()));
				addloc(obj.getWorld(), obj.getLoc(), config().GAME_TICK * 5);
				player.teleport(794, 3469);
				delay(config().GAME_TICK);
				player.teleport(791, 3469);
				delay(config().GAME_TICK * 2);
				player.message("you pull open the large doors");
				player.message("and walk out of the temple");
			}
		}
	}
}
