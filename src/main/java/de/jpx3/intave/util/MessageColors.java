package de.jpx3.intave.util;

import org.bukkit.ChatColor;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MessageColors {
  private static final char COLOR_CHAR = ChatColor.COLOR_CHAR;
  private static final Pattern HEX_PATTERN = Pattern.compile(
    "([&" + COLOR_CHAR + "]#[A-Fa-f0-9]{6})|([&" + COLOR_CHAR + "][xX]([&" + COLOR_CHAR + "][A-Fa-f0-9]){6})"
  );

  private MessageColors() {
  }

  public static String translate(String input) {
    if (input == null || input.isEmpty()) {
      return input;
    }
    return ChatColor.translateAlternateColorCodes('&', translateHexColors(input));
  }

  private static String translateHexColors(String input) {
    Matcher matcher = HEX_PATTERN.matcher(input);
    StringBuffer output = new StringBuffer(input.length());
    while (matcher.find()) {
      String hex = matcher.group()
        .replace("&", "")
        .replace(String.valueOf(COLOR_CHAR), "")
        .replace("#", "")
        .replace("x", "")
        .replace("X", "")
        .toLowerCase(Locale.ROOT);
      matcher.appendReplacement(output, Matcher.quoteReplacement(toMinecraftHexColor(hex)));
    }
    matcher.appendTail(output);
    return output.toString();
  }

  private static String toMinecraftHexColor(String hex) {
    StringBuilder output = new StringBuilder(14);
    output.append(COLOR_CHAR).append('x');
    for (int i = 0; i < hex.length(); i++) {
      output.append(COLOR_CHAR).append(hex.charAt(i));
    }
    return output.toString();
  }
}
