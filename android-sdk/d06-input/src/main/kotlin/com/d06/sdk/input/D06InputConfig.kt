package com.d06.sdk.input

import android.os.Build
import android.view.InputDevice

data class D06InputConfig(
    val detectMousepadTap: Boolean = false,
    val acceptedNames: Set<String> = setOf("D06 Pro", "D06", "TK Wireless Receiver"),
    val vendorId: Int = D06_VENDOR_ID,
    val productId: Int = D06_BLUETOOTH_PRODUCT_ID,
    val acceptedDeviceIds: Set<D06InputDeviceId> = D06_DEVICE_IDS + D06InputDeviceId(vendorId, productId)
)

data class D06InputDeviceId(
    val vendorId: Int,
    val productId: Int
)

data class D06InputDeviceInfo(
    val name: String?,
    val vendorId: Int?,
    val productId: Int?
) {
    companion object {
        fun from(device: InputDevice): D06InputDeviceInfo {
            return D06InputDeviceInfo(
                name = device.name,
                vendorId = if (Build.VERSION.SDK_INT >= 19) device.vendorId else null,
                productId = if (Build.VERSION.SDK_INT >= 19) device.productId else null
            )
        }
    }
}

class D06InputDeviceMatcher(
    private val config: D06InputConfig = D06InputConfig()
) {
    fun matches(info: D06InputDeviceInfo): Boolean {
        val nameMatches = info.name?.let { name ->
            config.acceptedNames.any { accepted -> name.contains(accepted, ignoreCase = true) }
        } ?: false

        val idMatches = info.vendorId != null &&
            info.productId != null &&
            D06InputDeviceId(info.vendorId, info.productId) in config.acceptedDeviceIds
        return nameMatches || idMatches
    }
}

const val D06_VENDOR_ID: Int = 0x248A
const val D06_BLUETOOTH_PRODUCT_ID: Int = 0x0101
const val D06_USB_RECEIVER_PRODUCT_ID: Int = 0x0401
@Deprecated("Use D06_BLUETOOTH_PRODUCT_ID or D06_USB_RECEIVER_PRODUCT_ID for path-specific matching.")
const val D06_PRODUCT_ID: Int = D06_BLUETOOTH_PRODUCT_ID
val D06_DEVICE_IDS: Set<D06InputDeviceId> = setOf(
    D06InputDeviceId(D06_VENDOR_ID, D06_BLUETOOTH_PRODUCT_ID),
    D06InputDeviceId(D06_VENDOR_ID, D06_USB_RECEIVER_PRODUCT_ID)
)
