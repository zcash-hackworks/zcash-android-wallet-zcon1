package cash.z.android.wallet.ui.presenter

import cash.z.android.wallet.data.DataSyncronizer
import cash.z.android.wallet.data.db.*
import cash.z.android.wallet.ui.fragment.Zcon1HomeFragment
import cash.z.android.wallet.ui.presenter.Presenter.PresenterView
import cash.z.wallet.sdk.dao.WalletTransaction
import cash.z.wallet.sdk.data.TransactionState
import cash.z.wallet.sdk.data.Twig
import cash.z.wallet.sdk.data.twig
import dagger.Binds
import dagger.Module
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

class TransactionPresenter @Inject constructor(
    private val view: Zcon1HomeFragment,
    private val synchronizer: DataSyncronizer
) : Presenter {

    interface TransactionView : PresenterView {
        fun setTransactions(transactions: List<WalletTransaction>)
    }

    private var pendingJob: Job? = null
    private var clearedJob: Job? = null

    private var latestPending: List<PendingTransactionEntity> = listOf()
    private var latestCleared: List<WalletTransaction> = listOf()

    //
    // LifeCycle
    //

    override suspend fun start() {
        Twig.sprout("TransactionPresenter")
        twig("TransactionPresenter starting!")

        pendingJob?.cancel()
        pendingJob = view.launchPendingBinder()

        clearedJob?.cancel()
        clearedJob = view.launchClearedBinder()
    }

    override fun stop() {
        twig("TransactionPresenter stopping!")
        Twig.clip("TransactionPresenter")
        pendingJob?.cancel()?.also { pendingJob = null }
        clearedJob?.cancel()?.also { clearedJob = null }
    }

    fun CoroutineScope.launchPendingBinder() = launch {
        val channel = synchronizer.pendingTransactions()
        twig("pending transaction binder starting")
        for (new in channel) {
            twig("pending transactions have been modified... binding to the view")
            latestPending = new
            bind()
        }
        twig("pending transaction binder exiting!")
    }

    fun CoroutineScope.launchClearedBinder() = launch {
        val channel = synchronizer.clearedTransactions()
        twig("cleared transaction binder starting")
        for (new in channel) {
            twig("cleared transactions have been modified... binding to the view")
            latestCleared = new
            bind()
        }
        twig("cleared transaction binder exiting!")
    }


    //
    // Events
    //

    private fun bind() {
        twig("binding ${latestPending.size} pending transactions and ${latestCleared.size} cleared transactions")
        // merge transactions
        val mergedTransactions = mutableListOf<WalletTransaction>()
        latestPending.forEach { mergedTransactions.add(it.toWalletTransaction()) }
        mergedTransactions.addAll(latestCleared)
        mergedTransactions.sortByDescending {
            if (!it.isMined && it.isSend) Long.MAX_VALUE else it.timeInSeconds
        }
        view.setTransactions(mergedTransactions)
    }


    sealed class PurchaseResult {
        data class Processing(val state: TransactionState = TransactionState.Creating) : PurchaseResult()
        data class Failure(val reason: String = "") : PurchaseResult()
    }
}

private fun PendingTransactionEntity.toWalletTransaction(): WalletTransaction {
    var description = when {
        isFailedEncoding() -> "Failed to create! Aborted."
        isFailedSubmit() -> "Failed to send...Retying!"
        isCreating() -> "Creating transaction..."
        isSubmitted() && !isMined() -> "Submitted to network."
        isSubmitted() && isMined() -> "Successfully mined!"
        else -> "Pending..."
    }
    if (!isSubmitted() && (submitAttempts > 2 || encodeAttempts > 2)) {
        description += " aborting in ${ttl() / 60L}m${ttl().rem(60)}s"
    }
    return WalletTransaction(
        value = value,
        isSend = true,
        timeInSeconds = createTime / 1000L,
        address = address,
        status = description,
        memo = memo
    )
}

@Module
abstract class TransactionPresenterModule {
    @Binds
    abstract fun providePresenter(transactionPresenter: TransactionPresenter): Presenter
}