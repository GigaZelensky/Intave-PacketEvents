package de.jpx3.intave.user.meta;

import com.google.common.collect.Maps;

import java.util.Map;

public final class ViolationMetadata {
  public double physicsOffset;
  public double physicsVL;
  public double physicsInsignificantBufferVL;
  public double physicsPredictionAdvantage;
  public double physicsSuppressedOffset;
  public double physicsVelocityVL;
  public double physicsVehicleVL;
  public double physicsInvalidMovementsInRow;
  public volatile boolean isInActiveTeleportBundle;
  public volatile boolean disableActiveTeleportBundleNextTeleportAccept;
  public volatile boolean doNotVerifyBaseMotion;

  public long lastMovementDebugRequest;

  public double backtrackVL;
  public long lastBacktrackVLChange;
  public long lastBacktrackHitCancelRequest;

  public double wrappedNoSlowdownVL;

  public int detectionCounter;
  public long detectionCounterReset;

  public long lastBlockPlaceDenyRequest;

  public int facingFailedCounter = 0;

  public Map<String, Map<String, Double>> violationLevel = Maps.newConcurrentMap();
  public Map<String, Map<String, Double>> violationLevelGainedCounter = Maps.newConcurrentMap();
  public Map<String, Map<String, Long>> lastViolationLevelGainedCounterReset = Maps.newConcurrentMap();
}