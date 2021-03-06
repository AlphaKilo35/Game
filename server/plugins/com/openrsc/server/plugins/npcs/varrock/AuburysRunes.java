package com.openrsc.server.plugins.npcs.varrock;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Quests;
import com.openrsc.server.model.Shop;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.AbstractShop;
import com.openrsc.server.plugins.quests.members.RuneMysteries;

import java.util.ArrayList;

import static com.openrsc.server.plugins.Functions.*;

public final class AuburysRunes extends AbstractShop {

	private final Shop shop = new Shop(false, 3000, 100, 70, 2, new Item(ItemId.FIRE_RUNE.id(),
		50), new Item(ItemId.WATER_RUNE.id(), 50), new Item(ItemId.AIR_RUNE.id(), 50), new Item(ItemId.EARTH_RUNE.id(),
		50), new Item(ItemId.MIND_RUNE.id(), 50), new Item(ItemId.BODY_RUNE.id(), 50));

	@Override
	public boolean blockTalkNpc(final Player player, final Npc n) {
		return n.getID() == NpcId.AUBURY.id();
	}

	@Override
	public Shop[] getShops(World world) {
		return new Shop[]{shop};
	}

	@Override
	public boolean isMembers() {
		return false;
	}

	@Override
	public Shop getShop() {
		return shop;
	}

	@Override
	public void onTalkNpc(final Player player, final Npc n) {
		ArrayList<String> menu = new ArrayList<>();
		menu.add("Yes please");
		menu.add("Oh it's a rune shop. No thank you, then.");

		if (config().WANT_RUNECRAFTING)
			if (player.getQuestStage(Quests.RUNE_MYSTERIES) == 2)
				menu.add("I've been sent here with a package for you.");
			else if (player.getQuestStage(Quests.RUNE_MYSTERIES) == 3)
				menu.add("Rune mysteries");
			else if (player.getQuestStage(Quests.RUNE_MYSTERIES) == -1)
				menu.add("Teleport to rune essence");

		npcsay(player, n, "Do you want to buy some runes?");

		int opt = multi(player, n, false, menu.toArray(new String[menu.size()]));

		if (opt == 0) {
			say(player, n, "Yes Please");
			player.setAccessingShop(shop);
			ActionSender.showShop(player, shop);
		}
		else if (opt == 1) {
			say(player, n, "Oh it's a rune shop. No thank you, then");
			npcsay(player, n,
				"Well if you find someone who does want runes,",
				"send them my way");
		}
		else if (opt == 2) {
			RuneMysteries.auburyDialog(player,n);
		}
	}

	@Override
	public void onOpNpc(Player player, Npc n, String command) {
		if (command.equalsIgnoreCase("Trade") && config().RIGHT_CLICK_TRADE) {
			player.setAccessingShop(shop);
			ActionSender.showShop(player, shop);
		} else {
			RuneMysteries.auburyDialog(player, n);
		}
	}

	@Override
	public boolean blockOpNpc(Player player, Npc n, String command) {
		return ( n.getID() == 54 &&
			player.getConfig().WANT_RUNECRAFTING &&
			player.getQuestStage(Quests.RUNE_MYSTERIES) == Quests.QUEST_STAGE_COMPLETED &&
			command.equalsIgnoreCase("teleport") ||
			(n.getID() == 54 && command.equalsIgnoreCase("Trade")));
	}
}
