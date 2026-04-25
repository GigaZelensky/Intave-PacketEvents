package de.jpx3.intave.module.filter;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.EventProcessor;

public class Filter implements EventProcessor {
  private final String name;
  private volatile boolean enabled;

  public Filter(String name) {
    this.name = name;
    reloadBaseConfiguration();
  }

  public void reloadConfiguration() {
    reloadBaseConfiguration();
  }

  private void reloadBaseConfiguration() {
    this.enabled = IntavePlugin.singletonInstance().settings().getBoolean("filter." + name);
  }

  protected boolean enabled() {
    return enabled;
  }
}
