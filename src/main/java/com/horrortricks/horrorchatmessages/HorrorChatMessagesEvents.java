package com.horrortricks.horrorchatmessages;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;
import org.slf4j.Logger;

@EventBusSubscriber(modid = "horrorchatmessages")
public final class HorrorChatMessagesEvents {
   private static final int DEBUG_COMMAND_PERMISSION_LEVEL = 0;
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final boolean HAUNTEDCORE_LOADED = ModList.get().isLoaded("hauntedcore");
   private static final Map<UUID, Long> NEXT_PLAYER_GAME_TIME = new HashMap<>();
   private static Long nextBroadcastGameTime;
   private static boolean presenceActiveLastTick = false;
   private static boolean whisperSentThisWindow = false;
   private static long whisperEligibleGameTime = -1L;
   private static long whisperWindowStartGameTime = -1L;
   private static int whisperRolledDelayTicks = -1;

   private HorrorChatMessagesEvents() {
   }

   @SubscribeEvent
   public static void onLevelTick(Post event) {
      if (event.getLevel() instanceof ServerLevel level) {
         long var6 = level.getGameTime();

         for (ServerPlayer player : level.players()) {
            HorrorChatMessagesSituations.trackPlayer(player, var6);
         }

         if ((Boolean)HorrorChatMessagesConfig.ENABLED.get()) {
            if (HAUNTEDCORE_LOADED && level.dimension() == Level.OVERWORLD) {
               tickWhisper(level);
            }

            if ((Boolean)HorrorChatMessagesConfig.BROADCAST_TO_ALL.get()) {
               if (level.dimension() == Level.OVERWORLD) {
                  processBroadcastTimer(level, var6);
               }
            } else {
               for (ServerPlayer player : level.players()) {
                  processPlayerTimer(level, player, var6);
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onPlayerLoggedIn(PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         HorrorChatMessagesSituations.onPlayerLoggedIn(player, player.serverLevel().getGameTime());
      }
   }

   @SubscribeEvent
   public static void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
      NEXT_PLAYER_GAME_TIME.remove(event.getEntity().getUUID());
      if (event.getEntity() instanceof ServerPlayer player) {
         HorrorChatMessagesSituations.onPlayerLoggedOut(player);
      }
   }

   @SubscribeEvent
   public static void onRegisterCommands(RegisterCommandsEvent event) {
      register(event.getDispatcher());
   }

   private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("horrorchatmessages").requires(source -> source.hasPermission(0)))
            .then(
               ((LiteralArgumentBuilder)Commands.literal("trigger").executes(context -> triggerNow((CommandSourceStack)context.getSource())))
                  .then(Commands.literal("whisper").executes(context -> triggerWhisperNow((CommandSourceStack)context.getSource())))
            )
      );
   }

   private static int triggerWhisperNow(CommandSourceStack source) {
      if (!HAUNTEDCORE_LOADED) {
         LOGGER.info("[horrorchatmessages debug] hauntedcore is not loaded in this process - the whisper feature is inactive.");
         return 0;
      }

      if (!HorrorChatMessagesPresenceBridge.isPresent()) {
         LOGGER.info("[horrorchatmessages debug] No hauntedcore presence window is active - trigger one first (e.g. /phantomjoin trigger).");
         return 0;
      }

      MinecraftServer server = source.getServer();

      ServerPlayer executor;
      try {
         executor = source.getPlayerOrException();
      } catch (CommandSyntaxException e) {
         executor = null;
      }

      List<ServerPlayer> players = server.getPlayerList().getPlayers();
      ServerPlayer target = executor != null ? executor : (players.isEmpty() ? null : players.get(0));
      if (target == null) {
         LOGGER.info("[horrorchatmessages debug] No online player to whisper to.");
         return 0;
      } else {
         String fakeName = HorrorChatMessagesPresenceBridge.getFakeName();
         if (fakeName == null) {
            LOGGER.info("[horrorchatmessages debug] Presence is active but no fake name is available yet - try again.");
            return 0;
         } else {
            sendWhisperMessage(target, fakeName, server.overworld().getRandom());
            whisperSentThisWindow = true;
            HorrorChatMessagesPresenceBridge.reportEvent();
            LOGGER.info(
               "[horrorchatmessages debug] Sent {} a whisper as {} (bypassing the once-per-window/quiet-period/delay gates).",
               target.getGameProfile().getName(),
               fakeName
            );
            return 1;
         }
      }
   }

   private static int triggerNow(CommandSourceStack source) {
      MinecraftServer server = source.getServer();
      RandomSource random = server.overworld().getRandom();
      if ((Boolean)HorrorChatMessagesConfig.BROADCAST_TO_ALL.get()) {
         sendBroadcastMessage(server, random);
         scheduleNextBroadcast(server.overworld().getGameTime(), random);
         LOGGER.info("[horrorchatmessages debug] Sent a broadcast chat message.");
         return 1;
      }

      ServerPlayer executor;
      try {
         executor = source.getPlayerOrException();
      } catch (CommandSyntaxException e) {
         executor = null;
      }

      if (executor != null) {
         sendPlayerMessage(executor, random);
         scheduleNextPlayer(executor.getUUID(), executor.serverLevel().getGameTime(), random);
         LOGGER.info("[horrorchatmessages debug] Sent {} a chat message.", executor.getGameProfile().getName());
         return 1;
      }

      List<ServerPlayer> players = server.getPlayerList().getPlayers();

      for (ServerPlayer player : players) {
         sendPlayerMessage(player, random);
         scheduleNextPlayer(player.getUUID(), player.serverLevel().getGameTime(), random);
      }

      LOGGER.info(
         "[horrorchatmessages debug] Per-player mode with no command-executing player - sent a chat message to all {} online player(s) instead.",
         players.size()
      );
      return players.size();
   }

   private static void processBroadcastTimer(ServerLevel level, long gameTime) {
      if (nextBroadcastGameTime == null) {
         scheduleNextBroadcast(gameTime, level.getRandom());
      } else if (gameTime >= nextBroadcastGameTime) {
         sendBroadcastMessage(level.getServer(), level.getRandom());
         scheduleNextBroadcast(gameTime, level.getRandom());
      }
   }

   private static void scheduleNextBroadcast(long gameTime, RandomSource random) {
      int min = (Integer)HorrorChatMessagesConfig.MIN_INTERVAL_TICKS.get();
      int max = Math.max(min, (Integer)HorrorChatMessagesConfig.MAX_INTERVAL_TICKS.get());
      int delay = min + random.nextInt(max - min + 1);
      nextBroadcastGameTime = gameTime + delay;
   }

   private static void processPlayerTimer(ServerLevel level, ServerPlayer player, long gameTime) {
      UUID id = player.getUUID();
      Long next = NEXT_PLAYER_GAME_TIME.get(id);
      if (next == null) {
         scheduleNextPlayer(id, gameTime, player.getRandom());
      } else if (gameTime >= next) {
         sendPlayerMessage(player, player.getRandom());
         scheduleNextPlayer(id, gameTime, player.getRandom());
      }
   }

   private static void scheduleNextPlayer(UUID id, long gameTime, RandomSource random) {
      int min = (Integer)HorrorChatMessagesConfig.MIN_INTERVAL_TICKS.get();
      int max = Math.max(min, (Integer)HorrorChatMessagesConfig.MAX_INTERVAL_TICKS.get());
      int delay = min + random.nextInt(max - min + 1);
      NEXT_PLAYER_GAME_TIME.put(id, gameTime + delay);
   }

   private static void sendBroadcastMessage(MinecraftServer server, RandomSource random) {
      List<ServerPlayer> players = server.getPlayerList().getPlayers();
      EnumSet<HorrorChatMessagesCategory> matched = EnumSet.noneOf(HorrorChatMessagesCategory.class);
      String name = null;
      if (!players.isEmpty()) {
         ServerPlayer referencePlayer = players.get(random.nextInt(players.size()));
         matched = HorrorChatMessagesSituations.matchingCategories(referencePlayer.serverLevel(), referencePlayer, referencePlayer.serverLevel().getGameTime());
         if ((Boolean)HorrorChatMessagesConfig.USE_PLAYER_NAME.get()) {
            name = referencePlayer.getGameProfile().getName();
         }
      }

      String line = HorrorChatMessagesLines.selectLine(random, matched);
      Component message = HorrorChatMessagesLines.resolve(line, name);
      server.getPlayerList().broadcastSystemMessage(message, false);
   }

   private static void tickWhisper(ServerLevel level) {
      boolean presentNow = HorrorChatMessagesPresenceBridge.isPresent();
      if (presentNow && !presenceActiveLastTick) {
         whisperSentThisWindow = false;
         whisperWindowStartGameTime = level.getGameTime();
         whisperRolledDelayTicks = randomWhisperDelayTicks(level.getRandom());
         whisperEligibleGameTime = whisperWindowStartGameTime + whisperRolledDelayTicks;
         LOGGER.debug(
            "[horrorchatmessages] presence window began at gameTime {} - whisper rolled a {}-tick delay (eligible at gameTime {}).",
            new Object[]{whisperWindowStartGameTime, whisperRolledDelayTicks, whisperEligibleGameTime}
         );
      }

      presenceActiveLastTick = presentNow;
      if (presentNow && !whisperSentThisWindow) {
         if (level.getGameTime() >= whisperEligibleGameTime) {
            if (HorrorChatMessagesPresenceBridge.isQuietPeriodOver()) {
               List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers();
               if (!players.isEmpty()) {
                  String fakeName = HorrorChatMessagesPresenceBridge.getFakeName();
                  if (fakeName != null) {
                     RandomSource random = level.getRandom();
                     ServerPlayer target = players.get(random.nextInt(players.size()));
                     long elapsedTicks = level.getGameTime() - whisperWindowStartGameTime;
                     LOGGER.debug(
                        "[horrorchatmessages] whisper sending now: {} ticks elapsed since window start (rolled delay was {} ticks).",
                        elapsedTicks,
                        whisperRolledDelayTicks
                     );
                     sendWhisperMessage(target, fakeName, random);
                     whisperSentThisWindow = true;
                     HorrorChatMessagesPresenceBridge.reportEvent();
                  }
               }
            }
         }
      }
   }

   private static int randomWhisperDelayTicks(RandomSource random) {
      int min = (Integer)HorrorChatMessagesConfig.WHISPER_MIN_DELAY_TICKS.get();
      int max = Math.max(min, (Integer)HorrorChatMessagesConfig.WHISPER_MAX_DELAY_TICKS.get());
      return min + random.nextInt(max - min + 1);
   }

   private static void sendWhisperMessage(ServerPlayer target, String fakeName, RandomSource random) {
      String rawLine = HorrorChatMessagesLines.selectWhisperLine(random);
      String name = HorrorChatMessagesConfig.USE_PLAYER_NAME.get() ? target.getGameProfile().getName() : null;
      Component line = HorrorChatMessagesLines.resolve(rawLine, name);
      Component message = Component.translatable("commands.message.display.incoming", new Object[]{Component.literal(fakeName), line})
         .withStyle(new ChatFormatting[]{ChatFormatting.GRAY, ChatFormatting.ITALIC});
      target.sendSystemMessage(message);
   }

   private static void sendPlayerMessage(ServerPlayer player, RandomSource random) {
      EnumSet<HorrorChatMessagesCategory> matched = HorrorChatMessagesSituations.matchingCategories(
         player.serverLevel(), player, player.serverLevel().getGameTime()
      );
      String line = HorrorChatMessagesLines.selectLine(random, matched);
      String name = HorrorChatMessagesConfig.USE_PLAYER_NAME.get() ? player.getGameProfile().getName() : null;
      Component message = HorrorChatMessagesLines.resolve(line, name);
      player.sendSystemMessage(message);
   }
}
