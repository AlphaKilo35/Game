package com.openrsc.server.plugins.npcs.alkharid;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Quests;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;

import static com.openrsc.server.plugins.Functions.*;

public class SilkTrader implements TalkNpcTrigger {

	@Override
	public boolean blockTalkNpc(Player player, Npc n) {
		return n.getID() == NpcId.SILK_TRADER.id();
	}

	@Override
	public void onTalkNpc(Player player, Npc n) {
		final String[] options;
		npcsay(player, n, "Do you want to buy any fine silks?");
		if (player.getQuestStage(Quests.FAMILY_CREST) <= 2 || player.getQuestStage(Quests.FAMILY_CREST) >= 5) {
			options = new String[]{
				"How much are they?",
				"No. Silk doesn't suit me"
			};
		} else {
			options = new String[]{
				"How much are they?",
				"No. Silk doesn't suit me",
				"I'm in search of a man named adam fitzharmon"
			};
		}
		int option1 = multi(player, n, options);
		if (option1 == 0) {
			npcsay(player, n, "3 Coins");

			int option2 = multi(player, n, "No. That's too much for me",
				"OK, that sounds good");
			if (option2 == 0) {
				npcsay(player, n, "Two coins and that's as low as I'll go",
					"I'm not selling it for any less",
					"You'll probably go and sell it in Varrock for a profit anyway"
				);

				int option3 = multi(player, n, "Two coins sounds good",
					"No, really. I don't want it"
				);
				if (option3 == 0) {
					player.message("You buy some silk for 2 coins");
					if (player.getCarriedItems().remove(new Item(ItemId.COINS.id(), 2)) > -1) {
						give(player, ItemId.SILK.id(), 1);
					} else {
						say(player, n, "Oh dear. I don't have enough money");
					}
				} else if (option3 == 1) {
					npcsay(player, n, "OK, but that's the best price you're going to get");
				}

			} else if (option2 == 1) {
				if (player.getCarriedItems().remove(new Item(ItemId.COINS.id(), 3)) > -1) {
					give(player, ItemId.SILK.id(), 1);
					player.message("You buy some silk for 3 coins");
				} else {
					say(player, n, "Oh dear. I don't have enough money");
				}
			}
		} else if (option1 == 2) {
			npcsay(player, n, "I haven't seen him",
					"I'm sure if he's been to Al Kharid recently",
					"Someone around here will have seen him though");
		}
	}
}
