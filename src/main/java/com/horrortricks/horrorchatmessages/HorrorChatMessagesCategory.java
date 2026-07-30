package com.horrortricks.horrorchatmessages;

enum HorrorChatMessagesCategory {
   GENERAL("general", 0),
   NIGHT("night", 2),
   LONG_SESSION("long_session", 3),
   STILL("still", 3),
   UNDERGROUND("underground", 4),
   ALONE("alone", 5),
   LOW_HEALTH("low_health", 6),
   WHISPER("whisper", 0);

   private final String key;
   private final int weight;

   HorrorChatMessagesCategory(String key, int weight) {
      this.key = key;
      this.weight = weight;
   }

   String key() {
      return this.key;
   }

   int weight() {
      return this.weight;
   }

   static HorrorChatMessagesCategory byKey(String key) {
      for (HorrorChatMessagesCategory category : values()) {
         if (category.key.equals(key)) {
            return category;
         }
      }

      return null;
   }
}
