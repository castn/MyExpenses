package org.totschnig.myexpenses.util

import android.Manifest
import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import androidx.annotation.IntDef
import androidx.annotation.RequiresApi
import com.vmadalin.easypermissions.EasyPermissions
import org.totschnig.myexpenses.R
import java.io.File


object PermissionHelper {
    @IntDef(
        PERMISSIONS_REQUEST_NOTIFICATIONS_SYNC,
        PERMISSIONS_REQUEST_NOTIFICATIONS_AUTO_BACKUP, PERMISSIONS_REQUEST_STORAGE
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class PermissionRequestCode

    const val PERMISSIONS_REQUEST_NOTIFICATIONS_SYNC = 3
    const val PERMISSIONS_REQUEST_NOTIFICATIONS_AUTO_BACKUP = 5
    const val PERMISSIONS_REQUEST_STORAGE = 6

    @JvmStatic
    fun canReadUri(uri: Uri, context: Context): Boolean {
        if ("file" == uri.scheme) {
            uri.path?.let {
                return with(File(it)) {
                    exists() && canRead()
                }
            }
            return false
        }
        return AppDirHelper.fileProviderAuthority == uri.authority || context.checkUriPermission(
            uri, Binder.getCallingPid(), Binder.getCallingUid(),
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    fun getRationale(context: Context, @PermissionRequestCode requestCode: Int): String =
        when (requestCode) {
            PERMISSIONS_REQUEST_NOTIFICATIONS_SYNC -> context.getString(R.string.notifications_permission_required_sync)
            PERMISSIONS_REQUEST_NOTIFICATIONS_AUTO_BACKUP ->
                context.getString(R.string.notifications_permission_required_auto_backup)
            PERMISSIONS_REQUEST_STORAGE ->
                "In order to save images to external storage you must grant Storage Permission"
            else -> throw IllegalArgumentException("Unexpected requestCode: $requestCode")
        }

    enum class PermissionGroup(
        val androidPermissions: List<String>
    ) {
        STORAGE(
            listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        ),
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        NOTIFICATION(
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        )
        ;

        /**
         * @return true if all of our [.androidPermissions] are granted
         */
        fun hasPermission(context: Context): Boolean {
            return EasyPermissions.hasPermissions(context, *androidPermissions.toTypedArray())
        }
    }
}
