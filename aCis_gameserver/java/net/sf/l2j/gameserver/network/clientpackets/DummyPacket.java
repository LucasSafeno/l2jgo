package net.sf.l2j.gameserver.network.clientpackets;

public final class DummyPacket extends L2GameClientPacket
{
	@Override
	protected void readImpl()
	{
		// Do nothing.
	}
	
	@Override
	public void runImpl()
	{
		// Do nothing.
	}
}