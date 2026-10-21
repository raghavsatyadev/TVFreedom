package com.gathin.tvgo.tv

import android.content.Context
import java.io.File

/** [TvLink] over libadb, with this phone's adb key from [keysDir]. */
class AndroidTvLink(ctx: Context, keysDir: File) : TvLink {
    private val discovery = Discovery(ctx)
    private val keys by lazy { AdbKeys(keysDir) }

    override fun tv(host: String, port: Int): Tv = AdbTv(host, port, keys)
    override suspend fun pair(host: String, port: Int, code: String) = AdbTv.pair(host, port, code, keys)
    override fun localIp() = discovery.localIp()
    override suspend fun find() = discovery.find()
    override suspend fun mdns() = discovery.mdns()
}
