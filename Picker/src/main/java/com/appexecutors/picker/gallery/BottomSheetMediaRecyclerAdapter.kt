package com.appexecutors.picker.gallery

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.recyclerview.widget.RecyclerView
import com.appexecutors.picker.R
import com.appexecutors.picker.databinding.RecyclerItemDateHeaderBinding
import com.appexecutors.picker.databinding.RecyclerItemMediaBinding
import com.appexecutors.picker.interfaces.MediaClickInterface
import com.appexecutors.picker.utils.GeneralUtils.getScreenWidth
import com.appexecutors.picker.utils.HeaderItemDecoration
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.request.RequestOptions


class BottomSheetMediaRecyclerAdapter(private val mMediaList: ArrayList<MediaModel>, val mInterface: MediaClickInterface, val mContext: Context):
    RecyclerView.Adapter<RecyclerView.ViewHolder>(), HeaderItemDecoration.StickyHeaderInterface {

    companion object{
        const val HEADER = 1
        const val ITEM = 2
        const val SPAN_COUNT = 4
        private const val MARGIN = 4
    }

    var maxCount = 0
    var layoutParams: FrameLayout.LayoutParams
    private val glide: RequestManager
    private val options: RequestOptions

    init {
        val size: Int =
            getScreenWidth(mContext as Activity) / SPAN_COUNT - MARGIN / 2
        layoutParams = FrameLayout.LayoutParams(size, size)
        layoutParams.setMargins(
            MARGIN, MARGIN - MARGIN / 2,
            MARGIN,
            MARGIN - MARGIN / 2
        )
        options = RequestOptions().override(300).transform(CenterCrop())
            .transform(FitCenter())
        glide = Glide.with(mContext)
    }

    override fun getItemViewType(position: Int): Int {
        return if (mMediaList[position].mMediaUri == null) HEADER else ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == HEADER){
            val mBinding = RecyclerItemDateHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            HeaderViewHolder(mBinding)
        }else {
            val mBinding = RecyclerItemMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            MediaViewHolder(mBinding)
        }
    }

    override fun getItemCount(): Int {
        return mMediaList.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HeaderViewHolder) holder.bind(mMediaList[holder.adapterPosition])
        else if (holder is MediaViewHolder) holder.bind(holder.adapterPosition)
    }

    var imageCount = 0
    var mTapToSelect = false

    inner class MediaViewHolder(private val mBinding: RecyclerItemMediaBinding): RecyclerView.ViewHolder(mBinding.root) {

        fun bind(position: Int){
            val media = mMediaList[position]
            mBinding.imageView.layoutParams = layoutParams

            glide.load(media.mMediaUri)
                .apply(options)
                .into(mBinding.imageView)

            if (media.isSelected) mBinding.imageViewSelection.visibility = View.VISIBLE
            else mBinding.imageViewSelection.visibility = View.GONE

            itemView.setOnClickListener {
                if (imageCount == 0 && !mTapToSelect) {
                    media.isSelected = !media.isSelected
                    mInterface.onMediaClick(media)
                }
                else if (imageCount < maxCount || media.isSelected){
                    media.isSelected = !media.isSelected
                    notifyItemChanged(position)
                    if (media.isSelected) imageCount++ else imageCount--
                    mInterface.onMediaLongClick(media, this@BottomSheetMediaRecyclerAdapter::class.java.simpleName)
                }else{
                    Toast.makeText(mContext, "Cannot add more than $maxCount items", LENGTH_SHORT).show()
                }
            }

            itemView.setOnLongClickListener {
                if (imageCount == 0) {
                    media.isSelected = true
                    notifyItemChanged(position)
                    imageCount++
                    mInterface.onMediaLongClick(media, this@BottomSheetMediaRecyclerAdapter::class.java.simpleName)
                }
                true
            }
        }
    }

    inner class HeaderViewHolder(private val mBinding: RecyclerItemDateHeaderBinding): RecyclerView.ViewHolder(mBinding.root) {

        fun bind(media: MediaModel){
            mBinding.header.text = media.mMediaDate
        }
    }

    override fun getHeaderPositionForItem(itemPosition: Int): Int {
        var position: Int = itemPosition
        var headerPosition = 0
        do {
            if (isHeader(position)) {
                headerPosition = position
                break
            }
            position -= 1
        } while (position >= 0)
        return headerPosition
    }

    override fun getHeaderLayout(headerPosition: Int): Int {
        return R.layout.recycler_item_date_header
    }

    override fun bindHeaderData(header: View?, headerPosition: Int) {
        header?.findViewById<TextView>(R.id.header)?.text = mMediaList[headerPosition].mMediaDate
    }

    override fun isHeader(itemPosition: Int): Boolean {
        return getItemViewType(itemPosition) == HEADER
    }
}