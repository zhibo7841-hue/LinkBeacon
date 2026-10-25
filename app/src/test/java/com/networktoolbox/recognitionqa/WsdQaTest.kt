package com.networktoolbox.recognitionqa

import com.networktoolbox.feature.lanscan.domain.recognition.*
import java.io.ByteArrayInputStream
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class WsdQaTest {
    private val request = "urn:uuid:11111111-1111-1111-1111-111111111111"
    private val endpoint = "urn:uuid:22222222-2222-2222-2222-222222222222"
    private val other = "urn:uuid:33333333-3333-3333-3333-333333333333"
    private val ip = "192.0.2.9"
    private val url = "http://$ip:5357/device"
    private fun envelope(action: String, body: String, id: String = request) = """<s:Envelope xmlns:s="${WsdProtocol.SOAP}" xmlns:a="${WsdProtocol.ADDRESS}" xmlns:d="${WsdProtocol.DISCOVERY}" xmlns:p="${WsdProtocol.PUB}" xmlns:w="${WsdProtocol.DPWS}" xmlns:x="${WsdProtocol.PNPX}" xmlns:m="${WsdProtocol.MEX}"><s:Header><a:Action>$action</a:Action><a:RelatesTo>$id</a:RelatesTo><a:MessageID>$other</a:MessageID><a:To>${WsdProtocol.ADDRESS}/role/anonymous</a:To></s:Header><s:Body>$body</s:Body></s:Envelope>"""
    private fun probe(id: String = request, type: String = "p:Computer", address: String = endpoint, xaddr: String = url) = envelope("${WsdProtocol.DISCOVERY}/ProbeMatches",
        "<d:ProbeMatches><d:ProbeMatch><a:EndpointReference><a:Address>$address</a:Address></a:EndpointReference><d:Types>$type</d:Types><d:XAddrs>$xaddr</d:XAddrs></d:ProbeMatch></d:ProbeMatches>", id)
    private fun metadata(id: String = request, type: String = "p:Computer", category: String = "Computers", address: String = endpoint) = envelope("${WsdProtocol.TRANSFER}/GetResponse", """<m:Metadata><m:MetadataSection Dialect="${WsdProtocol.DPWS}/Relationship"><w:Relationship Type="${WsdProtocol.DPWS}/host"><w:Host><a:EndpointReference><a:Address>$address</a:Address></a:EndpointReference><w:Types>$type</w:Types><p:Computer>TEST-PC\Workgroup:TEST</p:Computer></w:Host></w:Relationship></m:MetadataSection><m:MetadataSection Dialect="${WsdProtocol.DPWS}/ThisModel"><w:ThisModel><x:DeviceCategory>$category</x:DeviceCategory></w:ThisModel></m:MetadataSection></m:Metadata>""", id)
    private fun parse(xml: String) = WsdProtocol.probeMatches(xml.toByteArray(), request)
    private fun meta(xml: String) = WsdProtocol.metadata(xml.toByteArray(), request, endpoint)
    @Test fun standardProbeAndTransferRequestsHaveExpectedNamespaces() {
        val p = WsdProtocol.probe(request).toString(Charsets.UTF_8)
        assertTrue(p.contains("<d:Probe/>")); assertFalse(p.contains("<d:Types>"))
        assertTrue(WsdProtocol.get(request, endpoint).toString(Charsets.UTF_8).contains("${WsdProtocol.TRANSFER}/Get"))
    }
    @Test fun qualifiedComputerProbeType() { assertTrue(parse(probe())!!.single().computer) }
    @Test fun prefixIsNotIdentity() {
        assertTrue(parse(probe().replace("xmlns:p=", "xmlns:another=").replace("p:Computer", "another:Computer"))!!.single().computer)
        assertFalse(parse(probe().replace(WsdProtocol.PUB, "urn:fake"))!!.single().computer)
    }
    @Test fun defaultNamespaceQNameIsResolved() {
        assertTrue(parse(probe(type="Computer").replace("<d:Types>", "<d:Types xmlns=\"${WsdProtocol.PUB}\">"))!!.single().computer)
    }
    @Test fun unboundQNameAndBadHeaderAreRejected() {
        assertNull(parse(probe(type="bad:Computer")))
        assertNull(parse(probe(id=other)))
        assertNull(parse(probe().replace("/ProbeMatches</a:Action>", "/Hello</a:Action>")))
        assertNull(parse(probe().replace("<a:RelatesTo>", "<a:RelatesTo>$request</a:RelatesTo><a:RelatesTo>")))
        assertNull(parse(probe(address="http://example.com")))
    }
    @Test fun metadataComputerFieldsAndNameAreBoundToHostEndpoint() {
        val m = meta(metadata())!!
        assertEquals(setOf(WsdProof.HOST_COMPUTER_TYPE,WsdProof.PNPX_COMPUTERS),m.proofs)
        assertEquals("TEST-PC",m.name); assertFalse(m.otherCategory)
        assertNull(meta(metadata(address=other))); assertNull(meta(metadata(id=other)))
    }
    @Test fun categoryFromModelMustUsePnpxNamespaceAndExactToken() {
        assertEquals(setOf(WsdProof.PNPX_COMPUTERS),meta(metadata(type="w:Device"))!!.proofs)
        assertTrue(meta(metadata(type="w:Device",category="computers"))!!.proofs.isEmpty())
        assertTrue(meta(metadata(type="w:Device").replace(WsdProtocol.PNPX,"urn:fake"))!!.proofs.isEmpty())
    }
    @Test fun hostedServiceOrFriendlyNameIsNotDeviceCategory() {
        assertNull(meta(metadata().replace("w:Host>","w:Hosted>")))
        assertTrue(meta(metadata(type="w:Device",category="Devices"))!!.proofs.isEmpty())
    }
    @Test fun arbitraryNamesDoNotBecomeNetbiosNames() {
        assertNull(meta(metadata().replace("TEST-PC\\Workgroup:TEST","Windows Server Computer"))!!.name)
        assertEquals("TEST-PC",meta(metadata().replace("\\Workgroup:TEST","/Domain:TEST"))!!.name)
        assertEquals("TEST-PC",meta(metadata().replace("\\Workgroup:TEST","/Workgroup:TEST"))!!.name)
    }
    @Test fun externalEntitiesAndMalformedInputAreRejected() {
        val bad=listOf("<!DOCTYPE s:Envelope SYSTEM 'http://example.com/x'>"+probe(),
            "<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///secret'>]>"+probe(),
            probe().replace("p:Computer", "&ext;"), "<broken", probe()+"<extra/>", "\u0000"+probe())
        bad.forEach { assertNull(parse(it)) }
    }
    @Test fun sizeDepthNodeAndTextCaps() {
        assertNull(parse(" ".repeat(WsdProtocol.MAX_UDP+1)))
        assertNull(parse(probe().replace("<d:Types>","<d:Types>"+"<a:x>".repeat(25)).replace("</d:Types>","</a:x>".repeat(25)+"</d:Types>")))
        assertNull(parse(probe().replace("<d:Types>","<d:Types>"+"x".repeat(4097))))
        assertNull(parse(probe().replace("<d:Types>","<d:Types>"+"<a:x/>".repeat(513))))
    }
    @Test fun urlAllowlistIsLiteralAndNarrow() {
        assertEquals(url,WsdProtocol.safeXaddr(url,ip))
        listOf("https://$ip:5358/device","http://example.com:5357/device","http://192.0.2.10:5357/device",
            "http://$ip:80/device","http://u@$ip:5357/device","http://$ip:5357/device?q=1",
            "http://$ip:5357/device#x","http://$ip:5357/../x","http://$ip:5357/%2e/x",
            "http://$ip:5357/\r\nx","http://$ip:5357/\\evil","file:///secret").forEach { assertNull(WsdProtocol.safeXaddr(it,ip)) }
    }
    private class FakeIo(val answer: (String)->List<WsdDatagram>,val metadataAnswer:(String)->String):WsdIo {
        var sent=0;var posts=0;var closed=false;var requestId="";var queue= mutableListOf<WsdDatagram>()
        var onReceive:()->Unit={};var onMetadata:()->Unit={}
        override fun send(bytes:ByteArray){sent++;requestId=Regex("<a:MessageID>(.*?)</a:MessageID>").find(bytes.toString(Charsets.UTF_8))!!.groupValues[1];queue=answer(requestId).toMutableList()}
        override fun receive(timeoutMs:Int):WsdDatagram {onReceive();return if(queue.isEmpty())throw SocketTimeoutException() else queue.removeAt(0)}
        override fun metadata(url:String,body:ByteArray):ByteArray {posts++;onMetadata();val id=Regex("<a:MessageID>(.*?)</a:MessageID>").find(body.toString(Charsets.UTF_8))!!.groupValues[1];return metadataAnswer(id).toByteArray()}
        override fun close(){closed=true}
    }
    private fun io(reply:(String)->List<WsdDatagram> = {listOf(WsdDatagram(ip,3702,probe(it).toByteArray()))},meta:(String)->String={metadata(it)})=FakeIo(reply,meta)
    private var used=false
    private val once get()=WsdOnce({used},{used=true;true})
    private fun run(io:FakeIo,gate:QaGate=QaGate{true},raw:(String,String,ByteArray)->Unit={_,_,_->}) =
        WsdExchange().run(io,mapOf(ip to "T09"),gate,once,{0L},raw)
    @Test fun acceptedProbeAndOneMetadataProduceSeparateComputerEvidence() {
        val io=io(); val result=run(io)
        assertEquals(1,io.sent);assertEquals(1,io.posts);assertTrue(io.closed)
        assertEquals(3,result.evidence.single().wsdProofs.size)
        assertEquals("TEST-PC",QaProjection.name(null,null,result.evidence))
    }
    @Test fun duplicateResponsesDoNotCauseRepeatedRequests() {
        val io=io({id->List(4){WsdDatagram(ip,3702,probe(id).toByteArray())}});run(io);assertEquals(1,io.posts)
    }
    @Test fun nonAllowlistedResponsesAreNotParsedOrLogged() {
        var logs=0
        val result=run(io({listOf(WsdDatagram("192.0.2.10",3702,"private".toByteArray()))}),raw={_,_,_->logs++})
        assertEquals(0,logs);assertEquals("NO_RESPONSE",result.evidence.single().status)
    }
    @Test fun wrongSourcePortAndUnrelatedResponseCannotTriggerMetadata() {
        val io=io({listOf(WsdDatagram(ip,1234,probe(it).toByteArray()),WsdDatagram(ip,3702,probe(other).toByteArray()))})
        run(io);assertEquals(0,io.posts)
    }
    @Test fun unsafeXaddrIsNotFetchedButExplicitProbeClaimRemainsQualified() {
        val io=io({listOf(WsdDatagram(ip,3702,probe(it,xaddr="http://example.com:5357/x").toByteArray()))})
        val r=run(io);assertEquals(0,io.posts);assertEquals("XADDR_REJECTED",r.evidence.single().status)
        assertEquals(setOf(WsdProof.PROBE_COMPUTER_TYPE),r.evidence.single().wsdProofs)
    }
    @Test fun endpointConflictIsNotLastResponseWins() {
        val io=io({listOf(WsdDatagram(ip,3702,probe(it).toByteArray()),WsdDatagram(ip,3702,probe(it,address=other).toByteArray()))})
        val r=run(io);assertEquals(0,io.posts);assertEquals("ASSOCIATION_CONFLICT",r.evidence.single().status)
    }
    @Test fun metadataEndpointMismatchCannotProvideProofOrName() {
        val r=run(io({listOf(WsdDatagram(ip,3702,probe(it,type="w:Device").toByteArray()))},{metadata(it,address=other)}))
        assertTrue(r.evidence.single().wsdProofs.isEmpty());assertTrue(r.evidence.single().names.isEmpty())
    }
    @Test fun noResponseAndGenericResponseDoNotUpgradeSuspectedComputer() {
        val nb=QaEvidence("T09",QaSource.NBSTAT,"NAME",listOf(PtrName("TEST-PC",0)))
        for(e in listOf(QaEvidence("T09",QaSource.WSD,"NO_RESPONSE"),QaEvidence("T09",QaSource.WSD,"GENERIC_SERVICE"))){
            assertEquals(QaCategoryCertainty.POSSIBLE,QaCategoryMapper.map(LanRecognitionResult(LanRecognitionStatus.CURRENT),listOf(nb,e)).certainty)
        }
    }
    @Test fun independentWsdProofUpgradesCandidateWithoutAddingDeviceOrGuessingOS() {
        val e=QaEvidence("T09",QaSource.WSD,"COMPUTER_SELF_REPORT",wsdProofs=setOf(WsdProof.PROBE_COMPUTER_TYPE))
        val nb=QaEvidence("T09",QaSource.NBSTAT,"NAME",listOf(PtrName("TEST-PC",0)))
        val r=QaCategoryMapper.map(LanRecognitionResult(LanRecognitionStatus.CURRENT),listOf(nb,e))
        assertEquals(QaDeviceCategory.COMPUTER,r.category);assertEquals(QaCategoryCertainty.CONFIRMED,r.certainty)
        assertEquals("Computer",QaCategoryPresentation.label(r))
    }
    @Test fun conflictingMetadataDoesNotPromoteCategory() {
        val r=run(io(meta={metadata(it,category="Printers")}))
        assertEquals("CATEGORY_CONFLICT",r.evidence.single().status)
        assertEquals(QaCategoryCertainty.UNKNOWN,QaCategoryMapper.map(LanRecognitionResult(LanRecognitionStatus.CURRENT),r.evidence).certainty)
    }
    @Test fun rescanAndNewOwnerCannotRearmPersistentBudget() {
        val first=io();run(first);val second=io();assertEquals("BUDGET_SPENT",run(second).status)
        assertEquals(0,second.sent);assertEquals(0,second.posts);assertTrue(second.closed)
    }
    @Test fun failedPersistenceDoesNotAdmitProbe() {
        val io=io();val r=WsdExchange().run(io,mapOf(ip to "T09"),QaGate{true},WsdOnce({false},{false}),{0L})
        assertEquals("BUDGET_SPENT",r.status);assertEquals(0,io.sent)
    }
    @Test fun cancelDuringReceiveClosesResourcesAndSuppressesMetadata() {
        val gate=QaGate{true};val io=io();gate.register(io);io.onReceive={gate.close()}
        assertTrue(runCatching {run(io,gate)}.exceptionOrNull() is CancellationException)
        assertEquals(0,io.posts);assertTrue(io.closed);assertEquals(0,gate.resourceCount())
        val retry=io();assertEquals("BUDGET_SPENT",run(retry).status)
    }
    @Test fun networkInvalidationRejectsLateMetadata() {
        var valid=true;val gate=QaGate{valid};val io=io();io.onMetadata={valid=false}
        assertTrue(runCatching {run(io,gate)}.exceptionOrNull() is CancellationException);assertTrue(io.closed)
    }
    @Test fun invalidSessionDoesNotConsumeOrSend() {
        val gate=QaGate{false};val io=io()
        assertTrue(runCatching {run(io,gate)}.exceptionOrNull() is CancellationException)
        assertFalse(used);assertEquals(0,io.sent);assertTrue(io.closed)
    }
    @Test fun sameEndpointOnDifferentTargetsIsRejectedForBoth() {
        val second="192.0.2.10"
        val io=io({id->listOf(WsdDatagram(ip,3702,probe(id).toByteArray()),WsdDatagram(second,3702,probe(id,xaddr="http://$second:5357/device").toByteArray()))})
        val r=WsdExchange().run(io,mapOf(ip to "T09",second to "T05"),QaGate{true},once,{0L})
        assertEquals(0,io.posts);assertTrue(r.evidence.all {it.status=="ASSOCIATION_CONFLICT" && it.wsdProofs.isEmpty()})
    }
    @Test fun wsdNameDoesNotOverwriteCustomOrBaselineNameAndConflictStaysInDetails() {
        val e=QaEvidence("T09",QaSource.WSD,"METADATA_ACCEPTED",listOf(PtrName("WSD-NAME",0)))
        assertEquals("Custom",QaProjection.name("Custom","Baseline",listOf(e)))
        assertEquals("Baseline",QaProjection.name(null,"Baseline",listOf(e)))
        assertNull(QaProjection.name(null,null,listOf(e,QaEvidence("T09",QaSource.NBSTAT,"NAME",listOf(PtrName("OTHER",0))))))
    }
    @Test fun staleWsdProofAndSshAloneNeverBecomeComputer() {
        val e=QaEvidence("T06",QaSource.WSD,"COMPUTER_SELF_REPORT",wsdProofs=setOf(WsdProof.PROBE_COMPUTER_TYPE))
        val stale=LanRecognitionResult(LanRecognitionStatus.NOT_CURRENT)
        assertEquals(QaCategoryCertainty.UNKNOWN,QaCategoryMapper.map(stale,listOf(e)).certainty)
        val ssh=QaEvidence("T06",QaSource.SSH,"SSH_IDENTIFIED",software="OpenSSH_9.6")
        assertEquals(QaCategoryCertainty.UNKNOWN,QaCategoryMapper.map(LanRecognitionResult(LanRecognitionStatus.CURRENT),listOf(ssh)).certainty)
    }
    private fun http(headers:String,body:String)=WsdHttp.read(ByteArrayInputStream(("HTTP/1.1 200 OK\r\nContent-Type: application/soap+xml\r\n$headers\r\n$body").toByteArray())){}
    @Test fun boundedHttpContentLengthAndChunked() {
        assertEquals("abc",http("Content-Length: 3\r\n","abc").toString(Charsets.UTF_8))
        assertEquals("abc",http("Transfer-Encoding: chunked\r\n","3\r\nabc\r\n0\r\n\r\n").toString(Charsets.UTF_8))
    }
    @Test fun httpRejectsRedirectCompressionOversizeAndAmbiguousFraming() {
        assertTrue(runCatching {WsdHttp.read(ByteArrayInputStream("HTTP/1.1 302 Found\r\nLocation: http://example.com\r\n\r\n".toByteArray())){}}.isFailure)
        listOf("Content-Length: 999999\r\n","Content-Length: 3\r\nContent-Length: 3\r\n",
            "Content-Encoding: gzip\r\n","Transfer-Encoding: chunked\r\nContent-Length: 3\r\n").forEach {assertTrue(runCatching {http(it,"abc")}.isFailure)}
    }
}
