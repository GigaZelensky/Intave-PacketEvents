package de.jpx3.intave.check.other;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.CheckViolationLevelDecrementer;
import de.jpx3.intave.check.other.inventoryclickanalysis.*;
import de.jpx3.intave.executor.TaskTracker;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Bukkit;

public final class InventoryClickAnalysis extends Check {
  public static final double MAX_VL_DECREMENT_PER_SECOND = 1;
  private volatile boolean highToleranceMode;
  private final CheckViolationLevelDecrementer decrementer;

  public InventoryClickAnalysis(IntavePlugin plugin) {
    super("InventoryClickAnalysis", "inventoryclickanalysis");
    decrementer = new CheckViolationLevelDecrementer(this, MAX_VL_DECREMENT_PER_SECOND);
    reloadRuntimeSettings();
    this.startDecrementTask();
    this.setupCheckParts();
  }

  private void reloadRuntimeSettings() {
    this.highToleranceMode = configuration().settings().boolBy("high-tolerance", true);
  }

  @Override
  protected void onConfigurationReload() {
    reloadRuntimeSettings();
  }

  public boolean highToleranceMode() {
    return highToleranceMode;
  }

  private void startDecrementTask() {
    int taskId = Bukkit.getScheduler().scheduleAsyncRepeatingTask(
      IntavePlugin.singletonInstance(),
      () -> UserRepository.applyOnAll(user -> decrementer.decrement(user, 0.05))
      , 40, 40);
    TaskTracker.begun(taskId);
  }

  private void setupCheckParts() {
    appendCheckPart(new OnMoveCheck(this));
    appendCheckPart(new NotOpenCheck(this));
    appendCheckPart(new DelayAnalyzer(this));
    appendCheckPart(new RegrDelayAnalyzer(this));
    appendCheckPart(new PacketDelayAnalyzer(this));
    appendCheckPart(new AutoTotem(this));
  }
}
