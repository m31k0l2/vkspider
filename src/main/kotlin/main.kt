import com.vk.api.sdk.client.VkApiClient
import com.vk.api.sdk.client.actors.UserActor
import com.vk.api.sdk.httpclient.HttpTransportClient
import khttp.get
import khttp.post
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.regex.Pattern
import kotlin.system.exitProcess
import java.net.URLDecoder
import java.util.LinkedHashMap
import java.io.UnsupportedEncodingException
import java.net.URL


@Suppress("PrivatePropertyName")
class VKSpider {
    val APP_ID = 6701750
    private val CLIENT_SECRET = "MHSYr1vEpIGdHnbym1jx"
    val REDIRECT_URI = "http://example.com/callback"
    private val vk = init()
//    private val code = appAuthorization()
//    val actor = userAuthorization(code)

    private fun init(): VkApiClient {
        val transportClient = HttpTransportClient.getInstance()
        return VkApiClient(transportClient)
    }

    private fun appAuthorization(): String {
        TODO()
    }

    private fun userAuthorization(code: String): UserActor {
        val authResponse = vk.oauth()
                .userAuthorizationCodeFlow(APP_ID, CLIENT_SECRET, REDIRECT_URI, code)
                .execute()
        return UserActor(authResponse.userId, authResponse.accessToken)
    }

    fun parse(html: String, p: Pattern): String {
        val m = p.matcher(html)
        if (m.find()) {
            return html.substring(m.start(), m.end())
        }
        return ""
    }

    fun parseBlock(html: String, tag: String) = parse(html, Pattern.compile("<$tag[\\W]*[^>]*>.*</$tag>"))

    fun parseTag(html: String, tag: String)= parse(html, Pattern.compile("<$tag[\\W]*[^>]*>"))

    fun parseParam(html: String, param: String): String? {
        val p = Pattern.compile("$param=\"([^\"]*)\"")
        val m = p.matcher(html.replace("\n", ""))
        if (m.find()) {
            return m.group(1) ?: ""
        }
        return ""
    }

    fun parseInputs(form: String): List<String> {
        var f = form
        val inputs = mutableListOf<String>()
        do {
            val input = parseTag(f, "input")
            inputs.add(input)
            f = f.replace(input, "")
        } while (!input.isBlank())
        return inputs
    }

    fun extractPostData(inputs: List<String>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        inputs.forEach {
            val name = parseParam(it, "name")!!
            val value = parseParam(it, "value")!!
            map[name] = value
        }
        return map
    }

    fun getLoginData(html: String, email: String, pass: String): Map<String, String> {
//        val tag = parseTag(html, "form")
//        val action = parseParam(tag, "action")!!
        val form = parseBlock(html.replace("\n", ""), "form")
        val inputs = parseInputs(form)
        val data = mutableMapOf<String, String>()
        val d = extractPostData(inputs.filter { it.contains("hidden") })
        data["act"] = "login"
        data["soft"] = "1"
        data["ip_h"] = d["ip_h"]!!
        data["lg_h"] = d["lg_h"]!!
        data["_origin"] = d["_origin"]!!
        data["to"] = d["to"]!!
        data["expire"] = "0"
        data["email"] = email
        data["pass"] = pass
        return data
    }
}

fun readFromFile(fname: String): String {
    var html = ""
    FileReader(File(fname)).use {
        val lines = it.readLines()
        html = lines.reduce { acc, s -> acc + "$s\n" }
    }
    return html
}

@Throws(UnsupportedEncodingException::class)
fun splitQuery(url: URL): Map<String, String> {
    val query_pairs = LinkedHashMap<String, String>()
    val query = url.getQuery()
    val pairs = query.split("&".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    for (pair in pairs) {
        val idx = pair.indexOf("=")
        query_pairs[URLDecoder.decode(pair.substring(0, idx), "UTF-8")] = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
    }
    return query_pairs
}

fun main(args: Array<String>) {
    val spider = VKSpider()
//    val html = VKSpider().sendGet("https://oauth.vk.com/authorize?client_id=${spider.APP_ID}&display=page&scope=friends&response_type=code&v=5.85")
////    val html = readFromFile("login.html")
//    val data = spider.getLoginData(html, "89266552375", "m31k0l2")
//    data.second.forEach { a, b -> println("$a -> $b") }
//    spider.sendPost("https://login.vk.com", data.second)
    val url = "https://oauth.vk.com/authorize?client_id=${spider.APP_ID}&display=page&scope=friends&response_type=code&v=5.85"
    var response = get(url)
    val data = spider.getLoginData(response.text, "89266552375", "m31k0l2")
    response = post("https://login.vk.com", cookies = response.cookies.toMap(), data = data, allowRedirects = false)
    val remixq = response.cookies.filter { it.key.contains("remixq_") }.keys.first()
    val q_hash = remixq.substring("remixq_".length)
    response = get("https://vk.com/login.php?act=slogin&to=&s=1&__q_hash=$q_hash", cookies = response.cookies.toMap())
    val sid = response.cookies.get("remixsid")!!
    val cookies = mapOf("remixsid" to sid)
//    response = get("https://vk.com/id508731237", cookies = cookies)
//    val cookies2 = response.cookies.toMap()
    response = get(url, cookies=cookies)
    val html = response.text
    val tag = spider.parseTag(html, "form")
    val action = spider.parseParam(tag, "action")!!
    val params = splitQuery(URL(action))
    println(action)
    response = get(action, headers=mapOf(
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Encoding" to "gzip, deflate, br",
            "Accept-Language" to "ru,en-US;q=0.7,en;q=0.3",
            "Cache-Control" to "max-age=0",
            "Connection" to "keep-alive",
            "Host" to "oauth.vk.com",
            "User-Agent" to "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:61.0) Gecko/20100101 Firefox/61.0"
            ))
//    https://login.vk.com/?act=grant_access&client_id=6701750&settings=2&redirect_uri=&response_type=code&group_ids=&token_type=0&v=5.85&state=&display=page&ip_h=fa6c17450948392836&hash=1538339457_d487e734d965242413&https=1
    println(response.text)

//    val f = File("test.html")
//    val fw = FileWriter(f)
//    fw.write(response.text)
//    fw.close()
    //https://vk.com/login.php?act=slogin&to=&s=1&__q_hash=9d736f5556ea7378f6963eba95d08b3e
//    val r = post("https://login.vk.com", data = data)
//    println(r.statusCode)
}
//https://oauth.vk.com/authorize?client_id=6701750&display=page&scope=friends&response_type=code&v=5.85