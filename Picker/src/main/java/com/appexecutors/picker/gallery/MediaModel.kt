package com.appexecutors.picker.gallery

import android.net.Uri

data class MediaModel(
    var mMediaUri: Uri?,
    var mMediaType: Int,
    var mMediaDate: String
){
    var isSelected = false
}