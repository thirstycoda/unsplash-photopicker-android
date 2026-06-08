package com.unsplash.pickerandroid.photopicker.presentation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.unsplash.pickerandroid.photopicker.Injector
import com.unsplash.pickerandroid.photopicker.R
import com.unsplash.pickerandroid.photopicker.data.UnsplashPhoto
import com.unsplash.pickerandroid.photopicker.databinding.ActivityPickerBinding

/**
 * Main screen for the picker.
 * This will show a list a photos and a search component.
 * The list is has an infinite scroll.
 */
class UnsplashPickerActivity : AppCompatActivity(), OnPhotoSelectedListener {

    private lateinit var binding: ActivityPickerBinding

    private lateinit var mLayoutManager: StaggeredGridLayoutManager

    private lateinit var mAdapter: UnsplashPhotoAdapter

    private lateinit var mViewModel: UnsplashPickerViewModel

    private var mIsMultipleSelection = false

    private var mCurrentState = UnsplashPickerState.IDLE

    private var mPreviousState = UnsplashPickerState.IDLE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Dynamic Window Inset handling for Edge-to-Edge displays
        ViewCompat.setOnApplyWindowInsetsListener(binding.unsplashPickerRootLayout) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Keep top buttons below the status bar, and list padding clear of the navigation bar
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }

        mIsMultipleSelection = intent.getBooleanExtra(EXTRA_IS_MULTIPLE, false)

        // 2. Setup your layout manager
        mLayoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)

        // 3. Setup adapter configurations
        mAdapter = UnsplashPhotoAdapter(this, mIsMultipleSelection)
        mAdapter.setOnImageSelectedListener(this)

        binding.unsplashPickerRecyclerView.setHasFixedSize(true)
        binding.unsplashPickerRecyclerView.itemAnimator = null
        binding.unsplashPickerRecyclerView.layoutManager = mLayoutManager
        binding.unsplashPickerRecyclerView.adapter = mAdapter

        // 4. Set click listeners
        binding.unsplashPickerBackImageView.setOnClickListener { onBackPressed() }
        binding.unsplashPickerCancelImageView.setOnClickListener { onBackPressed() }
        binding.unsplashPickerClearImageView.setOnClickListener { onBackPressed() }
        binding.unsplashPickerSearchImageView.setOnClickListener {
            mCurrentState = UnsplashPickerState.SEARCHING
            updateUiFromState()
        }
        binding.unsplashPickerDoneImageView.setOnClickListener { sendPhotosAsResult() }

        // 5. Initialize view model bindings
        mViewModel = ViewModelProviders.of(this, Injector.createPickerViewModelFactory())
            .get(UnsplashPickerViewModel::class.java)
        observeViewModel()
        mViewModel.bindSearch(binding.unsplashPickerEditText)
    }

    /**
     * Observes the live data in the view model.
     */
    private fun observeViewModel() {
        mViewModel.errorLiveData.observe(this, Observer {
            Toast.makeText(this, "error", Toast.LENGTH_SHORT).show()
        })
        mViewModel.messageLiveData.observe(this, Observer {
            Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
        })
        mViewModel.loadingLiveData.observe(this, Observer {
            binding.unsplashPickerProgressBarLayout.visibility = if (it != null && it) View.VISIBLE else View.GONE
        })
        mViewModel.photosLiveData.observe(this, Observer {
            binding.unsplashPickerNoResultTextView.visibility =
                    if (it == null || it.isEmpty()) View.VISIBLE
                    else View.GONE
            mAdapter.submitList(it)
        })
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // we want the recycler view to have 3 columns when in landscape and 2 in portrait
        mLayoutManager.spanCount =
                if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) 3
                else 2
        mAdapter.notifyDataSetChanged()
    }

    override fun onPhotoSelected(nbOfSelectedPhotos: Int) {
        // if multiple selection
        if (mIsMultipleSelection) {
            // update the title
            binding.unsplashPickerTitleTextView.text = when (nbOfSelectedPhotos) {
                0 -> getString(R.string.unsplash)
                1 -> getString(R.string.photo_selected)
                else -> getString(R.string.photos_selected, nbOfSelectedPhotos)
            }
            // updating state
            if (nbOfSelectedPhotos > 0) {
                // only once, ignoring all subsequent photo selections
                if (mCurrentState != UnsplashPickerState.PHOTO_SELECTED) {
                    mPreviousState = mCurrentState
                    mCurrentState = UnsplashPickerState.PHOTO_SELECTED
                }
                updateUiFromState()
            } else { // no photo selected means un-selection
                onBackPressed()
            }
        }
        // if single selection send selected photo as a result
        else if (nbOfSelectedPhotos > 0) {
            sendPhotosAsResult()
        }
    }

    /**
     * Sends images in the result intent as a result for the calling activity.
     */
    private fun sendPhotosAsResult() {
        // get the selected photos
        val photos: ArrayList<UnsplashPhoto> = mAdapter.getImages()
        // track the downloads
        mViewModel.trackDownloads(photos)
        // send them back to the calling activity
        val data = Intent()
        data.putExtra(EXTRA_PHOTOS, photos)
        setResult(Activity.RESULT_OK, data)
        finish()
    }

    override fun onPhotoLongPress(imageView: ImageView, url: String) {
        startActivity(PhotoShowActivity.getStartingIntent(this, url))
    }

    override fun onBackPressed() {
        when (mCurrentState) {
            UnsplashPickerState.IDLE -> {
                super.onBackPressed()
            }
            UnsplashPickerState.SEARCHING -> {
                // updating states
                mCurrentState = UnsplashPickerState.IDLE
                mPreviousState = UnsplashPickerState.SEARCHING
                // updating ui
                updateUiFromState()
            }
            UnsplashPickerState.PHOTO_SELECTED -> {
                // updating states
                mCurrentState = if (mPreviousState == UnsplashPickerState.SEARCHING) {
                    UnsplashPickerState.SEARCHING
                } else {
                    UnsplashPickerState.IDLE
                }
                mPreviousState = UnsplashPickerState.PHOTO_SELECTED
                // updating ui
                updateUiFromState()
            }
        }
    }

    /*
    STATES
     */

    private fun updateUiFromState() {
        when (mCurrentState) {
            UnsplashPickerState.IDLE -> {
                // back and search buttons visible
                binding.unsplashPickerBackImageView.visibility = View.VISIBLE
                binding.unsplashPickerSearchImageView.visibility = View.VISIBLE
                // cancel and done buttons gone
                binding.unsplashPickerCancelImageView.visibility = View.GONE
                binding.unsplashPickerDoneImageView.visibility = View.GONE
                // edit text cleared and gone
                if (!TextUtils.isEmpty(binding.unsplashPickerEditText.text)) {
                    binding.unsplashPickerEditText.setText("")
                }
                binding.unsplashPickerEditText.visibility = View.GONE
                // right clear button on top of edit text gone
                binding.unsplashPickerClearImageView.visibility = View.GONE
                // keyboard down
                binding.unsplashPickerEditText.closeKeyboard(this)
                // action bar with unsplash
                binding.unsplashPickerTitleTextView.text = getString(R.string.unsplash)
                // clear list selection
                mAdapter.clearSelection()
                mAdapter.notifyDataSetChanged()
            }
            UnsplashPickerState.SEARCHING -> {
                // back, cancel, done or search buttons gone
                binding.unsplashPickerBackImageView.visibility = View.GONE
                binding.unsplashPickerCancelImageView.visibility = View.GONE
                binding.unsplashPickerDoneImageView.visibility = View.GONE
                binding.unsplashPickerSearchImageView.visibility = View.GONE
                // edit text visible and focused
                binding.unsplashPickerEditText.visibility = View.VISIBLE
                // right clear button on top of edit text visible
                binding.unsplashPickerClearImageView.visibility = View.VISIBLE
                // keyboard up
                binding.unsplashPickerEditText.requestFocus()
                binding.unsplashPickerEditText.openKeyboard(this)
                // clear list selection
                mAdapter.clearSelection()
                mAdapter.notifyDataSetChanged()
            }
            UnsplashPickerState.PHOTO_SELECTED -> {
                // back and search buttons gone
                binding.unsplashPickerBackImageView.visibility = View.GONE
                binding.unsplashPickerSearchImageView.visibility = View.GONE
                // cancel and done buttons visible
                binding.unsplashPickerCancelImageView.visibility = View.VISIBLE
                binding.unsplashPickerDoneImageView.visibility = View.VISIBLE
                // edit text gone
                binding.unsplashPickerEditText.visibility = View.GONE
                // right clear button on top of edit text gone
                binding.unsplashPickerClearImageView.visibility = View.GONE
                // keyboard down
                binding.unsplashPickerEditText.closeKeyboard(this)
            }
        }
    }

    companion object {
        const val EXTRA_PHOTOS = "EXTRA_PHOTOS"
        private const val EXTRA_IS_MULTIPLE = "EXTRA_IS_MULTIPLE"

        /**
         * @param callingContext the calling context
         * @param isMultipleSelection true if multiple selection, false otherwise
         *
         * @return the intent needed to come to this activity
         */
        fun getStartingIntent(callingContext: Context, isMultipleSelection: Boolean): Intent {
            val intent = Intent(callingContext, UnsplashPickerActivity::class.java)
            intent.putExtra(EXTRA_IS_MULTIPLE, isMultipleSelection)
            return intent
        }
    }
}
