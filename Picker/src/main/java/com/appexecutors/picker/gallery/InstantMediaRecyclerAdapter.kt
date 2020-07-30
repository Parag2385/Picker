package com.appexecutors.picker.gallery

import android.content.Context
import android.view.LayoutInflater
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.appexecutors.picker.databinding.RecyclerItemMediaBinding
import com.appexecutors.picker.utils.GeneralUtils.convertDpToPixel
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.request.RequestOptions


class InstantMediaRecyclerAdapter(val mMediaList: ArrayList<MediaModel>, mContext: Context): RecyclerView.Adapter<InstantMediaRecyclerAdapter.MediaViewHolder>() {

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

    inner class MediaViewHolder(private val mBinding: RecyclerItemMediaBinding): RecyclerView.ViewHolder(mBinding.root) {

        fun bind(position: Int){
            val media = mMediaList[position]
            if (media.mMediaUri == null){
                itemView.visibility = GONE
                itemView.layoutParams = LinearLayout.LayoutParams(0,0)
            }else {
                itemView.visibility = VISIBLE
                val layoutParams = LinearLayout.LayoutParams(size.toInt(), size.toInt())

                if (position == 0) {
                    layoutParams.setMargins(-(margin / 2), margin, margin, margin)
                } else {
                    layoutParams.setMargins(margin, margin, margin, margin)
                }

                itemView.layoutParams = layoutParams

                glide.load(media.mMediaUri)
                    .apply(options)
                    .into(mBinding.imageView)
            }


        }
    }
}