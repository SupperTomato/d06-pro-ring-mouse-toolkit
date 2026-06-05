package com.d06.sdk.input

import android.os.Build
import android.view.InputDevice

data class D06InputConfig(
    val detectMousepadTap: Boolean = false,
    val acceptedNames: Set<String> = setOf("D06 Pro", "D06"),
    val vendorId: Int = D06_VENDOR_ID,
    val productId: Int = D06_PRODUCT_ID
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

        val idMatches = info.vendorId == config.vendorId && info.productId == config.productId
        return nameMatches || idMatches
    }
}

const val D06_VENDOR_ID: Int = 0x248A
const val D06_PRODUCT_ID: Int = 0x0101
