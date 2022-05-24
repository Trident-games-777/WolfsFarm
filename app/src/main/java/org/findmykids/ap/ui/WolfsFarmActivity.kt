package org.findmykids.ap.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.findmykids.ap.databinding.ActivityWolfsFarmBinding
import org.findmykids.ap.other.Extras
import org.findmykids.ap.play.PlayStart
import org.findmykids.ap.view_models.WolfsFarmViewModel
import org.findmykids.ap.view_models.WolfsFarmViewModelFactory

class WolfsFarmActivity : AppCompatActivity() {

    private var _binding: ActivityWolfsFarmBinding? = null
    private val binding get() = _binding!!
    private val wolfsViewModel by viewModels<WolfsFarmViewModel> {
        WolfsFarmViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityWolfsFarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!wolfsViewModel.isRootsAndAdbEnabled()) {
            play()
        } else {
            lifecycleScope.launch {
                val isFirstLaunch = async { wolfsViewModel.fetchIsFirstLaunch() }
                if (isFirstLaunch.await()) {
                    wolfsViewModel.urlLiveData.observe(this@WolfsFarmActivity) { url ->
                        launchWebView(url)
                    }
                } else {
                    val url = async { wolfsViewModel.fetchUrl() }
                    launchWebView(url.await())
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    private fun play() {
        val intent = Intent(this, PlayStart::class.java)
        startActivity(intent)
        finish()
    }

    private fun launchWebView(url: String): Unit =
        with(Intent(this@WolfsFarmActivity, WolfsFarmWebViewActivity::class.java)) {
            putExtra(Extras.LINK_EXTRA, url)
            startActivity(this)
            this@WolfsFarmActivity.finish()
        }
}