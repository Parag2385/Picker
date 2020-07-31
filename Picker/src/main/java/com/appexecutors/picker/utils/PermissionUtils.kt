package com.appexecutors.picker.utils

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.appexecutors.picker.interfaces.PermissionCallback

object PermissionUtils {

    private const val REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS = 9921

    @RequiresApi(api = Build.VERSION_CODES.M)
    private fun addPermission(
        permissionsList: MutableList<String>, permission: String,
        ac: FragmentActivity
    ): Boolean {
        if (ac.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(permission)
            // Check for Rationale Option
            return ac.shouldShowRequestPermissionRationale(permission)
        }
        return true
    }


    fun checkForCameraWritePermissions(activity: FragmentActivity, permissionCall: PermissionCallback) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            permissionCall.onPermission(true)
        } else {
            val permissionsNeeded = ArrayList<String>()
            val permissionsList = ArrayList<String>()
            if (!addPermission(permissionsList, Manifest.permission.CAMERA, activity))
                permissionsNeeded.add("CAMERA")
            if (!addPermission(permissionsList, Manifest.permission.WRITE_EXTERNAL_STORAGE, activity))
                permissionsNeeded.add("WRITE_EXTERNAL_STORAGE")
            if (!addPermission(permissionsList, Manifest.permission.READ_EXTERNAL_STORAGE, activity))
                permissionsNeeded.add("READ_EXTERNAL_STORAGE")
            if (!addPermission(permissionsList, Manifest.permission.RECORD_AUDIO, activity))
                permissionsNeeded.add("RECORD_AUDIO")
            if (permissionsList.size > 0) {
                activity.requestPermissions(
                    permissionsList.toTypedArray(),
                    REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS
                )
            } else {
                permissionCall.onPermission(true)
            }
        }
    }


    fun checkForCameraWritePermissions(fragment: Fragment, permissionCall: PermissionCallback) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            permissionCall.onPermission(true)
        } else {
            val permissionsNeeded = ArrayList<String>()
            val permissionsList = ArrayList<String>()
            if (!addPermission(permissionsList, Manifest.permission.CAMERA, fragment.requireActivity()))
                permissionsNeeded.add("CAMERA")
            if (!addPermission(permissionsList, Manifest.permission.WRITE_EXTERNAL_STORAGE, fragment.requireActivity()))
                permissionsNeeded.add("WRITE_EXTERNAL_STORAGE")
            if (!addPermission(permissionsList, Manifest.permission.READ_EXTERNAL_STORAGE, fragment.requireActivity()))
                permissionsNeeded.add("READ_EXTERNAL_STORAGE")
            if (!addPermission(permissionsList, Manifest.permission.RECORD_AUDIO, fragment.requireActivity()))
                permissionsNeeded.add("RECORD_AUDIO")
            if (permissionsList.size > 0) {
                fragment.requestPermissions(
                    permissionsList.toTypedArray(),
                    REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS
                )
            } else {
                permissionCall.onPermission(true)
            }
        }
    }

}