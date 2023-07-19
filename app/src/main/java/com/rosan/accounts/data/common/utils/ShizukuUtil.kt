package com.rosan.accounts.data.common.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.ServiceManager
import com.rosan.accounts.data.common.cause.ShizukuNotWorkException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.sui.Sui

private suspend fun blockingRequestShizukuPermission() = callbackFlow {
    val requestCode = (Int.MIN_VALUE..Int.MAX_VALUE).random()
    val listener =
        Shizuku.OnRequestPermissionResultListener { _requestCode, grantResult ->
            if (_requestCode != requestCode) return@OnRequestPermissionResultListener
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED)
                trySend(Unit)
            else close(ShizukuNotWorkException("sui/shizuku permission denied"))
        }
    Shizuku.addRequestPermissionResultListener(listener)
    Shizuku.requestPermission(requestCode)
    awaitClose { Shizuku.removeRequestPermissionResultListener(listener) }
}.catch {
    throw if (it !is ShizukuNotWorkException) ShizukuNotWorkException(cause = it)
    else it
}.first()

suspend fun <T> requireShizukuPermissionGranted(context: Context, action: suspend () -> T): T {
    Sui.init(context.packageName)
    val binder = Shizuku.getBinder()
    if (binder == null || !binder.pingBinder())
        throw ShizukuNotWorkException("sui/shizuku isn't activated")
    if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED)
        blockingRequestShizukuPermission()
    return action()
}

fun shizukuBinder(name: String): IBinder =
    shizukuBinder(ServiceManager.getService(name))

fun shizukuBinder(binder: IBinder): IBinder = ShizukuBinderWrapper(binder)