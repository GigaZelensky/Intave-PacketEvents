package de.jpx3.intave.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public final class MessageColorsTests {
  private static final char COLOR_CHAR = '\u00A7';

  @Test
  public void translatesShortHexColors() {
    assertEquals(hex("12abef") + "Hello", MessageColors.translate("&#12AbEfHello"));
  }

  @Test
  public void translatesExpandedHexColors() {
    assertEquals(hex("ffffff") + "Hello", MessageColors.translate("&x&F&F&F&F&F&FHello"));
  }

  @Test
  public void translatesLegacyColorsAfterHexColors() {
    assertEquals(hex("aabbcc") + "Hex " + COLOR_CHAR + "cRed", MessageColors.translate("&#AABBCCHex &cRed"));
  }

  @Test
  public void preservesNullInput() {
    assertNull(MessageColors.translate(null));
  }

  private static String hex(String value) {
    StringBuilder output = new StringBuilder();
    output.append(COLOR_CHAR).append('x');
    for (int i = 0; i < value.length(); i++) {
      output.append(COLOR_CHAR).append(value.charAt(i));
    }
    return output.toString();
  }
}
