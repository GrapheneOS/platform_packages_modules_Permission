package android.safetycenter.testers

import android.os.Parcel
import android.os.Parcelable
import com.google.common.truth.Truth.assertThat

/** Collection of functions to test [Parcelable] objects */
object ParcelableTester {
    /**
     * Asserts that writing a [Parcelable] object to a [Parcel] and creating an object from that
     * [Parcel] returns an object that is equal to the original [Parcelable] object.
     */
    fun <T : Parcelable> assertThatRoundTripReturnsOriginal(
        parcelable: T,
        creator: Parcelable.Creator<T>
    ) {
        val parcel: Parcel = Parcel.obtain()
        parcelable.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        val parcelableFromParcel: T = creator.createFromParcel(parcel)
        parcel.recycle()

        assertThat(parcelableFromParcel).isEqualTo(parcelable)
    }
}