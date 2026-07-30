package com.horrortricks.horrorchatmessages;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;
import org.slf4j.Logger;

@Mod("horrorchatmessages")
public class HorrorChatMessagesMod {
   public static final String MOD_ID = "horrorchatmessages";
   private static final Logger LOGGER = LogUtils.getLogger();

   public HorrorChatMessagesMod(IEventBus modEventBus, ModContainer modContainer) {
      modContainer.registerConfig(Type.COMMON, HorrorChatMessagesConfig.SPEC);
      LOGGER.info("Horror Chat Messages initialized.");
   }
}
