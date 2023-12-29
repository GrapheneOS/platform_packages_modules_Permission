import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

fun PackageManager.getAppInfoOrNull(pkgName: String, flags: Long = 0L): ApplicationInfo? {
    return try {
        getApplicationInfo(pkgName, PackageManager.ApplicationInfoFlags.of(flags))
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}
