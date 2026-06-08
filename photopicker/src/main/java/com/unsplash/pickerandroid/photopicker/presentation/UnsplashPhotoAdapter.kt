package com.unsplash.pickerandroid.photopicker.presentation

import android.content.Context
import android.graphics.Color
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.unsplash.pickerandroid.photopicker.UnsplashPhotoPicker
import com.unsplash.pickerandroid.photopicker.data.UnsplashPhoto
import com.unsplash.pickerandroid.photopicker.databinding.ItemUnsplashPhotoBinding

/**
 * The photos recycler view adapter.
 * This is using the Android paging library to display an infinite list of photos.
 * This deals with either a single or multiple selection list.
 */
class UnsplashPhotoAdapter constructor(context: Context, private val isMultipleSelection: Boolean) :
    PagedListAdapter<UnsplashPhoto, UnsplashPhotoAdapter.PhotoViewHolder>(COMPARATOR) {

    private val mLayoutInflater: LayoutInflater = LayoutInflater.from(context)

    private val mSelectedIndexes = ArrayList<Int>()

    private val mSelectedImages = ArrayList<UnsplashPhoto>()

    private var mOnPhotoSelectedListener: OnPhotoSelectedListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemUnsplashPhotoBinding.inflate(mLayoutInflater, parent, false)
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        // item
        getItem(position)?.let { photo ->
            // image
            holder.binding.itemUnsplashPhotoImageView.aspectRatio = photo.height.toDouble() / photo.width.toDouble()
            holder.itemView.setBackgroundColor(Color.parseColor(photo.color))
            Picasso.get().load(photo.urls.small)
                .into(holder.binding.itemUnsplashPhotoImageView)
            // photograph name
            val txt = String.format("<a href=\"%s?utm_source=%s&utm_medium=referral\">%s</a>", photo.user.links.html, UnsplashPhotoPicker.getAppName(), photo.user.name)
            holder.binding.itemUnsplashPhotoTextView.text = HtmlCompat.fromHtml(txt, HtmlCompat.FROM_HTML_MODE_LEGACY)
            holder.binding.itemUnsplashPhotoTextView.movementMethod = LinkMovementMethod.getInstance()
            // selected controls visibility
            holder.binding.itemUnsplashPhotoCheckedImageView.visibility =
                    if (mSelectedIndexes.contains(holder.adapterPosition)) View.VISIBLE else View.INVISIBLE
            holder.binding.itemUnsplashPhotoOverlay.visibility =
                    if (mSelectedIndexes.contains(holder.adapterPosition)) View.VISIBLE else View.INVISIBLE
            // click listener
            holder.itemView.setOnClickListener {
                // selected index(es) management
                if (mSelectedIndexes.contains(holder.adapterPosition)) {
                    mSelectedIndexes.remove(holder.adapterPosition)
                } else {
                    if (!isMultipleSelection) mSelectedIndexes.clear()
                    mSelectedIndexes.add(holder.adapterPosition)
                }
                if (isMultipleSelection) {
                    notifyDataSetChanged()
                }
                mOnPhotoSelectedListener?.onPhotoSelected(mSelectedIndexes.size)
                // change title text
            }
            holder.itemView.setOnLongClickListener {
                photo.urls.regular?.let {
                    mOnPhotoSelectedListener?.onPhotoLongPress(holder.binding.itemUnsplashPhotoImageView, it)
                }
                false
            }
        }
    }

    /**
     * Getter for the selected images.
     */
    fun getImages(): ArrayList<UnsplashPhoto> {
        mSelectedImages.clear()
        for (index in mSelectedIndexes) {
            currentList?.get(index)?.let {
                mSelectedImages.add(it)
            }
        }
        return mSelectedImages
    }

    fun clearSelection() {
        mSelectedImages.clear()
        mSelectedIndexes.clear()
    }

    fun setOnImageSelectedListener(onPhotoSelectedListener: OnPhotoSelectedListener) {
        mOnPhotoSelectedListener = onPhotoSelectedListener
    }

    companion object {
        // diff util comparator
        val COMPARATOR = object : DiffUtil.ItemCallback<UnsplashPhoto>() {
            override fun areContentsTheSame(oldItem: UnsplashPhoto, newItem: UnsplashPhoto): Boolean =
                oldItem == newItem

            override fun areItemsTheSame(oldItem: UnsplashPhoto, newItem: UnsplashPhoto): Boolean =
                oldItem == newItem
        }
    }

    /**
     * UnsplashPhoto view holder.
     */
    class PhotoViewHolder(val binding: ItemUnsplashPhotoBinding) : RecyclerView.ViewHolder(binding.root)
}