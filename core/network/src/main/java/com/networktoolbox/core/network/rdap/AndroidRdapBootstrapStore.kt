package com.networktoolbox.core.network.rdap

import android.content.Context
import android.util.AtomicFile
import java.io.*

/** Only public IANA directory bytes are persisted. No queried domain or response is stored. */
class AndroidRdapBootstrapStore(context: Context, private val dataset:String="dns") : RdapBootstrapStore {
    init {require(dataset in setOf("dns","ipv4","ipv6"))}
    private val app=context.applicationContext
    private val cache=AtomicFile(File(app.filesDir,if(dataset=="dns") "rdap-bootstrap-v1.bin" else "rdap-$dataset-bootstrap-v1.bin"))
    override fun load(): List<RdapBootstrapSnapshot> = buildList {
        runCatching {cache.openRead().use {file ->
            val stream=DataInputStream(file);val at=stream.readLong()
            add(RdapBootstrapSnapshot(readRdapBody(stream,RdapLimits.BOOTSTRAP_BYTES),at,RdapBootstrapSource.CACHE))
        }}
        val meta=BoundedRdapJson.parse(app.assets.open("rdap/$dataset.meta.json").use {readRdapBody(it,8192)},8192)
        val at=(meta["fetchedAtEpochMs"] as? BoundedRdapJson.Number)?.raw?.toLongOrNull()
            ?: throw RdapFailure(RdapStatus.BOOTSTRAP_UNAVAILABLE)
        val bytes=app.assets.open("rdap/$dataset.json").use {readRdapBody(it,RdapLimits.BOOTSTRAP_BYTES)}
        val hash=java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}
        if(hash!=meta.str("sha256")) throw RdapFailure(RdapStatus.BOOTSTRAP_UNAVAILABLE)
        add(RdapBootstrapSnapshot(bytes,at,RdapBootstrapSource.BUNDLED))
    }
    override fun save(bytes: ByteArray,fetchedAt: Long) {
        require(bytes.size<=RdapLimits.BOOTSTRAP_BYTES)
        val output=cache.startWrite()
        try {val data=DataOutputStream(output);data.writeLong(fetchedAt);data.write(bytes);data.flush();cache.finishWrite(output)}
        catch(e: Exception) {cache.failWrite(output);throw e}
    }
}

fun androidRdapLookup(context: Context,observer:RdapObserver=RdapObserver.NONE): RunRdapLookup = DefaultRdapLookup(
    AndroidRdapTransport(context,observer),AndroidRdapBootstrapStore(context),RdapResponseParser(AndroidRdapDomainNormalizer()),observer=observer,ipStores={family->AndroidRdapBootstrapStore(context,family)})
