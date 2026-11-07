package net.minecraft.client.renderer.texture.atlas;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@FunctionalInterface
@Environment(EnvType.CLIENT)
public interface SpriteResourceLoader {
	Logger LOGGER = LogUtils.getLogger();

	static SpriteResourceLoader create(Set<MetadataSectionType<?>> set) {
		return (identifier, resource) -> {
			Optional<AnimationMetadataSection> optional;
			Optional<TextureMetadataSection> optional2;
			List<MetadataSectionType.WithValue<?>> list;
			try {
				ResourceMetadata resourceMetadata = resource.metadata();
				optional = resourceMetadata.getSection(AnimationMetadataSection.TYPE);
				optional2 = resourceMetadata.getSection(TextureMetadataSection.TYPE);
				list = resourceMetadata.getTypedSections(set);
			} catch (Exception var11) {
				LOGGER.error("Unable to parse metadata from {}", identifier, var11);
				return null;
			}

			NativeImage nativeImage;
			try {
				InputStream inputStream = resource.open();

				try {
					nativeImage = NativeImage.read(inputStream);
				} catch (Throwable var12) {
					if (inputStream != null) {
						try {
							inputStream.close();
						} catch (Throwable var10) {
							var12.addSuppressed(var10);
						}
					}

					throw var12;
				}

				if (inputStream != null) {
					inputStream.close();
				}
			} catch (IOException var13) {
				LOGGER.error("Using missing texture, unable to load {}", identifier, var13);
				return null;
			}

			FrameSize frameSize;
			if (optional.isPresent()) {
				frameSize = ((AnimationMetadataSection)optional.get()).calculateFrameSize(nativeImage.getWidth(), nativeImage.getHeight());
				if (!Mth.isMultipleOf(nativeImage.getWidth(), frameSize.width()) || !Mth.isMultipleOf(nativeImage.getHeight(), frameSize.height())) {
					LOGGER.error(
						"Image {} size {},{} is not multiple of frame size {},{}",
						identifier,
						nativeImage.getWidth(),
						nativeImage.getHeight(),
						frameSize.width(),
						frameSize.height()
					);
					nativeImage.close();
					return null;
				}
			} else {
				frameSize = new FrameSize(nativeImage.getWidth(), nativeImage.getHeight());
			}

			return new SpriteContents(identifier, frameSize, nativeImage, optional, list, optional2);
		};
	}

	@Nullable
	SpriteContents loadSprite(Identifier identifier, Resource resource);
}
