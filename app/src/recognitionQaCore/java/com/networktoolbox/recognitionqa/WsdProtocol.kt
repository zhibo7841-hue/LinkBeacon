package com.networktoolbox.recognitionqa

import java.io.ByteArrayInputStream
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

enum class WsdProof { PROBE_COMPUTER_TYPE, HOST_COMPUTER_TYPE, PNPX_COMPUTERS }
data class WsdMatch(val endpoint: String, val xaddr: String?, val computer: Boolean)
data class WsdMetadata(val proofs: Set<WsdProof>, val name: String?, val otherCategory: Boolean = false)

/** A bounded, namespace-aware subset of DPWS 2006/02 and WS-Discovery 2005/04.
 * No XML URL, schema, entity or metadata reference is ever dereferenced here. */
object WsdProtocol {
    const val SOAP = "http://www.w3.org/2003/05/soap-envelope"
    const val ADDRESS = "http://schemas.xmlsoap.org/ws/2004/08/addressing"
    const val DISCOVERY = "http://schemas.xmlsoap.org/ws/2005/04/discovery"
    const val DPWS = "http://schemas.xmlsoap.org/ws/2006/02/devprof"
    const val PUB = "http://schemas.microsoft.com/windows/pub/2005/07"
    const val PNPX = "http://schemas.microsoft.com/windows/pnpx/2005/10"
    const val TRANSFER = "http://schemas.xmlsoap.org/ws/2004/09/transfer"
    const val MEX = "http://schemas.xmlsoap.org/ws/2004/09/mex"
    const val MAX_UDP = 32768
    const val MAX_XML = 65536
    fun messageId() = "urn:uuid:${UUID.randomUUID()}"
    private fun uuid(value: String) = value.matches(Regex("urn:uuid:[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"))
    private fun envelope(id: String, to: String, action: String, body: String): ByteArray {
        require(uuid(id) && (uuid(to) || to == "urn:schemas-xmlsoap-org:ws:2005:04:discovery"))
        return ("""<?xml version="1.0" encoding="UTF-8"?><s:Envelope xmlns:s="$SOAP" xmlns:a="$ADDRESS" xmlns:d="$DISCOVERY"><s:Header><a:MessageID>$id</a:MessageID><a:To>$to</a:To><a:Action>$action</a:Action><a:ReplyTo><a:Address>$ADDRESS/role/anonymous</a:Address></a:ReplyTo></s:Header><s:Body>$body</s:Body></s:Envelope>""").toByteArray()
    }
    fun probe(id: String) = envelope(id, "urn:schemas-xmlsoap-org:ws:2005:04:discovery", "$DISCOVERY/Probe", "<d:Probe/>")
    fun get(id: String, endpoint: String) = envelope(id, endpoint, "$TRANSFER/Get", "")

    /** No name resolution, credentials, redirects, external host, alternate IP encoding or query. */
    fun safeXaddr(value: String, sourceIp: String): String? = runCatching {
        require(value.length in 1..512 && value.none { it <= ' ' || it == '\\' || it == '%' })
        val u = URI(value)
        require(u.scheme == "http" && u.host == sourceIp && u.port == 5357)
        require(u.rawAuthority == "$sourceIp:5357" && u.userInfo == null && u.rawQuery == null && u.rawFragment == null)
        require(u.rawPath.matches(Regex("/[A-Za-z0-9._~/-]+")) && u.rawPath.split('/').none { it == "." || it == ".." })
        value
    }.getOrNull()

    fun probeMatches(bytes: ByteArray, requestId: String): List<WsdMatch>? = runCatching {
        require(bytes.size <= MAX_UDP)
        val root = xml(bytes)
        val body = response(root, "$DISCOVERY/ProbeMatches", requestId)
        val matches = body.one(DISCOVERY, "ProbeMatches").children(DISCOVERY, "ProbeMatch")
        require(matches.size in 1..8)
        matches.map { match ->
            val endpoint = match.one(ADDRESS, "EndpointReference").one(ADDRESS, "Address").value()
            require(uuid(endpoint))
            val types = match.optional(DISCOVERY, "Types")
            val xaddr = match.optional(DISCOVERY, "XAddrs")?.value()?.takeIf { it.isNotBlank() }
            WsdMatch(endpoint, xaddr, types?.qnames()?.contains(PUB to "Computer") == true)
        }
    }.getOrNull()

    fun metadata(bytes: ByteArray, requestId: String, endpoint: String): WsdMetadata? = runCatching {
        val body = response(xml(bytes), "$TRANSFER/GetResponse", requestId)
        val sections = body.one(MEX, "Metadata").children(MEX, "MetadataSection")
        require(sections.size in 1..16)
        val hosts = sections.filter { it.attrs["Dialect"] == "$DPWS/Relationship" }
            .map { it.one(DPWS, "Relationship") }.onEach { require(it.attrs["Type"] == "$DPWS/host") }
            .map { it.one(DPWS, "Host") }
        require(hosts.size == 1)
        val host = hosts.single()
        require(host.one(ADDRESS, "EndpointReference").one(ADDRESS, "Address").value() == endpoint)
        val proofs = mutableSetOf<WsdProof>()
        if (host.optional(DPWS, "Types")?.qnames()?.contains(PUB to "Computer") == true) proofs += WsdProof.HOST_COMPUTER_TYPE
        val categories = sections.filter { it.attrs["Dialect"] == "$DPWS/ThisModel" }
            .flatMap { it.one(DPWS, "ThisModel").children(PNPX, "DeviceCategory") }
            .flatMap { it.value().split(Regex("\\s+")) }.filter { it.isNotBlank() }.toSet()
        if (categories == setOf("Computers")) proofs += WsdProof.PNPX_COMPUTERS
        val text = host.optional(PUB, "Computer")?.value()
        val name = text?.let { netbiosName(it) }
        WsdMetadata(proofs, name, categories.any { it != "Computers" })
    }.getOrNull()

    private fun netbiosName(text: String): String? {
        // MS-PBSD documents backslash Workgroup; the captured Windows response uses slash.
        // Both are explicit publication-field formats, never hostname-keyword classification.
        val m = Regex("^([A-Za-z0-9][A-Za-z0-9_-]{0,14})(?:/Domain:|[/\\\\]Workgroup:)[^\\p{Cntrl}]{1,128}$").matchEntire(text) ?: return null
        return m.groupValues[1]
    }
    private fun response(root: Node, action: String, relates: String): Node {
        require(root.key == SOAP to "Envelope")
        val header = root.one(SOAP, "Header")
        require(header.one(ADDRESS, "Action").value() == action)
        require(header.one(ADDRESS, "RelatesTo").value() == relates)
        require(uuid(header.one(ADDRESS, "MessageID").value()))
        require(header.one(ADDRESS, "To").value() == "$ADDRESS/role/anonymous")
        return root.one(SOAP, "Body")
    }
    private data class Node(val key: Pair<String, String>, val ns: Map<String, String>, val attrs: Map<String, String>,
        val nodes: MutableList<Node> = mutableListOf(), val text: StringBuilder = StringBuilder()) {
        fun children(ns: String, name: String) = nodes.filter { it.key == ns to name }
        fun one(ns: String, name: String) = children(ns, name).single()
        fun optional(ns: String, name: String): Node? = children(ns, name).let { require(it.size <= 1); it.singleOrNull() }
        fun value(): String { require(nodes.isEmpty()); return text.toString().trim() }
        fun qnames(): Set<Pair<String, String>> = value().split(Regex("\\s+")).filter { it.isNotEmpty() }.map {
            require(it.matches(Regex("(?:[A-Za-z_][A-Za-z0-9_.-]*:)?[A-Za-z_][A-Za-z0-9_.-]*")))
            val p = it.split(':'); require(p.size <= 2)
            requireNotNull(ns[if (p.size == 2) p[0] else ""]) to p.last()
        }.toSet()
    }
    private fun xml(bytes: ByteArray): Node {
        require(bytes.size in 1..MAX_XML)
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
        require(!text.contains('\u0000') && !text.contains("<!DOCTYPE", true) && !text.contains("<!ENTITY", true))
        val pending = mutableMapOf<String, String>(); val stack = mutableListOf<Node>(); var root: Node? = null; var count = 0
        val handler = object : DefaultHandler() {
            override fun startPrefixMapping(prefix: String, uri: String) { pending[prefix] = uri }
            override fun startElement(uri: String, local: String, qName: String, attributes: Attributes) {
                require(++count <= 512 && stack.size < 24)
                val ns = (stack.lastOrNull()?.ns.orEmpty() + pending); pending.clear()
                val attrs = (0 until attributes.length).filter { attributes.getURI(it).isEmpty() }
                    .associate { attributes.getLocalName(it) to attributes.getValue(it) }
                val node = Node(uri to local, ns, attrs)
                if (stack.isEmpty()) { require(root == null); root = node } else stack.last().nodes += node
                stack += node
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (stack.isNotEmpty()) { require(stack.last().text.length + length <= 4096); stack.last().text.append(ch, start, length) }
            }
            override fun endElement(uri: String, local: String, qName: String) { stack.removeAt(stack.lastIndex) }
            override fun resolveEntity(publicId: String?, systemId: String?): org.xml.sax.InputSource = throw SAXException("External entity rejected")
            override fun error(e: org.xml.sax.SAXParseException) { throw e }
            override fun fatalError(e: org.xml.sax.SAXParseException) { throw e }
        }
        val reader = SAXParserFactory.newInstance().apply { isNamespaceAware = true; isValidating = false }.newSAXParser().xmlReader
        // Android and JVM differ in recognized features; lexical denial + rejecting resolver are unconditional.
        listOf("http://xml.org/sax/features/external-general-entities", "http://xml.org/sax/features/external-parameter-entities",
            "http://apache.org/xml/features/nonvalidating/load-external-dtd").forEach { runCatching { reader.setFeature(it, false) } }
        reader.contentHandler = handler; reader.entityResolver = handler; reader.errorHandler = handler
        reader.parse(org.xml.sax.InputSource(ByteArrayInputStream(bytes)))
        return requireNotNull(root)
    }
}

/** Persistent admission supplied by Android; a failed send still consumes the one permitted attempt. */
class WsdOnce(private val alreadyUsed: () -> Boolean, private val persistUsed: () -> Boolean) {
    @Synchronized fun claim(): Boolean = !alreadyUsed() && persistUsed()
}
