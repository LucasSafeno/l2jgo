package net.sf.l2j.gameserver.handler.itemhandlers;

import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.enums.items.ShotType;
import net.sf.l2j.gameserver.handler.IItemHandler;
import net.sf.l2j.gameserver.model.actor.Playable;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.holder.IntIntHolder;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.model.item.kind.Weapon;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.ExAutoSoulShot;
import net.sf.l2j.gameserver.network.serverpackets.MagicSkillUse;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;

public class SoulShots implements IItemHandler
{
	private static final int MANA_POT_INTERVAL_MS = Config.MP_POT_CD, HEALING_POT_INTERVAL_MS = Config.HP_POT_CD, CP_POT_INTERVAL_MS = Config.CP_POT_CD;
	private static final double HEALING_POT_ACTIVATION_HP = Config.HP_REQUIRED;
	private static final double MANA_POT_ACTIVATION_MP = Config.MP_REQUIRED;
	private static final double CP_POT_ACTIVATION_CP = Config.CP_REQUIRED;

	@Override
	public void useItem(Playable playable, ItemInstance item, boolean forceUse)
	{
		if (!(playable instanceof Player player))
			return;
		
		final ItemInstance weaponInst = player.getActiveWeaponInstance();
		final Weapon weaponItem = player.getActiveWeaponItem();

		final int itemId = item.getItemId();
		if (itemId == 728 || itemId == 1540 || itemId == 5592)
		{
			handlePotionUsage(player, itemId);
			return;
		}


		// Check if soulshot can be used
		if (weaponInst == null || weaponItem.getSoulShotCount() == 0)
		{
			if (!player.getAutoSoulShot().contains(item.getItemId()))
				player.sendPacket(SystemMessageId.CANNOT_USE_SOULSHOTS);
			
			return;
		}
		
		if (weaponItem.getCrystalType() != item.getItem().getCrystalType())
		{
			if (!player.getAutoSoulShot().contains(item.getItemId()))
				player.sendPacket(SystemMessageId.SOULSHOTS_GRADE_MISMATCH);
			
			return;
		}
		
		// Check if Soulshot are already active.
		if (player.isChargedShot(ShotType.SOULSHOT))
			return;
		
		// Consume Soulshots if player has enough of them.
		int ssCount = weaponItem.getSoulShotCount();
		if (weaponItem.getReducedSoulShot() > 0 && Rnd.get(100) < weaponItem.getReducedSoulShotChance())
			ssCount = weaponItem.getReducedSoulShot();
		
		if (!player.destroyItem(item.getObjectId(), ssCount, false))
		{
			if (!player.disableAutoShot(item.getItemId()))
				player.sendPacket(SystemMessageId.NOT_ENOUGH_SOULSHOTS);
			
			return;
		}
		
		final IntIntHolder[] skills = item.getItem().getSkills();
		
		weaponInst.setChargedShot(ShotType.SOULSHOT, true);
		player.sendPacket(SystemMessageId.ENABLED_SOULSHOT);
		player.broadcastPacketInRadius(new MagicSkillUse(player, player, skills[0].getId(), 1, 0, 0), 600);
	}

	private void handlePotionUsage(Player player, int itemId)
	{
		if (player.isAutoPot(itemId))
		{
			deactivatePotion(player, itemId);
		}
		else
		{
			activatePotion(player, itemId);
		}
	}

	private static void deactivatePotion(Player player, int itemId)
	{
		player.sendPacket(new ExAutoSoulShot(itemId, 0));
		player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_S2).addString("Potion deactivated."));
		player.setAutoPot(itemId, null, false);
	}

	private void activatePotion(Player player, int itemId)
	{
		if (player.getInventory().getItemByItemId(itemId) != null &&
				player.getInventory().getItemByItemId(itemId).getCount() > 1)
		{
			player.sendPacket(new ExAutoSoulShot(itemId, 1));
			player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_S2).addString("Potion activated."));
			int interval = getPotionInterval(itemId);
			player.setAutoPot(itemId, ThreadPool.scheduleAtFixedRate(new AutoPot(itemId, player), 1000, interval), true);
		}
		else
		{
			useSinglePotion(player, itemId);
		}
	}

	private static void useSinglePotion(Player player, int itemId)
	{
		MagicSkillUse msu = new MagicSkillUse(player, player, getPotionSkillId(itemId), 1, 0, 100);
		player.broadcastPacket(msu);
		new ItemSkills().useItem(player, player.getInventory().getItemByItemId(itemId), true);
	}

	private static int getPotionInterval(int itemId)
	{
		return switch (itemId) {
			case 1540 -> HEALING_POT_INTERVAL_MS;
			case 728 -> MANA_POT_INTERVAL_MS;
			case 5592 -> CP_POT_INTERVAL_MS;
			default -> 1000;
		};
	}

	private static int getPotionSkillId(int itemId)
	{
		return switch (itemId) {
			case 1540 -> 2038;
			case 728 -> 2279;
			case 5592 -> 2166;
			default -> 0;
		};
	}
	private class AutoPot implements Runnable
	{
		private final int id;
		private final Player activeChar;

		public AutoPot(int id, Player activeChar)
		{
			this.id = id;
			this.activeChar = activeChar;
		}

		@Override
		public void run()
		{
			if (activeChar.isDead() || activeChar.getInventory().getItemByItemId(id) == null)
			{
				deactivatePotion(activeChar, id);
				return;
			}

			double currentStatus = getCurrentStatus(id);
			double maxStatus = getMaxStatus(id);
			double activationThreshold = getActivationThreshold(id);

			if (currentStatus < maxStatus * activationThreshold)
			{
				MagicSkillUse msu = new MagicSkillUse(activeChar, activeChar, getPotionSkillId(id), 1, 0, 100);
				activeChar.broadcastPacket(msu);
				new ItemSkills().useItem(activeChar, activeChar.getInventory().getItemByItemId(id), true);
			}
		}

		private double getCurrentStatus(int itemId)
		{
			return switch (itemId) {
				case 1540 -> activeChar.getStatus().getHp();
				case 728 -> activeChar.getStatus().getMp();
				case 5592 -> activeChar.getStatus().getCp();
				default -> 0;
			};
		}

		private double getMaxStatus(int itemId)
		{
			return switch (itemId) {
				case 1540 -> activeChar.getStatus().getMaxHp();
				case 728 -> activeChar.getStatus().getMaxMp();
				case 5592 -> activeChar.getStatus().getMaxCp();
				default -> 1;
			};
		}
		private double getActivationThreshold(int itemId)
		{
			return switch (itemId) {
				case 1540 -> HEALING_POT_ACTIVATION_HP;
				case 728 -> MANA_POT_ACTIVATION_MP;
				case 5592 -> CP_POT_ACTIVATION_CP;
				default -> 1;
			};
		}
	}
}