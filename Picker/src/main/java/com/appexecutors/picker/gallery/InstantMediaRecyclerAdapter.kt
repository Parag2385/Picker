package com.appexecutors.picker.gallery

import android.content.Context
import android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
import android.view.LayoutInflater
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.recyclerview.widget.RecyclerView
import com.appexecutors.picker.databinding.RecyclerItemMediaBinding
import com.appexecutors.picker.interfaces.MediaClickInterface
import com.appexecutors.picker.utils.GeneralUtils.convertDpToPixel
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.request.RequestOptions


class InstantMediaRecyclerAdapter(val mMediaList: ArrayList<MediaModel>, val mInterface: MediaClickInterface, private val mContext: Context): RecyclerView.Adapter<InstantMediaRecyclerAdapter.MediaViewHolder>() {

    var maxCount = 0
    private val glide: RequestManager
    private val options: RequestOptions
    private var size = 0f
    private val margin = 3
    private var padding = 0

    init {
        size = convertDpToPixel(72f, mContext) - 2
        padding = (size / 3.5).toInt()
        options = RequestOptions().override(300).transform(CenterCrop())
            .transform(FitCenter())
        glide = Glide.with(mContext)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val mBinding = RecyclerItemMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MediaViewHolder(mBinding)
    }

    override fun getItemCount(): Int {
        return mMediaList.size
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(holder.adapterPosition)
    }

    var imageCount = 0

    inner class MediaViewHolder(private val mBinding: RecyclerItemMediaBinding): RecyclerView.ViewHolder(mBinding.root) {

        fun bind(position: Int){
            val media = mMediaList[position]
            if (media.mMediaUri == null){
                itemView.visibility = GONE
                itemView.layoutParams = FrameLayout.LayoutParams(0,0)
            }else {
                itemView.visibility = VISIBLE
                val layoutParams = FrameLayout.LayoutParams(size.toInt(), size.toInt())

                if (position == 0) {
                    layoutParams.setMargins(-(margin / 2), margin, margin, margin)
                } else {
                    layoutParams.setMargins(margin, margin, margin, margin)
                }

                itemView.layoutParams = layoutParams

                glide.load(media.mMediaUri)
                    .apply(options)
                    .into(mBinding.imageView)

                if (media.mMediaType == MEDIA_TYPE_VIDEO) mBinding.imageViewVideo.visibility = VISIBLE
                else mBinding.imageViewVideo.visibility = GONE

                if (media.isSelected) mBinding.imageViewSelection.visibility = VISIBLE
                else mBinding.imageViewSelection.visibility = GONE

                itemView.setOnClickListener {
                    if (imageCount == 0) {
                        media.isSelected = !media.isSelected
                        mInterface.onMediaClick(media)
                    }
                    else if (imageCount < maxCount || media.isSelected){
                        media.isSelected = !media.isSelected
                        notifyItemChanged(position)
                        if (media.isSelected) imageCount++ else imageCount--
                        mInterface.onMediaLongClick(media, this@InstantMediaRecyclerAdapter::class.java.simpleName)
                    }else{
                        Toast.makeText(mContext, "Cannot add more than $maxCount items", LENGTH_SHORT).show()
                    }
                }

                itemView.setOnLongClickListener {
                    if (imageCount == 0) {
                        media.isSelected = true
                        notifyItemChanged(position)
                        imageCount++
                        mInterface.onMediaLongClick(media, this@InstantMediaRecyclerAdapter::class.java.simpleName)
                    }
                    true
                }
            }


        }
    }
}