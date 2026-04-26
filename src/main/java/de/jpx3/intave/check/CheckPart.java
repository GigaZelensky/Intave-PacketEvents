package de.jpx3.intave.check;

import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/**
 * A {@link CheckPart} is a single detection algorithm in a detection cluster
 * specifically bound to and held by a parent {@link Check}.
 * By holding a parent {@link Check} reference, it constrains
 * itself to only be bound to one parent {@link Check}.
 * A check part can by itself state whether it wants to be enabled and linked (see {@link CheckPart#enabled()}).
 * <br>
 * <br>
 * Note: {@link CheckPart}s of a {@link Check} are not necessarily affected by {@link Check#performLinkage()}.
 * Although they *do* just forward their {@link CheckPart#enabled()} method to {@link Check#enabled()},
 * they could override it, meaning that a {@link CheckPart} can be active while its parent {@link Check} is not.
 *
 * @param <P> parent check type
 * @see Check
 * @see CheckLinker
 * @see MetaCheck
 * @see MetaCheckPart
 */
public abstract class CheckPart<P extends Check> implements EventProcessor {
  private final P parentCheck;

  protected CheckPart(P parentCheck) {
    this.parentCheck = parentCheck;
  }

  /**
   * Performs a {@link User} lookup of a corresponding {@link Player}.
   *
   * @param player the player search
   * @return a blank or corresponding user
   */
  protected final User userOf(Player player) {
    return UserRepository.userOf(player);
  }

  /**
   * Retrieves the parent check
   *
   * @return the parent check
   */
  public final P parentCheck() {
    return parentCheck;
  }

  /**
   * Apply a change to the base statistics and all other statistics of abstract categories.
   *
   * @param user    the affected user
   * @param applier the player statistic applier
   */
  public void statisticApply(User user, Consumer<? super CheckStatistics> applier) {
    parentCheck.statisticApply(user, applier);
  }

  /**
   * Retrieves a {@link TrustFactor} setting for a given key using the trustfactor of the given {@link Player}.
   *
   * @param key    the trustfactor setting key
   * @param player the affected player
   * @return trustfactor setting
   */
  protected int trustFactorSetting(String key, Player player) {
    return parentCheck.trustFactorSetting(key, player);
  }

  /**
   * Retrieve the checks name.
   *
   * @return the checks name
   */
  public String name() {
    return parentCheck.name();
  }

  /**
   * States whether the {@link CheckPart} is enabled and therefore subject to linkage.
   * By default, the method just follows the parent check.
   *
   * @return whether the check part is enabled and therefore subject to linkage
   */
  public boolean enabled() {
    return parentCheck.enabled();
  }

  public void reloadConfiguration() {
  }
}
