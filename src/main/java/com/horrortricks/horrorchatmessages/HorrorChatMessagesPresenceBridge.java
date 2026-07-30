package com.horrortricks.horrorchatmessages;

import com.horrortricks.hauntedcore.coordinator.EventCoordinator;
import com.horrortricks.hauntedcore.presence.PresenceState;

final class HorrorChatMessagesPresenceBridge {
   private HorrorChatMessagesPresenceBridge() {
   }

   static boolean isPresent() {
      return PresenceState.isPresent();
   }

   static boolean isQuietPeriodOver() {
      return EventCoordinator.isQuietPeriodOver();
   }

   static String getFakeName() {
      return PresenceState.getFakeName();
   }

   static void reportEvent() {
      EventCoordinator.reportEvent();
   }
}
