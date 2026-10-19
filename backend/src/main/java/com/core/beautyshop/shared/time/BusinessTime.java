package com.core.beautyshop.shared.time;
import java.time.*;
/** Business calendar dates are Vietnamese dates; persisted event timestamps remain UTC instants. */
public final class BusinessTime {
 public static final ZoneId ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
 private BusinessTime(){}
 public static LocalDate today(){return today(Clock.systemUTC());}
 public static LocalDate today(Clock clock){return LocalDate.now(clock.withZone(ZONE));}
}
