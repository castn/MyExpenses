package org.totschnig.myexpenses.activity

import android.os.Bundle
import android.view.Menu
import android.view.ViewGroup.MarginLayoutParams
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.databinding.OnboardingBinding
import org.totschnig.myexpenses.fragment.OnBoardingPrivacyFragment
import org.totschnig.myexpenses.fragment.OnboardingDataFragment
import org.totschnig.myexpenses.fragment.OnboardingUiFragment

class OnboardingActivity : OnboardingHostActivity() {
    private lateinit var binding: OnboardingBinding
    private lateinit var pagerAdapter: MyPagerAdapter
    private lateinit var onBackPressedCallback: OnBackPressedCallback

    override val drawToTopEdge = true

    override val drawToBottomEdge = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        binding = OnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        pagerAdapter = MyPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.offscreenPageLimit = 2
        ViewCompat.setOnApplyWindowInsetsListener(binding.pageIndicatorView) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.updateLayoutParams<MarginLayoutParams> {
                topMargin = bars.top
            }
            insets
        }
        onBackPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                val currentItem = binding.viewPager.currentItem
                if (currentItem > 0) {
                    binding.viewPager.currentItem = currentItem - 1
                } else {
                    onBackPressedDispatcher.onBackPressed()

                }
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
        binding.viewPager.registerOnPageChangeCallback(object :
            ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                onBackPressedCallback.isEnabled = position > 0
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu) = false //skip help

    fun navigateNext() {
        val currentItem = binding.viewPager.currentItem
        binding.viewPager.setCurrentItem(currentItem + 1, true)
    }

    private val dataFragment: OnboardingDataFragment?
        get() = getFragmentAtPosition(2) as? OnboardingDataFragment

    private val privacyFragment: OnBoardingPrivacyFragment?
        get() = getFragmentAtPosition(1) as? OnBoardingPrivacyFragment

    private fun getFragmentAtPosition(pos: Int) =
        supportFragmentManager.findFragmentByTag(pagerAdapter.getFragmentName(pos))

    override fun onSyncAccountsChanged() {
        dataFragment?.setupMenu()
    }

    override fun onEncryptionCancelled() {
        privacyFragment?.setupMenu()
    }

    private class MyPagerAdapter(activity: FragmentActivity) :
        FragmentStateAdapter(activity) {
        fun getFragmentName(currentPosition: Int): String {
            //https://stackoverflow.com/a/61178226/1199911
            return "f" + getItemId(currentPosition)
        }


        override fun createFragment(position: Int) = when (position) {
            0 -> OnboardingUiFragment.newInstance()
            1 -> OnBoardingPrivacyFragment.newInstance()
            else -> OnboardingDataFragment.newInstance()
        }

        override fun getItemCount() = 3
    }

    override val snackBarContainerId: Int
        get() {
            return binding.viewPager.id
        }
}