package com.horrortricks.horrorchatmessages;

import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import org.slf4j.Logger;

final class HorrorChatMessagesLines {
   private static final String RESOURCE_PATH = "/horrorchatmessages/chat_lines.txt";
   private static final String PLACEHOLDER = "{player}";
   private static final String NEUTRAL_FALLBACK = "someone";
   private static final String WHISPER_FALLBACK_LINE = "Did I wake you, {player}?";
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Map<HorrorChatMessagesCategory, List<String>> LINES_BY_CATEGORY = load();

   private HorrorChatMessagesLines() {
   }

   static String selectLine(RandomSource random, EnumSet<HorrorChatMessagesCategory> matchedCategories) {
      HorrorChatMessagesCategory category = selectCategory(random, matchedCategories);
      List<String> lines = LINES_BY_CATEGORY.get(category);
      return lines.get(random.nextInt(lines.size()));
   }

   static String selectWhisperLine(RandomSource random) {
      List<String> lines = LINES_BY_CATEGORY.get(HorrorChatMessagesCategory.WHISPER);
      return lines != null && !lines.isEmpty() ? lines.get(random.nextInt(lines.size())) : "Did I wake you, {player}?";
   }

   private static HorrorChatMessagesCategory selectCategory(RandomSource random, EnumSet<HorrorChatMessagesCategory> matchedCategories) {
      List<HorrorChatMessagesCategory> candidates = new ArrayList<>();
      int totalWeight = 0;

      for (HorrorChatMessagesCategory category : matchedCategories) {
         List<String> lines = LINES_BY_CATEGORY.get(category);
         if (lines != null && !lines.isEmpty()) {
            candidates.add(category);
            totalWeight += category.weight();
         }
      }

      if (!candidates.isEmpty() && totalWeight > 0) {
         int roll = random.nextInt(totalWeight);
         int accumulated = 0;

         for (HorrorChatMessagesCategory category : candidates) {
            accumulated += category.weight();
            if (roll < accumulated) {
               return category;
            }
         }

         return candidates.get(candidates.size() - 1);
      } else {
         return HorrorChatMessagesCategory.GENERAL;
      }
   }

   static Component resolve(String rawLine, String playerName) {
      if (!rawLine.contains("{player}")) {
         return Component.literal(rawLine);
      }

      String name = playerName != null ? playerName : "someone";
      return Component.literal(rawLine.replace("{player}", name));
   }

   private static Map<HorrorChatMessagesCategory, List<String>> load() {
      Map<HorrorChatMessagesCategory, List<String>> lines = new EnumMap<>(HorrorChatMessagesCategory.class);

      try (InputStream stream = HorrorChatMessagesLines.class.getResourceAsStream("/horrorchatmessages/chat_lines.txt")) {
         if (stream == null) {
            LOGGER.error("Missing bundled resource {}, falling back to a single default line.", "/horrorchatmessages/chat_lines.txt");
            return fallback();
         }

         HorrorChatMessagesCategory current = null;

         String line;
         try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            while ((line = reader.readLine()) != null) {
               String trimmed = line.trim();
               if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                  if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                     String key = trimmed.substring(1, trimmed.length() - 1).trim();
                     current = HorrorChatMessagesCategory.byKey(key);
                     if (current == null) {
                        LOGGER.error("Unrecognized {} category header '[{}]', its lines will be ignored.", "/horrorchatmessages/chat_lines.txt", key);
                     }
                  } else if (current == null) {
                     LOGGER.error("Ignoring {} line found before any category header: {}", "/horrorchatmessages/chat_lines.txt", trimmed);
                  } else {
                     lines.computeIfAbsent(current, unused -> new ArrayList<>()).add(trimmed);
                  }
               }
            }
         }
      } catch (IOException e) {
         LOGGER.error("Failed to read {}, falling back to a single default line.", "/horrorchatmessages/chat_lines.txt", e);
         return fallback();
      }

      if (lines.getOrDefault(HorrorChatMessagesCategory.GENERAL, List.of()).isEmpty()) {
         LOGGER.error("{} has no usable [general] lines - it is the fallback category, so this should be fixed.", "/horrorchatmessages/chat_lines.txt");
         return fallback();
      }

      for (Entry<HorrorChatMessagesCategory, List<String>> entry : lines.entrySet()) {
         entry.setValue(Collections.unmodifiableList(entry.getValue()));
      }

      return Collections.unmodifiableMap(lines);
   }

   private static Map<HorrorChatMessagesCategory, List<String>> fallback() {
      Map<HorrorChatMessagesCategory, List<String>> fallback = new EnumMap<>(HorrorChatMessagesCategory.class);
      fallback.put(HorrorChatMessagesCategory.GENERAL, List.of("It's still watching you."));
      return Collections.unmodifiableMap(fallback);
   }
}
