package chihalu.horror.client;

import net.fabricmc.api.ClientModInitializer;
import chihalu.horror.client.surface.SurfaceRendering;
import chihalu.horror.client.terrain.MarchingTerrain;

public class HorrorClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SurfaceRendering.initialize();
		MarchingTerrain.initialize();
	}
}
