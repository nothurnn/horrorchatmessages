package com.horrortricks.horrorchatmessages;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

final class HorrorChatMessagesSituations {
   private static final int SAMPLE_INTERVAL_TICKS = 20;
   private static final double STILL_MOVE_EPSILON = 0.3;
   private static final int LOW_LIGHT_THRESHOLD = 8;
   private static final long LONG_SESSION_TICKS = 72000L;
   private static final long STILL_TICKS_THRESHOLD = 2400L;
   private static final long RECENT_DAMAGE_WINDOW_TICKS = 600L;
   private static final float LOW_HEALTH_FRACTION = 0.3F;
   private static final Map<UUID, HorrorChatMessagesSituations.PlayerState> STATES = new HashMap<>();

   private HorrorChatMessagesSituations() {
   }

   static void onPlayerLoggedIn(ServerPlayer player, long gameTime) {
      STATES.put(player.getUUID(), new HorrorChatMessagesSituations.PlayerState(gameTime, player.position(), gameTime, player.getHealth(), gameTime));
   }

   static void onPlayerLoggedOut(ServerPlayer player) {
      STATES.remove(player.getUUID());
   }

   static void trackPlayer(ServerPlayer player, long gameTime) {
      HorrorChatMessagesSituations.PlayerState state = STATES.get(player.getUUID());
      if (state == null) {
         onPlayerLoggedIn(player, gameTime);
      } else if (gameTime - state.lastSampleGameTime >= 20L) {
         Vec3 currentPos = player.position();
         if (currentPos.distanceTo(state.lastPosition) > 0.3) {
            state.lastMovedGameTime = gameTime;
         }

         state.lastPosition = currentPos;
         float currentHealth = player.getHealth();
         if (currentHealth < state.lastHealth) {
            state.lastDamageGameTime = gameTime;
         }

         state.lastHealth = currentHealth;
         state.lastSampleGameTime = gameTime;
      }
   }

   static EnumSet<HorrorChatMessagesCategory> matchingCategories(ServerLevel level, ServerPlayer player, long gameTime) {
      EnumSet<HorrorChatMessagesCategory> matched = EnumSet.noneOf(HorrorChatMessagesCategory.class);
      HorrorChatMessagesSituations.PlayerState state = STATES.get(player.getUUID());
      BlockPos pos = player.blockPosition();
      if (!level.canSeeSky(pos) && level.getMaxLocalRawBrightness(pos) <= 8) {
         matched.add(HorrorChatMessagesCategory.UNDERGROUND);
      }

      if (level.isNight()) {
         matched.add(HorrorChatMessagesCategory.NIGHT);
      }

      if (player.getServer().getPlayerList().getPlayerCount() <= 1) {
         matched.add(HorrorChatMessagesCategory.ALONE);
      }

      boolean lowHealthNow = player.getHealth() <= player.getMaxHealth() * 0.3F;
      if (state != null) {
         if (gameTime - state.sessionStartGameTime >= 72000L) {
            matched.add(HorrorChatMessagesCategory.LONG_SESSION);
         }

         if (gameTime - state.lastMovedGameTime >= 2400L) {
            matched.add(HorrorChatMessagesCategory.STILL);
         }

         if (lowHealthNow || gameTime - state.lastDamageGameTime <= 600L) {
            matched.add(HorrorChatMessagesCategory.LOW_HEALTH);
         }
      } else if (lowHealthNow) {
         matched.add(HorrorChatMessagesCategory.LOW_HEALTH);
      }

      return matched;
   }

   private static final class PlayerState {
      private final long sessionStartGameTime;
      private Vec3 lastPosition;
      private long lastMovedGameTime;
      private float lastHealth;
      private long lastDamageGameTime;
      private long lastSampleGameTime;

      private PlayerState(long sessionStartGameTime, Vec3 lastPosition, long lastMovedGameTime, float lastHealth, long lastDamageGameTime) {
         this.sessionStartGameTime = sessionStartGameTime;
         this.lastPosition = lastPosition;
         this.lastMovedGameTime = lastMovedGameTime;
         this.lastHealth = lastHealth;
         this.lastDamageGameTime = lastDamageGameTime;
         this.lastSampleGameTime = sessionStartGameTime;
      }
   }
}
