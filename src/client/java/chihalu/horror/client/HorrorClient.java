package chihalu.horror.client;

import net.fabricmc.api.ClientModInitializer;
import chihalu.horror.client.surface.SurfaceRendering;

public class HorrorClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SurfaceRendering.initialize();
	}
}
