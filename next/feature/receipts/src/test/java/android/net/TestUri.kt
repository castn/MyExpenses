package android.net

import android.os.Parcel

/**
 * A [Uri] for JVM tests: the stub of android.jar has a constructor visible only in this package and
 * throws in [Uri.parse]. Only [toString], [equals] and [hashCode] work.
 */
class TestUri(private val value: String) : Uri() {
    override fun toString() = value

    override fun equals(other: Any?) = other is TestUri && other.value == value

    override fun hashCode() = value.hashCode()

    override fun buildUpon(): Builder = unsupported()

    override fun getAuthority(): String = unsupported()

    override fun getEncodedAuthority(): String = unsupported()

    override fun getEncodedFragment(): String = unsupported()

    override fun getEncodedPath(): String = unsupported()

    override fun getEncodedQuery(): String = unsupported()

    override fun getEncodedSchemeSpecificPart(): String = unsupported()

    override fun getEncodedUserInfo(): String = unsupported()

    override fun getFragment(): String = unsupported()

    override fun getHost(): String = unsupported()

    override fun getLastPathSegment(): String = unsupported()

    override fun getPath(): String = unsupported()

    override fun getPathSegments(): List<String> = unsupported()

    override fun getPort(): Int = unsupported()

    override fun getQuery(): String = unsupported()

    override fun getScheme(): String = unsupported()

    override fun getSchemeSpecificPart(): String = unsupported()

    override fun getUserInfo(): String = unsupported()

    override fun isHierarchical(): Boolean = unsupported()

    override fun isRelative(): Boolean = unsupported()

    override fun describeContents(): Int = unsupported()

    override fun writeToParcel(dest: Parcel, flags: Int): Unit = unsupported()

    private fun unsupported(): Nothing = throw UnsupportedOperationException("Not in tests")
}
