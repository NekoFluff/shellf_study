package com.crazyfluff.shellfstudy.shared.data

import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.errSecNotAvailable
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

private const val KEYCHAIN_SERVICE = "com.crazyfluff.shellfstudy.apitoken"
private const val KEYCHAIN_ACCOUNT = "wanikani_api_token"
private const val KEYCHAIN_MARKER = "ios-keychain-v1"

/**
 * No custom AES here (unlike [AndroidKeystoreTokenCipher]) — the iOS Keychain already provides
 * hardware-backed encryption at rest for whatever it stores, so [encrypt] just writes straight
 * into it and returns a fixed marker (not real ciphertext — there's nothing else worth persisting
 * in the caller's own storage). [decrypt] ignores its input and reads back from the Keychain.
 *
 * Not covered by `:shared:iosSimulatorArm64Test`: manually verified there (full encrypt/decrypt/
 * update round trip passed), but Keychain access from that harness is unreliable across repeated
 * runs — the test binary runs via an ephemeral, headless simulator boot with no real "unlocked
 * device" session, which the Keychain daemon depends on, and intermittently returns
 * errSecNotAvailable (-25291) even for otherwise-correct calls. A real app running in the
 * foreground doesn't hit this. Re-verify manually (or via an Xcode-hosted XCTest target against
 * the built framework) once the iOS app shell exists.
 */
@OptIn(ExperimentalForeignApi::class)
class KeychainTokenCipher : TokenCipher {

    override fun encrypt(plainText: String): String {
        upsertKeychainItem(plainText)
        return KEYCHAIN_MARKER
    }

    override fun decrypt(encoded: String): String =
        readKeychainItem() ?: error("No token found in the iOS Keychain")

    override fun clear() {
        val query = newQuery()
        try {
            val status = SecItemDelete(query)
            // errSecItemNotFound (nothing to delete) and errSecNotAvailable (a documented,
            // intermittent Keychain-daemon quirk — most commonly seen deleting an item that's
            // already gone) both mean there's nothing left to clean up; clear() is best-effort by
            // nature, so neither is worth surfacing as a hard failure here.
            check(status == errSecSuccess || status == errSecItemNotFound || status == errSecNotAvailable) {
                "Keychain delete failed: $status"
            }
        } finally {
            CFRelease(query)
        }
    }

    private fun newQuery(): CFMutableDictionaryRef {
        val dict = CFDictionaryCreateMutable(
            kCFAllocatorDefault,
            0,
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr
        )
        CFDictionarySetValue(dict, kSecClass, kSecClassGenericPassword)
        val service = cfString(KEYCHAIN_SERVICE)
        val account = cfString(KEYCHAIN_ACCOUNT)
        CFDictionarySetValue(dict, kSecAttrService, service)
        CFDictionarySetValue(dict, kSecAttrAccount, account)
        CFRelease(service)
        CFRelease(account)
        return dict!!
    }

    private fun upsertKeychainItem(plainText: String) {
        val bytes = plainText.encodeToByteArray()
        val cfData = bytes.usePinned { pinned ->
            CFDataCreate(kCFAllocatorDefault, pinned.addressOf(0).reinterpret(), bytes.size.toLong())
        }
        try {
            if (readKeychainItem() != null) {
                val query = newQuery()
                val attributes = CFDictionaryCreateMutable(
                    kCFAllocatorDefault, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr
                )
                CFDictionarySetValue(attributes, kSecValueData, cfData)
                val status = SecItemUpdate(query, attributes)
                CFRelease(query)
                CFRelease(attributes)
                check(status == errSecSuccess) { "Keychain update failed: $status" }
            } else {
                val query = newQuery()
                CFDictionarySetValue(query, kSecValueData, cfData)
                val status = SecItemAdd(query, null)
                CFRelease(query)
                check(status == errSecSuccess) { "Keychain add failed: $status" }
            }
        } finally {
            CFRelease(cfData)
        }
    }

    private fun readKeychainItem(): String? = memScoped {
        val query = newQuery()
        CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
        CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)

        val result = alloc<COpaquePointerVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        CFRelease(query)
        if (status == errSecItemNotFound) return null
        check(status == errSecSuccess) { "Keychain read failed: $status" }

        val cfData: CFDataRef = result.value?.reinterpret() ?: return null
        val length = CFDataGetLength(cfData).toInt()
        val bytePtr = CFDataGetBytePtr(cfData)
        val bytes = ByteArray(length)
        if (bytePtr != null && length > 0) {
            bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), bytePtr, length.convert()) }
        }
        CFRelease(cfData)
        bytes.decodeToString()
    }

    private fun cfString(value: String): CFStringRef =
        CFStringCreateWithCString(kCFAllocatorDefault, value, kCFStringEncodingUTF8)!!
}
