package com.openlight.cal.data.repository

import androidx.room.withTransaction
import com.openlight.cal.data.db.AppDatabase
import com.openlight.cal.data.model.RedeemedReward
import com.openlight.cal.data.model.Reward
import com.openlight.cal.data.model.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Business logic for the Rewards system.
 *
 * Balance philosophy: we never denormalize a balance onto Person, because
 * the source data (Task.starsEarned, completed-state, redemption rows) all
 * change independently. Computing on demand makes balances always-correct
 * by construction — at the cost of a SQL aggregate per query, which is
 * sub-millisecond on the small datasets a family calendar generates.
 *
 * Redemption philosophy: every redemption is recorded permanently with
 * denormalized name/emoji/cost so history survives reward deletion. Don't
 * try to UPDATE on the rewards row; deleting a reward should preserve the
 * audit trail.
 *
 * Atomicity: redeem() runs the balance-check and the insert inside a single
 * Room @Transaction so a redemption can't happen against a stale balance
 * snapshot. If two clients ever raced (one tablet + one phone) the worst
 * case is one redemption succeeds and the other fails with InsufficientStars.
 */
class RewardRepository(private val db: AppDatabase) {

    private val rewardDao   get() = db.rewardDao()
    private val redeemedDao get() = db.redeemedRewardDao()
    private val taskDao     get() = db.taskDao()

    // ── Catalog ──────────────────────────────────────────────
    val allRewardsFlow: Flow<List<Reward>>     get() = rewardDao.getAllFlow()
    val enabledRewardsFlow: Flow<List<Reward>> get() = rewardDao.getEnabledFlow()

    suspend fun saveReward(reward: Reward): Long = rewardDao.upsert(reward)
    suspend fun deleteReward(reward: Reward) = rewardDao.delete(reward)

    // ── Balances ─────────────────────────────────────────────

    /**
     * Live balance for a single person:
     *     completed tasks' starsEarned  −  redeemed rewards' cost
     *
     * Implemented with combine() so both source flows update the UI in real
     * time when a task is completed or a reward is redeemed.
     */
    fun balanceFlow(personId: Long): Flow<Int> =
        combine(
            taskDao.starsEarnedByPersonFlow(personId),
            redeemedDao.starsSpentByPersonFlow(personId)
        ) { earned, spent -> earned - spent }

    /** One-shot balance for redemption time, must match the Flow definition. */
    suspend fun currentBalance(personId: Long): Int =
        taskDao.starsEarnedByPerson(personId) -
            redeemedDao.starsSpentByPerson(personId)

    // ── Redemption ───────────────────────────────────────────

    sealed class RedeemResult {
        data class Success(val redeemedId: Long, val newBalance: Int) : RedeemResult()
        data class InsufficientStars(val have: Int, val need: Int)     : RedeemResult()
        object  RewardNotFound      : RedeemResult()
        object  RewardDisabled      : RedeemResult()
    }

    /**
     * Atomic redemption. The whole check-then-insert is wrapped in a Room
     * transaction so balance never goes negative due to a race.
     */
    suspend fun redeem(
        rewardId: Long,
        personId: Long,
        note: String = ""
    ): RedeemResult = db.withTransaction {
        val reward = rewardDao.get(rewardId) ?: return@withTransaction RedeemResult.RewardNotFound
        if (!reward.isEnabled) return@withTransaction RedeemResult.RewardDisabled

        val balance = currentBalance(personId)
        if (balance < reward.starCost) {
            return@withTransaction RedeemResult.InsufficientStars(
                have = balance,
                need = reward.starCost
            )
        }

        val redeemed = RedeemedReward(
            rewardId     = reward.id,
            rewardName   = reward.name,
            rewardEmoji  = reward.emoji,
            personId     = personId,
            cost         = reward.starCost,
            note         = note
        )
        val newId = redeemedDao.insert(redeemed)
        RedeemResult.Success(redeemedId = newId, newBalance = balance - reward.starCost)
    }

    /** Allows parents to reverse an erroneous redemption. */
    suspend fun undoRedemption(redeemed: RedeemedReward) = redeemedDao.delete(redeemed)

    // ── Manual Star Adjustment (admin) ───────────────────────
    // We insert a completed task entry with the star delta so the
    // existing balanceFlow (SUM of tasks.starsEarned - SUM of redeemed)
    // picks it up automatically. The special title prefix "🌟" makes
    // these entries visually distinct if they ever show in a task list.
    companion object {
        private const val STAR_ADJUST_TITLE = "\uD83C\uDF1F Star adjustment"
    }

    suspend fun giveStars(personId: Long, amount: Int) {
        db.taskDao().insert(
            Task(
                title = "$STAR_ADJUST_TITLE (given)",
                starsEarned = amount,
                isCompleted = true,
                assignedPersonId = personId
            )
        )
    }

    suspend fun removeStars(personId: Long, amount: Int) {
        db.taskDao().insert(
            Task(
                title = "$STAR_ADJUST_TITLE (removed)",
                starsEarned = -amount,  // negative to subtract
                isCompleted = true,
                assignedPersonId = personId
            )
        )
    }

    // ── History ──────────────────────────────────────────────
    val historyFlow: Flow<List<RedeemedReward>> get() = redeemedDao.getHistoryFlow()
    fun historyForPersonFlow(personId: Long): Flow<List<RedeemedReward>> =
        redeemedDao.getHistoryForPersonFlow(personId)
}
