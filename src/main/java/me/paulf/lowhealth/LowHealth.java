package me.paulf.lowhealth;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.google.common.collect.ImmutableList;
import com.mojang.authlib.HttpAuthenticationService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SimpleSound;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Mod("lowhealth")
public final class LowHealth {
	public LowHealth() {
		FMLJavaModLoadingContext.get().getModEventBus().<FMLClientSetupEvent>addListener(event -> {
			final FileConfig config = CommentedFileConfig.builder(FMLPaths.CONFIGDIR.get().resolve("lowhealth-client.toml"))
				.defaultData(HttpAuthenticationService.constantURL("modjar://lowhealth/assets/lowhealth/config/default.toml"))
				.sync()
				.build();
			config.load();
			config.close();
			final ImmutableList<SoundConfig> sounds = config.<List<UnmodifiableConfig>>getOrElse("sounds", ArrayList::new).stream()
				.map(SoundConfig::new)
				.filter(s -> s.rate > 0 && s.volume > 0.0F && s.pitch > 0.0F)
				.sorted(Comparator.comparing(s -> s.health))
				.collect(ImmutableList.toImmutableList());
			MinecraftForge.EVENT_BUS.register(new Handler(sounds));
		});
	}

	private final class Handler {
		final ImmutableList<SoundConfig> sounds;
		SoundConfig absent = new SoundConfig();
		SoundConfig sound = this.absent;
		int clock;

		Handler(final ImmutableList<SoundConfig> sounds) {
			this.sounds = sounds;
		}

		@SubscribeEvent
		public void onTick(final TickEvent.ClientTickEvent e) {
			final Minecraft mc = Minecraft.getInstance();
			final PlayerEntity player = mc.player;
			if (e.phase == TickEvent.Phase.END && !mc.isGamePaused() && player != null && player.isAlive()) {
				final SoundConfig playing = this.sound;
				this.sound = this.absent;
				for (final SoundConfig sound : this.sounds) {
					if (player.getHealth() <= sound.health) {
						if (sound != playing || this.clock >= sound.rate) {
							mc.getSoundHandler().play(sound.create());
							this.clock = 0;
						} else {
							this.clock++;
						}
						this.sound = sound;
						break;
					}
				}
			}
		}
	}

	private final class SoundConfig {
		ResourceLocation sound = new ResourceLocation("meta:missing_sound");
		float health;
		float volume;
		float pitch;
		int rate;

		SoundConfig() {}

		SoundConfig(final UnmodifiableConfig config) {
			this.sound = config.<String>getOptional("sound")
				.flatMap(s -> Optional.ofNullable(ResourceLocation.tryCreate(s)))
				.orElseThrow(() -> new RuntimeException("missing sound"));
			this.health = config.<Number>getOptional("health")
				.orElseThrow(() -> new RuntimeException("missing health")).floatValue();
			this.volume = config.<Number>getOrElse("volume", 1.0F).floatValue();
			this.pitch = config.<Number>getOrElse("pitch", 1.0F).floatValue();
			this.rate = config.<Number>getOrElse("rate", 10).intValue();

		}

		ISound create() {
			return new SimpleSound(this.sound, SoundCategory.MASTER, this.volume, this.pitch, false, 0, ISound.AttenuationType.NONE, 0.0F, 0.0F, 0.0F, true);
		}
	}
}
