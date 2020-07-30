package com.appexecutors.picker.utils

import android.content.Context
import android.database.Cursor
import android.os.Build
import android.provider.MediaStore

object MediaConstants {

    private var IMAGE_VIDEO_PROJECTION = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.PARENT,
        MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Files.FileColumns.DATE_ADDED,
        MediaStore.Files.FileColumns.DATE_MODIFIED,
        MediaStore.Files.FileColumns.MEDIA_TYPE,
        MediaStore.Files.FileColumns.MIME_TYPE,
        MediaStore.Files.FileColumns.TITLE
    )

    var IMAGE_VIDEO_URI =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
    else MediaStore.Files.getContentUri("external")



    private var IMAGE_VIDEO_SELECTION = (MediaStore.Files.FileColumns.MEDIA_TYPE + "="
            + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
            + " OR "
            + MediaStore.Files.FileColumns.MEDIA_TYPE + "="
            + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)

    private var IMAGE_SELECTION = (MediaStore.Files.FileColumns.MEDIA_TYPE + "="
            + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE)

    private var IMAGE_VIDEO_ORDER_BY = MediaStore.Images.Media.DATE_MODIFIED + " DESC"

    fun getImageVideoCursor(
        context: Context,
        excludeVideo: Boolean
    ): Cursor? {
        return context.contentResolver
            .query(
                IMAGE_VIDEO_URI,
                IMAGE_VIDEO_PROJECTION,
                if (excludeVideo)IMAGE_SELECTION else IMAGE_VIDEO_SELECTION,
                null,
                IMAGE_VIDEO_ORDER_BY
            )
    }
}