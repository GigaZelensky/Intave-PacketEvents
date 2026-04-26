package de.jpx3.intave.module.feedback;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBundle;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerKeepAlive;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPing;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowConfirmation;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketEventBuffer;
import de.jpx3.intave.module.linker.packet.PacketReplay;
import de.jpx3.intave.module.linker.packet.PacketTransport;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ConnectionMetadata;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static de.jpx3.intave.module.feedback.FeedbackOptions.*;

public final class FeedbackSender extends Module {
  public static final short MIN_USER_KEY = Short.MIN_VALUE + 2000;
  public static final short MAX_USER_KEY = Short.MAX_VALUE - 2000;
  public static final int PING_MASK = 0xf5550000;
  private static final boolean USE_PING_PONG_PACKETS = MinecraftVersions.VER1_17_0.atOrAbove();
  private static final boolean SUPPORTS_BUNDLE_PACKETS = MinecraftVersions.VER1_19_4.atOrAbove();
  private static final long OPTIONAL_PENDING_LIMIT = 20;
  private static final long OPTIONAL_SENT_LIMIT = 150;

  private static final long bootTime = System.currentTimeMillis();
  public static IdGeneratorMode activeGenerator = IdGeneratorMode.highestCompatibility();

  private volatile boolean dumpFeedback;
  private volatile boolean bundlingDisabled;

  @Override
  public void enable() {
    reloadConfiguration();
  }

  public void reloadConfiguration() {
    activeGenerator = IdGeneratorMode.highestCompatibility();
    dumpFeedback = plugin.settings().getBoolean("logging.feedback-dump", false);
    bundlingDisabled = plugin.settings().getBoolean("check.physics.no-bundling", false);
    boolean disabledTransactionObfuscation = plugin.settings().getBoolean("compatibility.no-transaction-obfuscation", false);
    if (!disabledTransactionObfuscation && !shouldRefrainFromObfuscation()) {
      if (MinecraftVersions.VER1_17_1.atOrAbove()) {
        activeGenerator = IdGeneratorMode.FULL_RANDOM;
      } else {
        activeGenerator = IdGeneratorMode.modeOfTheDay();
//        activeGenerator = IdGeneratorMode.RANDOM;
      }
    }
  }

  private final List<String> compatibilityModePlugins = Arrays.asList("Vulcan", "Grim", "Verus", "Karhu", "Polar");

  private boolean shouldRefrainFromObfuscation() {
    return compatibilityModePlugins.stream().anyMatch(pluginName -> Bukkit.getPluginManager().isPluginEnabled(pluginName));
  }

  public <T> void doubleSynchronize(
    Player player, ProtocolPacketEvent event, T target,
    FeedbackCallback<T> firstCallback, FeedbackCallback<T> secondCallback
  ) {
    tracedDoubleSynchronize(player, event, target, firstCallback, secondCallback, null, null);
  }

  public <T> void doubleSynchronize(
    Player player, ProtocolPacketEvent event, T target,
    FeedbackCallback<T> firstCallback, FeedbackCallback<T> secondCallback,
    int options
  ) {
    tracedDoubleSynchronize(player, event, target, firstCallback, secondCallback, null, null, options);
  }

  public <T> void tracedDoubleSynchronize(
    Player player, ProtocolPacketEvent event, T target,
    FeedbackCallback<T> firstCallback, FeedbackCallback<T> secondCallback,
    FeedbackObserver firstTracker, FeedbackObserver secondTracker
  ) {
    tracedDoubleSynchronize(player, event, target, firstCallback, secondCallback, firstTracker, secondTracker, 0);
  }

  public <T> void tracedDoubleSynchronize(
    Player player, ProtocolPacketEvent event, T target,
    FeedbackCallback<T> firstCallback, FeedbackCallback<T> secondCallback,
    FeedbackObserver firstTracker, FeedbackObserver secondTracker,
    int options
  ) {
    tracedDoubleSynchronize(player, PacketEventBuffer.clonePacketForReplay(event), target, firstCallback, secondCallback, firstTracker, secondTracker, options);
    event.setCancelled(true);
  }

  public <T> void tracedDoubleSynchronize(
    Player player,
    Object encapsulate, T target,
    FeedbackCallback<? super T> firstCallback, FeedbackCallback<? super T> secondCallback,
    FeedbackObserver firstTracker, FeedbackObserver secondTracker,
    int options
  ) {
    if (!Bukkit.isPrimaryThread()) {
      if (matches(SELF_SYNCHRONIZATION, options)) {
        Synchronizer.synchronize(() -> tracedDoubleSynchronize(player, encapsulate, target, firstCallback, secondCallback, firstTracker, secondTracker, options));
        return;
      }
    }
    User user = UserRepository.userOf(player);
    if (!user.hasPlayer()) {
      return;
    }
    ReentrantLock lock = userLock(user);
    try {
      lock.lock();
      tracedSingleSynchronize(player, target, firstCallback, firstTracker, options);
      PacketReplay.sendToClient(user, encapsulate);
      tracedSingleSynchronize(player, target, secondCallback, secondTracker, options);
    } finally {
      lock.unlock();
    }
  }

  public void synchronize(Player player, Consumer<Void> callback) {
    synchronize(player, callback, (player1, target) -> target.accept(null));
  }

  public void synchronize(Player player, Consumer<Void> callback, ProtocolPacketEvent toBundle) {
    synchronize(player, callback, (player1, target) -> target.accept(null), toBundle);
  }

  public void synchronize(Player player, FeedbackCallback<Object> callback) {
    tracedSingleSynchronize(player, null, callback, null, 0);
  }

  public void synchronize(Player player, FeedbackCallback<Object> callback, ProtocolPacketEvent toBundle) {
    tracedSingleSynchronize(player, null, callback, null, 0, toBundle);
  }

  public <T> void synchronize(Player player, T target, FeedbackCallback<T> callback) {
    synchronize(player, target, callback, 0);
  }

  public <T> void synchronize(Player player, T target, FeedbackCallback<T> callback, ProtocolPacketEvent toBundle) {
    synchronize(player, target, callback, 0, toBundle);
  }

  public void synchronize(Player player, FeedbackCallback<Object> callback, int options) {
    tracedSingleSynchronize(player, null, callback, null, options);
  }

  public void synchronize(Player player, FeedbackCallback<Object> callback, int options, ProtocolPacketEvent toBundle) {
    tracedSingleSynchronize(player, null, callback, null, options, toBundle);
  }

  public <T> void synchronize(Player player, T target, FeedbackCallback<T> callback, int options) {
    tracedSingleSynchronize(player, target, callback, null, options);
  }

  public <T> void synchronize(Player player, T target, FeedbackCallback<T> callback, int options, ProtocolPacketEvent toBundle) {
    tracedSingleSynchronize(player, target, callback, null, options, toBundle);
  }

  public <T> void tracedSingleSynchronize(Player player, T target, FeedbackCallback<T> callback, FeedbackObserver tracker) {
    tracedSingleSynchronize(player, target, callback, tracker, 0);
  }

  public <T> void tracedSingleSynchronize(
    Player player, T target, FeedbackCallback<T> callback, FeedbackObserver tracker, int options
  ) {
    tracedSingleSynchronize(player, target, callback, tracker, options, null);
  }

  public <T> void tracedSingleSynchronize(
    Player player, T target, FeedbackCallback<T> callback, FeedbackObserver tracker, int options,
    @Nullable ProtocolPacketEvent toBundle
  ) {
    if (!Bukkit.isPrimaryThread()) {
      if (matches(SELF_SYNCHRONIZATION, options)) {
        Object deferredBundlePacket = cloneBundleForDeferredSynchronization(player, toBundle);
        Synchronizer.synchronize(() -> tracedSingleSynchronize(player, target, callback, tracker, options, null, deferredBundlePacket));
        return;
      }
    }
    tracedSingleSynchronize(player, target, callback, tracker, options, toBundle, null);
  }

  private <T> void tracedSingleSynchronize(
    Player player, T target, FeedbackCallback<T> callback, FeedbackObserver tracker, int options,
    @Nullable ProtocolPacketEvent toBundle, @Nullable Object deferredBundlePacket
  ) {
    ReentrantLock lock = userLock(userOf(player));
    try {
      lock.lock();
      User user = UserRepository.userOf(player);
      if (!user.hasPlayer()) {
        PacketEventBuffer.releasePacket(deferredBundlePacket);
        return;
      }
      boolean append = false;
      if (matches(APPEND_ON_OVERFLOW, options)) {
        boolean tooManyPending = pendingTransactions(userOf(player)) > OPTIONAL_PENDING_LIMIT;
        boolean sentTooManyRecently = user.meta().connection().transactionPacketCounter > OPTIONAL_SENT_LIMIT;
        append = tooManyPending || sentTooManyRecently;
      }
      if (matches(APPEND, options)) {
        append = true;
      }
      if (append) {
        if (deferredBundlePacket != null) {
          PacketReplay.sendToClient(user, deferredBundlePacket);
        }
        appendRequest(player, target, callback, options);
        return;
      }
      countTransactionPacket(player);
      FeedbackRequest<T> request = createRequest(player, target, callback, tracker, options);
      performRequest(player, request, toBundle, deferredBundlePacket);
    } finally {
      lock.unlock();
    }
  }

  private Object cloneBundleForDeferredSynchronization(Player player, @Nullable ProtocolPacketEvent toBundle) {
    if (toBundle == null) {
      return null;
    }
    User user = UserRepository.userOf(player);
    if (!shouldBundle(user, toBundle)) {
      return null;
    }
    try {
      Object originalPacket = PacketEventBuffer.clonePacketForReplay(toBundle);
      toBundle.setCancelled(true);
      return originalPacket;
    } catch (RuntimeException exception) {
      return null;
    }
  }

  private static final Object FALLBACK_OBJECT = new Object();

  private <T> void appendRequest(
    Player player, T obj, FeedbackCallback<T> callback, int options
  ) {
    User user = UserRepository.userOf(player);
    if (!user.hasPlayer()) {
      return;
    }
    ConnectionMetadata synchronizeData = user.meta().connection();
    Queue<FeedbackRequest<?>> queue = synchronizeData
      .transactionAppendMap()
      .computeIfAbsent(synchronizeData.transactionNumCounter, aLong -> new LinkedBlockingDeque<>());
    if (obj == null) {
      //noinspection unchecked
      obj = (T) FALLBACK_OBJECT;
    }
    queue.add(new FeedbackRequest<>(callback, null, obj, (short) -1, -1, options));
  }

  private /*synchronized*/ <T> FeedbackRequest<T> createRequest(
    Player player, T obj, FeedbackCallback<T> callback, FeedbackObserver tracker, int options
  ) {
    User user = UserRepository.userOf(player);
    ConnectionMetadata connection = user.meta().connection();
    if (obj == null) {
      //noinspection unchecked
      obj = (T) FALLBACK_OBJECT;
    }
    short userKey = findUserKey(player);
    long transactionNumCounter = connection.transactionNumCounter++;
    FeedbackRequest<T> feedbackEntry = new FeedbackRequest<T>(callback, tracker, obj, userKey, transactionNumCounter, options);
    connection.feedbackQueue().add(feedbackEntry);
    return feedbackEntry;
  }

  private /* synchronized */ short findUserKey(Player player) {
    User user = UserRepository.userOf(player);
    ConnectionMetadata connection = user.meta().connection();
    FeedbackQueue feedbackQueue = connection.feedbackQueue();
    Random selectedRandom = connection.feedbackUserKeyRandom;

    int pending = feedbackQueue.size();
    int attempts = 100;
    short counter = Short.MIN_VALUE;
    // select a random seed every time to prevent players from predicting the user key in RAND mode
    // predicted transactions could be a security issue, at least theoretically
    selectedRandom.setSeed(System.currentTimeMillis() ^ System.nanoTime() ^ pending ^ player.getUniqueId().hashCode());
    connection.generatorRunningNum = 0;
    boolean recentlyBooted = System.currentTimeMillis() - bootTime < 120_000 ;
    IdGeneratorMode selectedGenerator = recentlyBooted ? IdGeneratorMode.highestCompatibility() : activeGenerator;

    int lastGeneration;
    do {
      lastGeneration = counter;
      int generatedKey = selectedGenerator.generate(user, connection.lastFeedbackUserKey);
      if (generatedKey <= MIN_USER_KEY || generatedKey >= MAX_USER_KEY) {
        generatedKey = IdGeneratorMode.highestCompatibility().generate(user, connection.lastFeedbackUserKey);
      }
      counter = (short) generatedKey;
    } while ((feedbackQueue.hasUserKey(counter) && attempts > 5) && lastGeneration != counter && attempts-- > 0);
    // if 100 searches are not enough
    if (attempts <= 0) {
      // relax uniqueness requirements
      counter = (short) IdGeneratorMode.FULL_RANDOM.generate(user, connection.lastFeedbackUserKey);
    }
    return (short) (connection.lastFeedbackUserKey = counter);
  }

  private void countTransactionPacket(Player receiver) {
    User user = userOf(receiver);
    ConnectionMetadata connectionData = user.meta().connection();
    connectionData.transactionPacketCounter++;

    if (System.currentTimeMillis() - connectionData.transactionPacketCounterReset > 3000) {
      connectionData.transactionPacketCounter = 0;
      connectionData.transactionPacketCounterReset = System.currentTimeMillis();
    }
  }

  private void performRequest(Player receiver, FeedbackRequest<?> request, @Nullable ProtocolPacketEvent toBundle) {
    performRequest(receiver, request, toBundle, null);
  }

  private void performRequest(
    Player receiver,
    FeedbackRequest<?> request,
    @Nullable ProtocolPacketEvent toBundle,
    @Nullable Object deferredBundlePacket
  ) {
    if (request == null) {
      PacketEventBuffer.releasePacket(deferredBundlePacket);
      return;
    }
    User user = userOf(receiver);
    short id = request.userKey();
    boolean noPingMask = user.meta().protocol().noPingMask();
    PacketWrapper<?> packet = createFeedbackPacket(id, noPingMask);
    if (dumpFeedback) {
      Thread.dumpStack();
    }
    Modules.feedbackAnalysis().sentTransaction(user, request);
    if (IntaveControl.DEBUG_FEEDBACK_PACKETS) {
//      System.out.println("Received " + transactionIdentifier + "/" +transactionResponse.num() + " from " + player.getName());
      System.out.println("Sent " + id + "/"+request.num() + " to " + receiver.getName());
    }
    if (deferredBundlePacket != null) {
      sendBundledRequest(receiver, user, packet, deferredBundlePacket);
    } else if (shouldBundle(user, toBundle)) {
      sendBundledRequest(receiver, user, packet, toBundle);
    } else {
      sendServerPacket(receiver, packet);
    }
    request.sent();
    if (IntaveControl.CLIENT_KEEP_ALIVE_NETTY_CHECK) {
      sendServerPacket(receiver, new WrapperPlayServerKeepAlive(request.userKey()));
    }
  }

  private PacketWrapper<?> createFeedbackPacket(short id, boolean noPingMask) {
    if (USE_PING_PONG_PACKETS) {
      int sentId = noPingMask ? id : (Short.toUnsignedInt(id) | PING_MASK);
      return new WrapperPlayServerPing(sentId);
    }
    return new WrapperPlayServerWindowConfirmation(0, id, false);
  }

  private void sendServerPacket(Player player, Object packet) {
    if (packet == null) {
      return;
    }
    PacketTransport.sendToClient(player, packet);
  }

  private boolean shouldBundle(User user, @Nullable ProtocolPacketEvent toBundle) {
    return SUPPORTS_BUNDLE_PACKETS
      && !bundlingDisabled
      && toBundle != null
      && user.meta().protocol().supportsPacketBundles()
      && !user.meta().protocol().outdatedClient();
  }

  private void sendBundledRequest(Player receiver, User user, PacketWrapper<?> feedbackPacket, ProtocolPacketEvent toBundle) {
    Object originalPacket = PacketEventBuffer.clonePacketForReplay(toBundle);
    toBundle.setCancelled(true);
    sendBundledRequest(receiver, user, feedbackPacket, originalPacket);
  }

  private void sendBundledRequest(Player receiver, User user, PacketWrapper<?> feedbackPacket, Object originalPacket) {
    PacketTransport.sendToClientIgnoring(
      user,
      true,
      new WrapperPlayServerBundle(),
      feedbackPacket,
      originalPacket,
      new WrapperPlayServerBundle()
    );
  }

  private static ReentrantLock userLock(User user) {
    return user.meta().connection().feedbackLock;
  }

  private static long pendingTransactions(User user) {
    return user.meta().connection().feedbackQueue().size();
  }

  private User userOf(Player player) {
    return UserRepository.userOf(player);
  }
}
