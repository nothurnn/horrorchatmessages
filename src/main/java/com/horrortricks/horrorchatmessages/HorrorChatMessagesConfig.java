package com.horrortricks.horrorchatmessages;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;

public final class HorrorChatMessagesConfig {
   private static final Builder BUILDER = new Builder();
   public static final BooleanValue ENABLED = BUILDER.comment("Master switch for the horror chat messages effect.").define("enabled", true);
   public static final IntValue MIN_INTERVAL_TICKS = BUILDER.comment(
         new String[]{"Minimum ticks (20 ticks/second) between messages.", "Default of 12000 is 10 minutes."}
      )
      .defineInRange("minIntervalTicks", 12000, 20, Integer.MAX_VALUE);
   public static final IntValue MAX_INTERVAL_TICKS = BUILDER.comment(
         new String[]{"Maximum ticks (20 ticks/second) between messages.", "Default of 24000 is 20 minutes."}
      )
      .defineInRange("maxIntervalTicks", 24000, 20, Integer.MAX_VALUE);
   public static final BooleanValue BROADCAST_TO_ALL = BUILDER.comment(
         new String[]{
            "If true, one shared timer sends a single message visible to every online player.",
            "If false, each player has their own independent timer and only sees their own messages."
         }
      )
      .define("broadcastToAll", true);
   public static final BooleanValue USE_PLAYER_NAME = BUILDER.comment(
         new String[]{
            "If true, a line's optional {player} placeholder is filled with a real player name",
            "(the recipient's own name in per-player mode, or a random online player's name when",
            "broadcasting). If false, {player} is replaced with a neutral word instead."
         }
      )
      .define("usePlayerName", true);
   public static final IntValue WHISPER_MIN_DELAY_TICKS = BUILDER.comment(
         new String[]{
            "Minimum ticks (20 ticks/second) to wait after a hauntedcore presence window begins",
            "before the whisper is allowed to fire, so it doesn't always land in the same instant",
            "as the fake join line. Default of 100 is 5 seconds."
         }
      )
      .defineInRange("whisperMinDelayTicks", 100, 0, Integer.MAX_VALUE);
   public static final IntValue WHISPER_MAX_DELAY_TICKS = BUILDER.comment(
         new String[]{
            "Maximum ticks (20 ticks/second) to wait after a hauntedcore presence window begins",
            "before the whisper is allowed to fire. Default of 600 is 30 seconds.",
            "Still subject to the once-per-window and shared quiet-period gates."
         }
      )
      .defineInRange("whisperMaxDelayTicks", 600, 0, Integer.MAX_VALUE);
   public static final ModConfigSpec SPEC = BUILDER.build();

   private HorrorChatMessagesConfig() {
   }

   static {
      BUILDER.push("general");
      BUILDER.pop();
   }
}
