package cash.z.android.wallet.ui.presenter

import cash.z.android.wallet.ui.presenter.Presenter.PresenterView
import cash.z.wallet.sdk.data.Synchronizer
import cash.z.wallet.sdk.data.Twig
import cash.z.wallet.sdk.data.twig
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlin.coroutines.CoroutineContext

class ProgressPresenter(
    private val view: ProgressView,
    private val synchronizer: Synchronizer
) : Presenter, CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext get() = Dispatchers.Main + job

    interface ProgressView : PresenterView {
        fun showProgress(progress: Int)
    }

    //
    // LifeCycle
    //

    override suspend fun start() {
        Twig.sprout("ProgressPresenter")
        twig("starting")
        launchProgressMonitor(synchronizer.progress())
    }

    override fun stop() {
        Twig.clip("ProgressPresenter")
        twig("stopping")
    }

    private fun CoroutineScope.launchProgressMonitor(channel: ReceiveChannel<Int>) = launch {
        twig("progress monitor starting on thread ${Thread.currentThread().name}!")
        for (i in channel) {
            bind(i)
        }
        // TODO: remove this temp fix:
        bind(100)
        twig("progress monitor exiting!")
    }

    private fun bind(progress: Int) = launch {
        twig("binding progress of $progress on thread ${Thread.currentThread().name}!")
        view.showProgress(progress)
    }
}