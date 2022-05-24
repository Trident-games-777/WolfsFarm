package org.findmykids.ap.play

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.addListener
import org.findmykids.ap.R
import org.findmykids.ap.databinding.ActivityPlayStartBinding

class PlayStart : AppCompatActivity() {
    private var _binding: ActivityPlayStartBinding? = null
    private val binding get() = _binding!!
    private var tag: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityPlayStartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.button1.setOnClickListener {
            tag = it.tag as String
            binding.button2.visibility = View.INVISIBLE
            binding.button1.isClickable = false
            binding.button2.isClickable = false
            start()
        }
        binding.button2.setOnClickListener {
            tag = it.tag as String
            binding.button1.visibility = View.INVISIBLE
            binding.button1.isClickable = false
            binding.button2.isClickable = false
            start()
        }
        binding.btNext.setOnClickListener {
            startActivity(Intent(this@PlayStart, PlayStart::class.java))
            finish()
        }
    }

    private fun start() {
        val images = listOf(
            R.drawable.e1,
            R.drawable.e2,
            R.drawable.e3,
            R.drawable.e4,
            R.drawable.e5,
        )
        ValueAnimator.ofInt(0, 50).apply {
            duration = 2000
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val randomIndex = (0..4).random()
                binding.ivResult.setImageResource(images[randomIndex])
                binding.ivResult.tag = "$randomIndex"
            }
            addListener(onEnd = {
                if (binding.ivResult.tag == tag) {
                    binding.tvResult.text = "You win"
                } else {
                    binding.tvResult.text = "You loose"
                }
                binding.btNext.visibility = View.VISIBLE
            })
            start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}