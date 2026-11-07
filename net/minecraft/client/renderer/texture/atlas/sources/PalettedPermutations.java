package net.minecraft.client.renderer.texture.atlas.sources;

import com.google.common.base.Suppliers;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public record PalettedPermutations(List<Identifier> textures, Identifier paletteKey, Map<String, Identifier> permutations, String separator)
	implements SpriteSource {
	static final Logger LOGGER = LogUtils.getLogger();
	public static final String DEFAULT_SEPARATOR = "_";
	public static final MapCodec<PalettedPermutations> MAP_CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				Codec.list(Identifier.CODEC).fieldOf("textures").forGetter(PalettedPermutations::textures),
				Identifier.CODEC.fieldOf("palette_key").forGetter(PalettedPermutations::paletteKey),
				Codec.unboundedMap(Codec.STRING, Identifier.CODEC).fieldOf("permutations").forGetter(PalettedPermutations::permutations),
				Codec.STRING.optionalFieldOf("separator", "_").forGetter(PalettedPermutations::separator)
			)
			.apply(instance, PalettedPermutations::new)
	);

	public PalettedPermutations(List<Identifier> list, Identifier identifier, Map<String, Identifier> map) {
		this(list, identifier, map, "_");
	}

	@Override
	public void run(ResourceManager resourceManager, SpriteSource.Output output) {
		Supplier<int[]> supplier = Suppliers.memoize(() -> loadPaletteEntryFromImage(resourceManager, this.paletteKey));
		Map<String, Supplier<IntUnaryOperator>> map = new HashMap();
		this.permutations
			.forEach(
				(string, identifierx) -> map.put(
					string, Suppliers.memoize(() -> createPaletteMapping((int[])supplier.get(), loadPaletteEntryFromImage(resourceManager, identifierx)))
				)
			);

		for (Identifier identifier : this.textures) {
			Identifier identifier2 = TEXTURE_ID_CONVERTER.idToFile(identifier);
			Optional<Resource> optional = resourceManager.getResource(identifier2);
			if (optional.isEmpty()) {
				LOGGER.warn("Unable to find texture {}", identifier2);
			} else {
				LazyLoadedImage lazyLoadedImage = new LazyLoadedImage(identifier2, (Resource)optional.get(), map.size());

				for (Entry<String, Supplier<IntUnaryOperator>> entry : map.entrySet()) {
					Identifier identifier3 = identifier.withSuffix(this.separator + (String)entry.getKey());
					output.add(identifier3, new PalettedPermutations.PalettedSpriteSupplier(lazyLoadedImage, (Supplier<IntUnaryOperator>)entry.getValue(), identifier3));
				}
			}
		}
	}

	private static IntUnaryOperator createPaletteMapping(int[] is, int[] js) {
		if (js.length != is.length) {
			LOGGER.warn("Palette mapping has different sizes: {} and {}", is.length, js.length);
			throw new IllegalArgumentException();
		} else {
			Int2IntMap int2IntMap = new Int2IntOpenHashMap(js.length);

			for (int i = 0; i < is.length; i++) {
				int j = is[i];
				if (ARGB.alpha(j) != 0) {
					int2IntMap.put(ARGB.transparent(j), js[i]);
				}
			}

			return ix -> {
				int jx = ARGB.alpha(ix);
				if (jx == 0) {
					return ix;
				} else {
					int k = ARGB.transparent(ix);
					int l = int2IntMap.getOrDefault(k, ARGB.opaque(k));
					int m = ARGB.alpha(l);
					return ARGB.color(jx * m / 255, l);
				}
			};
		}
	}

	private static int[] loadPaletteEntryFromImage(ResourceManager resourceManager, Identifier identifier) {
		Optional<Resource> optional = resourceManager.getResource(TEXTURE_ID_CONVERTER.idToFile(identifier));
		if (optional.isEmpty()) {
			LOGGER.error("Failed to load palette image {}", identifier);
			throw new IllegalArgumentException();
		} else {
			try {
				InputStream inputStream = ((Resource)optional.get()).open();

				int[] var5;
				try (NativeImage nativeImage = NativeImage.read(inputStream)) {
					var5 = nativeImage.getPixels();
				} catch (Throwable var10) {
					if (inputStream != null) {
						try {
							inputStream.close();
						} catch (Throwable var7) {
							var10.addSuppressed(var7);
						}
					}

					throw var10;
				}

				if (inputStream != null) {
					inputStream.close();
				}

				return var5;
			} catch (Exception var11) {
				LOGGER.error("Couldn't load texture {}", identifier, var11);
				throw new IllegalArgumentException();
			}
		}
	}

	@Override
	public MapCodec<PalettedPermutations> codec() {
		return MAP_CODEC;
	}

	@Environment(EnvType.CLIENT)
	record PalettedSpriteSupplier(LazyLoadedImage baseImage, Supplier<IntUnaryOperator> palette, Identifier permutationLocation)
		implements SpriteSource.DiscardableLoader {
		@Nullable
		@Override
		public SpriteContents get(SpriteResourceLoader spriteResourceLoader) {
			Object var3;
			try {
				NativeImage nativeImage = this.baseImage.get().mappedCopy((IntUnaryOperator)this.palette.get());
				return new SpriteContents(this.permutationLocation, new FrameSize(nativeImage.getWidth(), nativeImage.getHeight()), nativeImage);
			} catch (IllegalArgumentException | IOException var7) {
				PalettedPermutations.LOGGER.error("unable to apply palette to {}", this.permutationLocation, var7);
				var3 = null;
			} finally {
				this.baseImage.release();
			}

			return (SpriteContents)var3;
		}

		@Override
		public void discard() {
			this.baseImage.release();
		}
	}
}
